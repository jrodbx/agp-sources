/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.common.resources.usage;

import static com.android.SdkConstants.AAPT_URI;
import static com.android.SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_DISCARD;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_KEEP;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_SHRINK_MODE;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.CONSTRAINT_REFERENCED_IDS;
import static com.android.SdkConstants.PREFIX_ANDROID;
import static com.android.SdkConstants.PREFIX_BINDING_EXPR;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.PREFIX_THEME_REF;
import static com.android.SdkConstants.PREFIX_TWOWAY_BINDING_EXPR;
import static com.android.SdkConstants.REFERENCE_STYLE;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.TAG_ACTION;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_LAYOUT;
import static com.android.SdkConstants.TAG_NAVIGATION;
import static com.android.SdkConstants.TAG_STYLE;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VALUE_SAFE;
import static com.android.SdkConstants.VALUE_STRICT;
import static com.android.SdkConstants.VIEW_FRAGMENT;
import static com.android.ide.common.resources.ResourcesUtil.resourceNameToFieldName;
import static com.android.utils.SdkUtils.endsWithIgnoreCase;
import static com.android.utils.SdkUtils.fileNameToResourceName;
import static com.android.utils.SdkUtils.globToRegexp;
import static com.google.common.base.Charsets.UTF_8;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.utils.SdkUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A model for Android resource declarations and usages
 */
public class ResourceUsageModel {
    private static final int TYPICAL_RESOURCE_COUNT = 200;

    /** List of all known resources (parsed from R.java) */
    private final List<Resource> mResources = Lists.newArrayListWithExpectedSize(TYPICAL_RESOURCE_COUNT);
    /** Map from resource type to map from resource name to resource object */
    private final Map<ResourceType, Map<String, Resource>> mTypeToName =
            Maps.newEnumMap(ResourceType.class);
    /** Map from R field value to corresponding resource */
    private final Map<Integer, Resource> mValueToResource =
            Maps.newHashMapWithExpectedSize(TYPICAL_RESOURCE_COUNT);
    /** Set of resource names that are explicitly whitelisted as used */
    private Set<String> mWhitelistedResources = Sets.newHashSet();
    /**
     * Next id suffix to be appended for the {@code <aapt:attr>} inlined resources created by
     * aapt
     */
    private int nextInlinedResourceSuffix;

    public enum ResourceActions {
        REMOVE("remove"),
        NO_OBFUSCATE("no_obfuscate");

        private String repr;

        ResourceActions(String repr) {
            this.repr = repr;
        }

        @Override
        public String toString() {
            return repr;
        }
    }


    public static String getResourceFieldName(Element element) {
        return resourceNameToFieldName(element.getAttribute(ATTR_NAME));
    }

    @Nullable
    public Resource getResource(Element element) {
        return getResource(element, false);
    }

    public Resource getResource(Element element, boolean declare) {
        ResourceType type = ResourceType.fromXmlTag(element);
        if (type != null) {
            String name = getResourceFieldName(element);
            Resource resource = getResource(type, name);
            if (resource == null && declare) {
                resource = addResource(type, name, null);
                resource.setDeclared(true);
            }
            return resource;
        }

        return null;
    }

    @SuppressWarnings("unused") // Used by (temporary) copy in Gradle resource shrinker
    @Nullable
    public Resource getResource(@NonNull Integer value) {
        return mValueToResource.get(value);
    }

    @Nullable
    public Resource getResource(@NonNull ResourceType type, @NonNull String name) {
        Map<String, Resource> nameMap = mTypeToName.get(type);
        if (nameMap != null) {
            return nameMap.get(resourceNameToFieldName(name));
        }
        return null;
    }

    @Nullable
    public Resource getResourceFromUrl(@NonNull String possibleUrlReference) {
        ResourceUrl url = ResourceUrl.parse(possibleUrlReference);
        if (url != null && !url.isFramework()) {
            return addResource(url.type, resourceNameToFieldName(url.name), null);
        }

        return null;
    }

    private static final String ANDROID_RES = "android_res/";

    @Nullable
    public Resource getResourceFromFilePath(@NonNull String url) {
        int nameSlash = url.lastIndexOf('/');
        if (nameSlash == -1) {
            return null;
        }

        // Look for
        //   (1) a full resource URL: /android_res/type/name.ext
        //   (2) a partial URL that uniquely identifies a given resource: drawable/name.ext
        // e.g. file:///android_res/drawable/bar.png
        int androidRes = url.indexOf(ANDROID_RES);
        if (androidRes != -1) {
            androidRes += ANDROID_RES.length();
            int slash = url.indexOf('/', androidRes);
            if (slash != -1) {
                String folderName = url.substring(androidRes, slash);
                ResourceFolderType folderType = ResourceFolderType.getFolderType(folderName);
                if (folderType != null) {
                    List<ResourceType> types = FolderTypeRelationship.getRelatedResourceTypes(
                            folderType);
                    if (!types.isEmpty()) {
                        ResourceType type = types.get(0);
                        int nameBegin = slash + 1;
                        int dot = url.indexOf('.', nameBegin);
                        String name = url.substring(nameBegin, dot != -1 ? dot : url.length());
                        return getResource(type, name);
                    }
                }
            }
        }

        // Some other relative path. Just look from the end:
        int typeSlash = url.lastIndexOf('/', nameSlash - 1);
        ResourceType type = ResourceType.fromXmlValue(url.substring(typeSlash + 1, nameSlash));
        if (type != null) {
            int nameBegin = nameSlash + 1;
            int dot = url.indexOf('.', nameBegin);
            String name = url.substring(nameBegin, dot != -1 ? dot : url.length());
            return getResource(type, name);
        }

        return null;
    }

    /**
     * Marks the given resource (if non-null) as reachable, and returns true if
     * this is the first time the resource is marked reachable
     */
    public static boolean markReachable(@Nullable Resource resource) {
        if (resource != null) {
            boolean wasReachable = resource.isReachable();
            resource.setReachable(true);
            return !wasReachable;
        }

        return false;
    }

    private static void markUnreachable(@Nullable Resource resource) {
        if (resource != null) {
            resource.setReachable(false);
        }
    }


    public void recordManifestUsages(Node node) {
        short nodeType = node.getNodeType();
        if (nodeType == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            NamedNodeMap attributes = element.getAttributes();
            for (int i = 0, n = attributes.getLength(); i < n; i++) {
                Attr attr = (Attr) attributes.item(i);
                markReachable(getResourceFromUrl(attr.getValue()));
            }
        } else if (nodeType == Node.TEXT_NODE) {
            // Does this apply to any manifests??
            String text = node.getNodeValue().trim();
            markReachable(getResourceFromUrl(text));
        }

        NodeList children = node.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            recordManifestUsages(child);
        }
    }

    private static final int RESOURCE_DECLARED =    1 << 1;
    private static final int RESOURCE_PUBLIC =      1 << 2;
    private static final int RESOURCE_KEEP =        1 << 3;
    private static final int RESOURCE_DISCARD =     1 << 4;
    private static final int RESOURCE_REACHABLE =   1 << 5;

    public static class Resource implements Comparable<Resource> {

        private int mFlags;

        /** Type of resource */
        public final ResourceType type;
        /** Name of resource */
        public final String name;
        /** Integer id location */
        public int value;

        /** Resources this resource references. For example, a layout can reference another via
         * an include; a style reference in a layout references that layout style, and so on. */
        public List<Resource> references;

        public List<Path> declarations;

        /** Whether we found a declaration for this resource (otherwise we might have seen
         * a reference to this before we came across its potential declaration, so we added it
         * to the map, but we don't want to report unused resources for invalid resource
         * references */
        public boolean isDeclared() {
            return (mFlags & RESOURCE_DECLARED) != 0;
        }

        /** Whether we found a declaration for this resource (otherwise we might have seen
         * a reference to this before we came across its potential declaration, so we added it
         * to the map, but we don't want to report unused resources for invalid resource
         * references */
        public void setDeclared(boolean on) {
            mFlags = on ? (mFlags | RESOURCE_DECLARED) : (mFlags & ~RESOURCE_DECLARED);
        }

        /** This resource is marked as public */
        public boolean isPublic() {
            return (mFlags & RESOURCE_PUBLIC) != 0;
        }

        /** This resource is marked as public */
        public void setPublic(boolean on) {
            mFlags = on ? (mFlags | RESOURCE_PUBLIC) : (mFlags & ~RESOURCE_PUBLIC);
        }

        /** This resource is marked as to be ignored for usage analysis, regardless of
         * references */
        public boolean isKeep() {
            return (mFlags & RESOURCE_KEEP) != 0;
        }

        /** This resource is marked as to be ignored for usage analysis, regardless of
         * references */
        public void setKeep(boolean on) {
            mFlags = on ? (mFlags | RESOURCE_KEEP) : (mFlags & ~RESOURCE_KEEP);
        }

        /** This resource is marked as to be ignored for usage analysis, regardless of lack of
         * references */
        public boolean isDiscard() {
            return (mFlags & RESOURCE_DISCARD) != 0;
        }

        /** This resource is marked as to be ignored for usage analysis, regardless of lack of
         * references */
        public void setDiscard(boolean on) {
            mFlags = on ? (mFlags | RESOURCE_DISCARD) : (mFlags & ~RESOURCE_DISCARD);
        }

        public boolean isReachable() {
            return (mFlags & RESOURCE_REACHABLE) != 0;
        }

        public void setReachable(boolean on) {
            mFlags = on ? (mFlags | RESOURCE_REACHABLE) : (mFlags & ~RESOURCE_REACHABLE);
        }

        public Resource(ResourceType type, String name, int value) {
            this.type = type;
            this.name = name;
            this.value = value;
        }

        @Override
        public String toString() {
            return type + ":" + name + ":" + value;
        }

        @SuppressWarnings("RedundantIfStatement") // Generated by IDE
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Resource resource = (Resource) o;

            if (!Objects.equals(name, resource.name)) {
                return false;
            }
            if (type != resource.type) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = type != null ? type.hashCode() : 0;
            result = 31 * result + (name != null ? name.hashCode() : 0);
            return result;
        }

        public void addLocation(@NonNull File file) {
            addLocation(file.toPath());
        }

        public void addLocation(@NonNull Path path) {
            if (declarations == null) {
                declarations = Lists.newArrayList();
            }
            declarations.add(path);
        }

        public void addReference(@Nullable Resource resource) {
            if (resource != null) {
                if (references == null) {
                    references = Lists.newArrayList();
                } else if (references.contains(resource)) {
                    return;
                }
                references.add(resource);
            }
        }

        public String getUrl() {
            return '@' + type.getName() + '/' + name;
        }

        public String getField() {
            return "R." + type.getName() + '.' + name;
        }

        @Override
        public int compareTo(@NonNull Resource other) {
            if (type != other.type) {
                return type.compareTo(other.type);
            }

            return name.compareTo(other.name);
        }
    }

    public List<Resource> findUnused() {
        return findUnused(mResources);
    }

    public String dumpWhitelistedResources() {
        return Joiner.on(",")
                .join(mWhitelistedResources.stream().sorted().collect(Collectors.toList()));
    }

    public String dumpConfig() {
        StringBuilder sb = new StringBuilder();
        mResources.sort(
                (resource1, resource2) -> {
                    int delta = resource1.type.compareTo(resource2.type);
                    if (delta != 0) {
                        return delta;
                    }
                    return resource1.name.compareTo(resource2.name);
                });

        for (Resource resource : mResources) {
            sb.append(resource.type);
            sb.append('/');
            sb.append(resource.name);
            sb.append("#");
            ArrayList<ResourceActions> actions = new ArrayList<>(2);
            if (!resource.isReachable()) {
                actions.add(ResourceActions.REMOVE);
            }
            if (mWhitelistedResources.contains(resource.name)) {
                actions.add(ResourceActions.NO_OBFUSCATE);
            }
            sb.append(Joiner.on(",").join(actions));
            sb.append("\n");
        }
        return sb.toString();
    }

    public String dumpReferences() {
        StringBuilder sb = new StringBuilder(1000);
        sb.append("Resource Reference Graph:\n");
        for (Resource resource : mResources) {
            if (resource.references != null) {
                sb.append(resource).append(" => ").append(resource.references).append('\n');
            }
        }
        return sb.toString();
    }


    public String dumpResourceModel() {
        StringBuilder sb = new StringBuilder(1000);
        mResources.sort(
                (resource1, resource2) -> {
                    int delta = resource1.type.compareTo(resource2.type);
                    if (delta != 0) {
                        return delta;
                    }
                    return resource1.name.compareTo(resource2.name);
                });

        for (Resource resource : mResources) {
            sb.append(resource.getUrl()).append(" : reachable=").append(resource.isReachable());
            sb.append("\n");
            if (resource.references != null) {
                for (Resource referenced : resource.references) {
                    sb.append("    ");
                    sb.append(referenced.getUrl());
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }

    public List<Resource> findUnused(List<Resource> resources) {
        List<Resource> roots = findRoots(resources);

        Map<Resource,Boolean> seen = new IdentityHashMap<>(resources.size());
        for (Resource root : roots) {
            visit(root, seen);
        }

        List<Resource> unused = Lists.newArrayListWithExpectedSize(resources.size());
        for (Resource resource : resources) {
            if (!resource.isReachable()
                    // Styles not yet handled correctly: don't mark as unused
                    && resource.type != ResourceType.ATTR
                    && resource.type != ResourceType.STYLEABLE
                    // Don't flag known service keys read by library
                    && !SdkUtils.isServiceKey(resource.name)) {
                unused.add(resource);
            }
        }

        return unused;
    }

    @SuppressWarnings("MethodMayBeStatic")
    @NonNull
    protected List<Resource> findRoots(@NonNull List<Resource> resources) {
        List<Resource> roots = Lists.newArrayList();

        for (Resource resource : resources) {
            if (resource.isReachable() || resource.isKeep()) {
                roots.add(resource);
            }
        }
        return roots;
    }

    private static void visit(Resource root, Map<Resource, Boolean> seen) {
        if (seen.containsKey(root)) {
            return;
        }
        seen.put(root, Boolean.TRUE);
        root.setReachable(true);
        if (root.references != null) {
            for (Resource referenced : root.references) {
                visit(referenced, seen);
            }
        }
    }

    @NonNull
    public Resource addDeclaredResource(@NonNull ResourceType type, @NonNull String name,
            @Nullable String value, boolean declared) {
        Resource resource = addResource(type, name, value);
        if (declared) {
            resource.setDeclared(true);
        }
        return resource;
    }

    public boolean addResourceToWhitelist(@Nullable Resource resource) {
        if (resource == null || Strings.isNullOrEmpty(resource.name)) {
            return false;
        }
        return mWhitelistedResources.add(resource.name);
    }

    @NonNull
    public Resource addResource(
            @NonNull ResourceType type, @NonNull String name, @Nullable String value) {
        return addResource(type, name, value != null ? Integer.decode(value) : -1);
    }

    @NonNull
    public Resource addResource(
            @NonNull ResourceType type, @NonNull String name, @Nullable int realValue) {
        Resource resource = getResource(type, name);
        if (resource != null) {
            //noinspection VariableNotUsedInsideIf
            if (realValue != -1) {
                if (resource.value == -1) {
                    resource.value = realValue;
                } else {
                    assert realValue == resource.value;
                }
            }
            return resource;
        }

        resource = createResource(type, name, realValue);
        mResources.add(resource);
        if (realValue != -1) {
            mValueToResource.put(realValue, resource);
        }
        Map<String, Resource> nameMap =
                mTypeToName.computeIfAbsent(type, k -> Maps.newHashMapWithExpectedSize(30));
        nameMap.put(resourceNameToFieldName(name), resource);

        // TODO: Assert that we don't set the same resource multiple times to different values.
        // Could happen if you pass in stale data!

        return resource;
    }

    @NonNull
    protected Resource createResource(
            @NonNull ResourceType type, @NonNull String name, int realValue) {
        return new Resource(type, name, realValue);
    }

    /**
     * Called for a tools:keep attribute containing a resource URL where that resource name
     * is not referencing a known resource
     *
     * @param value The keep value
     */
    private void processKeepAttributes(@NonNull String value) {
        // TODO: When nothing matches one of these attributes, mark it as unused too!
        // Handle comma separated lists of URLs and globs
        if (value.indexOf(',') != -1) {
            for (String portion : Splitter.on(',').omitEmptyStrings().trimResults().split(value)) {
                processKeepAttributes(portion);
            }
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null || url.isFramework()) {
            return;
        }

        Resource resource = getResource(url.type, url.name);
        if (resource != null) {
            markReachable(resource);
            addResourceToWhitelist(resource);
        } else if (url.name.contains("*") || url.name.contains("?")) {
            // Look for globbing patterns
            String regexp = globToRegexp(resourceNameToFieldName(url.name));
            try {
                Pattern pattern = Pattern.compile(regexp);
                Map<String, Resource> nameMap = mTypeToName.get(url.type);
                if (nameMap != null) {
                    for (Resource r : nameMap.values()) {
                        if (pattern.matcher(r.name).matches()) {
                            markReachable(r);
                            addResourceToWhitelist(r);
                        }
                    }
                }
            } catch (PatternSyntaxException ignored) {
            }
        }
    }

    private void processDiscardAttributes(@NonNull String value) {
        // Handle comma separated lists of URLs and globs
        if (value.indexOf(',') != -1) {
            for (String portion : Splitter.on(',').omitEmptyStrings().trimResults().split(value)) {
                processDiscardAttributes(portion);
            }
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null || url.isFramework()) {
            return;
        }

        Resource resource = getResource(url.type, url.name);
        if (resource != null) {
            markUnreachable(resource);
        } else if (url.name.contains("*") || url.name.contains("?")) {
            // Look for globbing patterns
            String regexp = globToRegexp(resourceNameToFieldName(url.name));
            try {
                Pattern pattern = Pattern.compile(regexp);
                Map<String, Resource> nameMap = mTypeToName.get(url.type);
                if (nameMap != null) {
                    for (Resource r : nameMap.values()) {
                        if (pattern.matcher(r.name).matches()) {
                            markUnreachable(r);
                        }
                    }
                }
            } catch (PatternSyntaxException ignored) {
            }
        }
    }

    /**
     * Recorded list of keep attributes: these can contain wildcards,
     * so they can't be applied immediately; we have to apply them after
     * scanning through all resources (done by {@link #processToolsAttributes()}
     */
    private List<String> mKeepAttributes;

    /**
     * Recorded list of discard attributes: these can contain wildcards,
     * so they can't be applied immediately; we have to apply them after
     * scanning through all resources (done by {@link #processToolsAttributes()}
     */
    private List<String> mDiscardAttributes;

    private boolean mSafeMode = true;

    /**
     * Whether we should attempt to guess resources that should be kept based on looking
     * at the string pool and assuming some of the strings can be used to dynamically construct
     * the resource names. Can be turned off via {@code tools:shrinkMode="strict"}.
     */
    public boolean isSafeMode() {
        return mSafeMode;
    }

    public void processToolsAttributes() {
        if (mKeepAttributes != null) {
            for (String keep : mKeepAttributes) {
                processKeepAttributes(keep);
            }
        }
        if (mDiscardAttributes != null) {
            for (String discard : mDiscardAttributes) {
                processDiscardAttributes(discard);
            }
        }
    }

    public void recordToolsAttributes(@Nullable Attr attr) {
        if (attr == null) {
            return;
        }
        String localName = attr.getLocalName();
        String value = attr.getValue();
        if (ATTR_KEEP.equals(localName)) {
            recordKeepToolAttribute(value);
        } else if (ATTR_DISCARD.equals(localName)) {
            recordDiscardToolAttribute(value);
        } else if (ATTR_SHRINK_MODE.equals(localName)) {
            recordShrinkModeAttribute(value);
        }
    }

    public void recordKeepToolAttribute(@NonNull String value) {
        if (mKeepAttributes == null) {
            mKeepAttributes = Lists.newArrayList();
        }
        mKeepAttributes.add(value);
    }

    public void recordDiscardToolAttribute(@NonNull String value) {
        if (mDiscardAttributes == null) {
            mDiscardAttributes = Lists.newArrayList();
        }
        mDiscardAttributes.add(value);
    }

    public void recordShrinkModeAttribute(@NonNull String value) {
        if (VALUE_STRICT.equals(value)) {
            mSafeMode = false;
        } else if (VALUE_SAFE.equals(value)) {
            mSafeMode = true;
        }
    }

    public List<String> getKeepAttributes() {
        return mKeepAttributes == null
                ? Collections.emptyList()
                : ImmutableList.copyOf(mKeepAttributes);
    }

    public List<String> getDiscardAttributes() {
        return mDiscardAttributes == null
                ? Collections.emptyList()
                : ImmutableList.copyOf(mDiscardAttributes);
    }

    protected Resource declareResource(ResourceType type, String name, Node node) {
        return addDeclaredResource(type, name, null, true);
    }

    @NonNull
    protected String readText(@NonNull File file) {
        try {
            return Files.asCharSource(file, UTF_8).read();
        } catch (IOException ignore) {
            return "";
        }
    }

    public void visitBinaryResource(
            @Nullable ResourceFolderType folderType,
            @NonNull File file) {
        Resource from = null;
        if (folderType != null && folderType != ResourceFolderType.VALUES) {
            // Record resource for the whole file
            List<ResourceType> types = FolderTypeRelationship.getRelatedResourceTypes(
                    folderType);
            ResourceType type = types.get(0);
            assert type != ResourceType.ID : folderType;
            String fileName = file.getName();
            if (fileName.startsWith(".")
                    || fileName.endsWith("~")
                    || fileName.equals("Thumbs.db")) {
                return;
            }
            String name = fileNameToResourceName(fileName);
            from = declareResource(type, name, null);
        }

        if (folderType == ResourceFolderType.RAW) {
            // Is this an HTML, CSS or JavaScript document bundled with the app?
            // If so tokenize and look for resource references.
            String path = file.getPath();
            if (endsWithIgnoreCase(path, ".html") || endsWithIgnoreCase(path, ".htm")) {
                tokenizeHtml(from, readText(file));
            } else if (endsWithIgnoreCase(path, ".css")) {
                tokenizeCss(from, readText(file));
            } else if (endsWithIgnoreCase(path, ".js")) {
                tokenizeJs(from, readText(file));
            } else if (file.isFile() && !SdkUtils.isBitmapFile(file)) {
                tokenizeUnknownBinary(from, file);
            }
        }
    }

    public void visitXmlDocument(
            @NonNull File file,
            @Nullable ResourceFolderType folderType,
            @NonNull Document document) {
        if (folderType == null) {
            // Manifest file
            recordManifestUsages(document.getDocumentElement());
            return;
        }
        Resource from = null;
        if (folderType != ResourceFolderType.VALUES) {
            // Record resource for the whole file
            List<ResourceType> types = FolderTypeRelationship.getRelatedResourceTypes(
                    folderType);
            ResourceType type = types.get(0);
            assert type != ResourceType.ID : folderType;
            String name = fileNameToResourceName(file.getName());

            from = declareResource(type, name, document.getDocumentElement());
        } else if (isAnalyticsFile(file)) {
            return;
        }

        nextInlinedResourceSuffix = 1;

        // For value files, and drawables and colors etc also pull in resource
        // references inside the context.file
        recordResourceReferences(folderType, document.getDocumentElement(), from);

        if (folderType == ResourceFolderType.XML) {
            tokenizeUnknownText(readText(file));
        }
    }

    private static final String ANALYTICS_FILE = "analytics.xml";

    /**
     * Returns true if this XML file corresponds to an Analytics configuration file;
     * these contain some attributes read by the library which won't be flagged as
     * used by the application
     *
     * @param file the file in question
     * @return true if the file represents an analytics file
     */
    public static boolean isAnalyticsFile(File file) {
        return file.getPath().endsWith(ANALYTICS_FILE) && file.getName().equals(ANALYTICS_FILE);
    }

    /**
     * Whether we should ignore tools attribute resource references.
     * <p>
     * For example, for resource shrinking we want to ignore tools attributes,
     * whereas for resource refactoring on the source code we do not.
     *
     * @return whether tools attributes should be ignored
     */
    protected boolean ignoreToolsAttributes() {
        return false;
    }

    /**
     * Records resource declarations and usages within an XML resource file
     * @param folderType the type of resource file
     * @param node the root node to start the recursive search from
     * @param from a referencing context, if any.
     */
    public void recordResourceReferences(
            @NonNull ResourceFolderType folderType,
            @NonNull Node node,
            @Nullable Resource from) {
        short nodeType = node.getNodeType();
        if (nodeType == Node.ELEMENT_NODE) {
            Element element = (Element) node;

            if ("attr".equals(element.getLocalName()) &&
                    AAPT_URI.equals(element.getNamespaceURI()) &&
                    from != null) {
                // AAPT inlined resource.
                Node child = element.getFirstChild();

                String base = from.name;
                while (child != null) {
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        String name = base + '_' + Integer.toString(nextInlinedResourceSuffix++);
                        Resource inlined = addResource(from.type, name, null);
                        from.addReference(inlined);
                    }
                    child = child.getNextSibling();
                }
            }

            if (from != null) {
                NamedNodeMap attributes = element.getAttributes();
                for (int i = 0, n = attributes.getLength(); i < n; i++) {
                    Attr attr = (Attr) attributes.item(i);

                    // Ignore tools: namespace attributes, unless it's
                    // a keep attribute
                    if (TOOLS_URI.equals(attr.getNamespaceURI())) {
                        recordToolsAttributes(attr);
                        // Skip all other tools: attributes?
                        if (ignoreToolsAttributes()) {
                            continue;
                        }
                    }

                    String value = attr.getValue();
                    if (!(value.startsWith(PREFIX_RESOURCE_REF)
                            || value.startsWith(PREFIX_THEME_REF))) {

                        String name = attr.getLocalName();
                        if (CONSTRAINT_REFERENCED_IDS.equals(name)) {
                            Splitter splitter = Splitter.on(',').trimResults().omitEmptyStrings();
                            for (String id : splitter.split(value)) {
                                markReachable(addResource(ResourceType.ID, id, null));
                            }
                        }

                        continue;
                    }
                    ResourceUrl url = ResourceUrl.parse(value);
                    if (url != null && !url.isFramework()) {
                        Resource resource;
                        if (url.isCreate()) {
                            boolean isId = ATTR_ID.equals(attr.getLocalName());
                            if (isId && TAG_LAYOUT.equals(
                                   element.getOwnerDocument().getDocumentElement().getTagName())) {
                                // When using data binding (root <layout> tag) the id's will be
                                // automatically bound (the binder will look through the layout
                                // and find all the id's.)  Therefore, treat these as read for
                                // now; longer term, it would be cool if we could track uses of
                                // the binding field instead.
                                markReachable(addResource(url.type, url.name, null));
                            } else if (isId && TAG_ACTION.equals(element.getTagName()) &&
                                    TAG_NAVIGATION.equals(element.getOwnerDocument().getDocumentElement().getTagName())) {
                                // Actions on navigation items are read by the navigation framework
                                // so treat as read
                                markReachable(addResource(url.type, url.name, null));
                            } else {
                                resource = declareResource(url.type, url.name, attr);
                                if (!isId || !ANDROID_URI.equals(attr.getNamespaceURI())) {
                                    // Declaring an id is not a reference to that id
                                    from.addReference(resource);
                                } else if (VIEW_FRAGMENT.equals(element.getTagName())) {
                                    // ID's on fragments are used implicitly (they're used by
                                    // the system to preserve fragments across configuration
                                    // changes etc.
                                    markReachable(resource);
                                }
                            }
                        } else {
                            resource = addResource(url.type, url.name, null);
                            from.addReference(resource);
                        }
                    } else if (value.startsWith(PREFIX_BINDING_EXPR) ||
                            value.startsWith(PREFIX_TWOWAY_BINDING_EXPR)) {
                        // Data binding expression: there could be multiple references here
                        int length = value.length();
                        int dbExpressionStartIndex =
                                value.startsWith(PREFIX_TWOWAY_BINDING_EXPR)
                                        ? PREFIX_TWOWAY_BINDING_EXPR.length()
                                        : PREFIX_BINDING_EXPR.length();

                        // Find resource references that look like "@string/", "@drawable/", etc.
                        int resourceStartIndex = dbExpressionStartIndex;
                        while (true) {
                            resourceStartIndex = value.indexOf('@', resourceStartIndex);
                            if (resourceStartIndex == -1) {
                                break;
                            }
                            // Find end of (potential) resource URL: first non resource URL character
                            int resourceEndIndex = resourceStartIndex + 1;
                            while (resourceEndIndex < length) {
                                char c = value.charAt(resourceEndIndex);
                                if (!(Character.isJavaIdentifierPart(c) ||
                                        c == '_' ||
                                        c == '.' ||
                                        c == '/' ||
                                        c == '+')) {
                                    break;
                                }
                                resourceEndIndex++;
                            }
                            url =
                                    ResourceUrl.parse(
                                            value.substring(resourceStartIndex, resourceEndIndex));
                            if (url != null && !url.isFramework()) {
                                Resource resource;
                                if (url.isCreate()) {
                                    resource = declareResource(url.type, url.name, attr);
                                } else {
                                    resource = addResource(url.type, url.name, null);
                                }
                                from.addReference(resource);
                            }

                            resourceStartIndex = resourceEndIndex;
                        }

                        // Find resource references that look like "R.string", "R.drawable", etc.
                        resourceStartIndex = dbExpressionStartIndex;
                        while (true) {
                            resourceStartIndex = value.indexOf("R.", resourceStartIndex);
                            if (resourceStartIndex == -1) {
                                break;
                            }
                            int resourceEndIndex = resourceStartIndex + 2;
                            // No exact match for "R." found (e.g. don't match against "BR.")
                            if (Character.isJavaIdentifierPart(
                                    value.charAt(resourceStartIndex - 1))) {
                                continue;
                            }
                            while (resourceEndIndex < length
                                    && (Character.isJavaIdentifierPart(
                                                    value.charAt(resourceEndIndex))
                                            || value.charAt(resourceEndIndex) == '.')) {
                                resourceEndIndex++;
                            }
                            // Get a substring "type.name" from "R.type.name" and split it into [type, name].
                            String[] tokens =
                                    value.substring(resourceStartIndex + 2, resourceEndIndex)
                                            .split("\\.");
                            if (tokens.length == 2) {
                                ResourceType type = ResourceType.fromClassName(tokens[0]);
                                if (type != null) {
                                    from.addReference(addResource(type, tokens[1], null));
                                }
                            }
                            resourceStartIndex = resourceEndIndex;
                        }
                    }
                }

                // Android Wear. We *could* limit ourselves to only doing this in files
                // referenced from a manifest meta-data element, e.g.
                // <meta-data android:name="com.google.android.wearable.beta.app"
                //    android:resource="@xml/wearable_app_desc"/>
                // but given that that property has "beta" in the name, it seems likely
                // to change and therefore hardcoding it for that key risks breakage
                // in the future.
                if ("rawPathResId".equals(element.getTagName())) {
                    StringBuilder sb = new StringBuilder();
                    NodeList children = node.getChildNodes();
                    for (int i = 0, n = children.getLength(); i < n; i++) {
                        Node child = children.item(i);
                        if (child.getNodeType() == Node.TEXT_NODE
                                || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                            sb.append(child.getNodeValue());
                        }
                    }
                    if (sb.length() > 0) {
                        Resource resource = getResource(ResourceType.RAW, sb.toString().trim());
                        from.addReference(resource);
                    }
                }
            } else {
                // Look for keep attributes everywhere else since they don't require a source
                recordToolsAttributes(element.getAttributeNodeNS(TOOLS_URI, ATTR_KEEP));
                recordToolsAttributes(element.getAttributeNodeNS(TOOLS_URI, ATTR_DISCARD));
                recordToolsAttributes(element.getAttributeNodeNS(TOOLS_URI, ATTR_SHRINK_MODE));
            }

            if (folderType == ResourceFolderType.VALUES) {

                Resource definition = null;
                ResourceType type = ResourceType.fromXmlTag(element);
                if (type != null) {
                    String name = getResourceFieldName(element);
                    if (name.isEmpty()) {
                        // Not a real resource
                        return;
                    }
                    if (type == ResourceType.PUBLIC) {
                        String typeName = element.getAttribute(ATTR_TYPE);
                        if (!typeName.isEmpty()) {
                            type = ResourceType.fromXmlValue(typeName);
                            if (type != null) {
                                definition = declareResource(type, name, element);
                                definition.setPublic(true);
                            }
                        }
                    } else {
                        definition = declareResource(type, name, element);
                    }
                }
                if (definition != null) {
                    from = definition;
                }

                if (type == ResourceType.STRING) {
                    // Don't look for resource definitions inside a <string> element;
                    // you can find random markup there, like <font>, which should not
                    // be taken to be a real resource. Only handle text children:
                    NodeList children = node.getChildNodes();
                    for (int i = 0, n = children.getLength(); i < n; i++) {
                        Node child = children.item(i);
                        if (child.getNodeType() != Node.ELEMENT_NODE) {
                            recordResourceReferences(folderType, child, from);
                        }
                    }

                    return;
                }

                String tagName = element.getTagName();
                if (TAG_STYLE.equals(tagName)) {
                    if (element.hasAttribute(ATTR_PARENT)) {
                        String parent = element.getAttribute(ATTR_PARENT);
                        if (!parent.isEmpty() && !parent.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)
                                && !parent.startsWith(PREFIX_ANDROID)) {
                            String parentStyle = parent;
                            if (!parentStyle.startsWith(STYLE_RESOURCE_PREFIX)) {
                                // Allow parent references to start with 'style/'
                                // as well as the more strict '@style/'.
                                if (parentStyle.startsWith(REFERENCE_STYLE)) {
                                    parentStyle = PREFIX_RESOURCE_REF + parentStyle;
                                } else {
                                    parentStyle = STYLE_RESOURCE_PREFIX + parentStyle;
                                }
                            }
                            Resource ps = getResourceFromUrl(resourceNameToFieldName(parentStyle));
                            if (ps != null && definition != null) {
                                definition.addReference(ps);
                            }
                        }
                    } else {
                        // Implicit parent styles by name
                        String name = getResourceFieldName(element);
                        while (true) {
                            int index = name.lastIndexOf('_');
                            if (index != -1) {
                                name = name.substring(0, index);
                                Resource ps =
                                        getResourceFromUrl(
                                                STYLE_RESOURCE_PREFIX
                                                        + resourceNameToFieldName(name));
                                if (ps != null && definition != null) {
                                    definition.addReference(ps);
                                }
                            } else {
                                break;
                            }
                        }
                    }
                }

                if (TAG_ITEM.equals(tagName)) {
                    // In style? If so the name: attribute can be a reference
                    if (element.getParentNode() != null
                            && element.getParentNode().getNodeName().equals(TAG_STYLE)) {
                        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                        if (!name.isEmpty() && !name.startsWith("android:")) {
                            Resource resource = getResource(ResourceType.ATTR, name);
                            if (definition == null) {
                                Element style = (Element) element.getParentNode();
                                definition = getResource(style);
                                if (definition != null) {
                                    from = definition;
                                    definition.addReference(resource);
                                }
                            }
                        }
                    }
                }
            }
        } else if (nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE) {
            String text = node.getNodeValue().trim();
            Resource textResource = getResourceFromUrl(resourceNameToFieldName(text));
            if (textResource != null && from != null) {
                from.addReference(textResource);
            }
        }

        NodeList children = node.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            recordResourceReferences(folderType, child, from);
        }
    }

    public void tokenizeHtml(@Nullable Resource from, @NonNull String html) {
        // Look for
        //    (1) URLs of the form /android_res/drawable/foo.ext
        //        which we will use to keep R.drawable.foo
        // and
        //    (2) Filenames. If the web content is loaded with something like
        //        WebView.loadDataWithBaseURL("file:///android_res/drawable/", ...)
        //        this is similar to Resources#getIdentifier handling where all
        //        *potentially* aliased filenames are kept to play it safe.

        // Simple HTML tokenizer
        int length = html.length();
        final int STATE_TEXT = 1;
        final int STATE_SLASH = 2;
        final int STATE_ATTRIBUTE_NAME = 3;
        final int STATE_BEFORE_TAG = 4;
        final int STATE_IN_TAG = 5;
        final int STATE_BEFORE_ATTRIBUTE = 6;
        final int STATE_ATTRIBUTE_BEFORE_EQUALS = 7;
        final int STATE_ATTRIBUTE_AFTER_EQUALS = 8;
        final int STATE_ATTRIBUTE_VALUE_NONE = 9;
        final int STATE_ATTRIBUTE_VALUE_SINGLE = 10;
        final int STATE_ATTRIBUTE_VALUE_DOUBLE = 11;
        final int STATE_CLOSE_TAG = 12;
        final int STATE_ENDING_TAG = 13;

        int state = STATE_TEXT;
        int offset = 0;
        int valueStart = 0;
        int tagStart = 0;
        String tag = null;
        String attribute = null;
        int attributeStart = 0;
        int prev = -1;
        while (offset < length) {
            if (offset == prev) {
                // Purely here to prevent potential bugs in the state machine from looping
                // infinitely
                offset++;
                if (offset == length) {
                    break;
                }
            }
            prev = offset;


            char c = html.charAt(offset);

            // MAke sure I handle doctypes properly.
            // Make sure I handle cdata properly.
            // Oh and what about <style> tags? tokenize everything inside as CSS!
            // ANd <script> tag content as js!
            switch (state) {
                case STATE_TEXT: {
                    if (c == '<') {
                        state = STATE_SLASH;
                        offset++;
                        continue;
                    }

                    // Other text is just ignored
                    offset++;
                    break;
                }

                case STATE_SLASH: {
                    if (c == '!') {
                        if (html.startsWith("!--", offset)) {
                            // Comment
                            int end = html.indexOf("-->", offset + 3);
                            if (end == -1) {
                                offset = length;
                                break;
                            }
                            state = STATE_TEXT;
                            offset = end + 3;
                            continue;
                        } else if (html.startsWith("![CDATA[", offset)) {
                            // Skip CDATA text content; HTML text is irrelevant to this tokenizer
                            // anyway
                            int end = html.indexOf("]]>", offset + 8);
                            if (end == -1) {
                                offset = length;
                                break;
                            }
                            state = STATE_TEXT;
                            offset = end + 3;
                            continue;
                        }
                    } else if (c == '/') {
                        state = STATE_CLOSE_TAG;
                        offset++;
                        continue;
                    } else if (c == '?') {
                        // XML Prologue
                        int end = html.indexOf('>', offset + 2);
                        if (end == -1) {
                            offset = length;
                            state = STATE_TEXT;
                            break;
                        }
                        offset = end + 1;
                        state = STATE_TEXT;
                        continue;
                    }
                    state = STATE_IN_TAG;
                    tagStart = offset;
                    break;
                }

                case STATE_CLOSE_TAG: {
                    if (c == '>') {
                        state = STATE_TEXT;
                    }
                    offset++;
                    break;
                }

                case STATE_BEFORE_TAG: {
                    if (!Character.isWhitespace(c)) {
                        state = STATE_IN_TAG;
                        tagStart = offset;
                    }
                    // (For an end tag we'll include / in the tag name here)
                    offset++;
                    break;
                }
                case STATE_IN_TAG: {
                    if (Character.isWhitespace(c)) {
                        state = STATE_BEFORE_ATTRIBUTE;
                        tag = html.substring(tagStart, offset).trim();
                    } else if (c == '>') {
                        tag = html.substring(tagStart, offset).trim();
                        endHtmlTag(from, html, offset, tag);
                        state = STATE_TEXT;
                    } else if (c == '/') {
                        tag = html.substring(tagStart, offset).trim();
                        endHtmlTag(from, html, offset, tag);
                        state = STATE_ENDING_TAG;
                    }
                    offset++;
                    break;
                }

                case STATE_ENDING_TAG: {
                    if (c == '>') {
                        offset++;
                        state = STATE_TEXT;
                    }
                    break;
                }

                case STATE_BEFORE_ATTRIBUTE: {
                    if (c == '>') {
                        endHtmlTag(from, html, offset, tag);
                        state = STATE_TEXT;
                    } else //noinspection StatementWithEmptyBody
                        if (c == '/') {
                            // we expect an '>' next to close the tag
                        } else if (!Character.isWhitespace(c)) {
                            state = STATE_ATTRIBUTE_NAME;
                            attributeStart = offset;
                        }
                    offset++;
                    break;
                }
                case STATE_ATTRIBUTE_NAME: {
                    if (c == '>') {
                        endHtmlTag(from, html, offset, tag);
                        state = STATE_TEXT;
                    } else if (c == '=') {
                        attribute = html.substring(attributeStart, offset);
                        state = STATE_ATTRIBUTE_AFTER_EQUALS;
                    } else if (Character.isWhitespace(c)) {
                        attribute = html.substring(attributeStart, offset);
                        state = STATE_ATTRIBUTE_BEFORE_EQUALS;
                    }
                    offset++;
                    break;
                }
                case STATE_ATTRIBUTE_BEFORE_EQUALS: {
                    if (c == '=') {
                        state = STATE_ATTRIBUTE_AFTER_EQUALS;
                    } else if (c == '>') {
                        endHtmlTag(from, html, offset, tag);
                        state = STATE_TEXT;
                    } else if (!Character.isWhitespace(c)) {
                        // Attribute value not specified (used for some boolean attributes)
                        state = STATE_ATTRIBUTE_NAME;
                        attributeStart = offset;
                    }
                    offset++;
                    break;
                }

                case STATE_ATTRIBUTE_AFTER_EQUALS: {
                    if (c == '\'') {
                        // a='b'
                        state = STATE_ATTRIBUTE_VALUE_SINGLE;
                        valueStart = offset + 1;
                    } else if (c == '"') {
                        // a="b"
                        state = STATE_ATTRIBUTE_VALUE_DOUBLE;
                        valueStart = offset + 1;
                    } else if (!Character.isWhitespace(c)) {
                        // a=b
                        state = STATE_ATTRIBUTE_VALUE_NONE;
                        valueStart = offset + 1;
                    }
                    offset++;
                    break;
                }

                case STATE_ATTRIBUTE_VALUE_SINGLE: {
                    if (c == '\'') {
                        state = STATE_BEFORE_ATTRIBUTE;
                        recordHtmlAttributeValue(from, tag, attribute,
                                html.substring(valueStart, offset));
                    }
                    offset++;
                    break;
                }
                case STATE_ATTRIBUTE_VALUE_DOUBLE: {
                    if (c == '"') {
                        state = STATE_BEFORE_ATTRIBUTE;
                        recordHtmlAttributeValue(from, tag, attribute,
                                html.substring(valueStart, offset));
                    }
                    offset++;
                    break;
                }
                case STATE_ATTRIBUTE_VALUE_NONE: {
                    if (c == '>') {
                        recordHtmlAttributeValue(from, tag, attribute,
                                html.substring(valueStart, offset));
                        endHtmlTag(from, html, offset, tag);
                        state = STATE_TEXT;
                    } else if (Character.isWhitespace(c)) {
                        state = STATE_BEFORE_ATTRIBUTE;
                        recordHtmlAttributeValue(from, tag, attribute,
                                html.substring(valueStart, offset));
                    }
                    offset++;
                    break;
                }
                default:
                    assert false : state;
            }
        }
    }

    private void endHtmlTag(@Nullable Resource from, @NonNull String html, int offset,
            @Nullable String tag) {
        if ("script".equals(tag)) {
            int end = html.indexOf("</script>", offset + 1);
            if (end != -1) {
                // Attempt to tokenize the text as JavaScript
                String js = html.substring(offset + 1, end);
                tokenizeJs(from, js);
            }
        } else if ("style".equals(tag)) {
            int end = html.indexOf("</style>", offset + 1);
            if (end != -1) {
                // Attempt to tokenize the text as CSS
                String css = html.substring(offset + 1, end);
                tokenizeCss(from, css);
            }
        }
    }

    public void tokenizeJs(@Nullable Resource from, @NonNull String js) {
        // Simple JavaScript tokenizer: only looks for literal strings,
        // and records those as string references
        int length = js.length();
        final int STATE_INIT = 1;
        final int STATE_SLASH = 2;
        final int STATE_STRING_DOUBLE = 3;
        final int STATE_STRING_DOUBLE_QUOTED = 4;
        final int STATE_STRING_SINGLE = 5;
        final int STATE_STRING_SINGLE_QUOTED = 6;

        int state = STATE_INIT;
        int offset = 0;
        int stringStart = 0;
        int prev = -1;
        while (offset < length) {
            if (offset == prev) {
                // Purely here to prevent potential bugs in the state machine from looping
                // infinitely
                offset++;
                if (offset == length) {
                    break;
                }
            }
            prev = offset;

            char c = js.charAt(offset);
            switch (state) {
                case STATE_INIT: {
                    if (c == '/') {
                        state = STATE_SLASH;
                    } else if (c == '"') {
                        stringStart = offset + 1;
                        state = STATE_STRING_DOUBLE;
                    } else if (c == '\'') {
                        stringStart = offset + 1;
                        state = STATE_STRING_SINGLE;
                    }
                    offset++;
                    break;
                }
                case STATE_SLASH: {
                    if (c == '*') {
                        // Comment block
                        state = STATE_INIT;
                        int end = js.indexOf("*/", offset + 1);
                        if (end == -1) {
                            offset = length; // unterminated
                            break;
                        }
                        offset = end + 2;
                        continue;
                    } else if (c == '/') {
                        // Line comment
                        state = STATE_INIT;
                        int end = js.indexOf('\n', offset + 1);
                        if (end == -1) {
                            offset = length;
                            break;
                        }
                        offset = end + 1;
                        continue;
                    } else {
                        // division - just continue
                        state = STATE_INIT;
                        offset++;
                        break;
                    }
                }
                case STATE_STRING_DOUBLE: {
                    if (c == '"') {
                        recordJsString(js.substring(stringStart, offset));
                        state = STATE_INIT;
                    } else if (c == '\\') {
                        state = STATE_STRING_DOUBLE_QUOTED;
                    }
                    offset++;
                    break;
                }
                case STATE_STRING_DOUBLE_QUOTED: {
                    state = STATE_STRING_DOUBLE;
                    offset++;
                    break;
                }
                case STATE_STRING_SINGLE: {
                    if (c == '\'') {
                        recordJsString(js.substring(stringStart, offset));
                        state = STATE_INIT;
                    } else if (c == '\\') {
                        state = STATE_STRING_SINGLE_QUOTED;
                    }
                    offset++;
                    break;
                }
                case STATE_STRING_SINGLE_QUOTED: {
                    state = STATE_STRING_SINGLE;
                    offset++;
                    break;
                }
                default:
                    assert false : state;
            }
        }
    }

    public void tokenizeCss(@Nullable Resource from, @NonNull String css) {
        // Simple CSS tokenizer: Only looks for URL references, and records those
        // filenames. Skips everything else (unrelated to images).
        int length = css.length();
        final int STATE_INIT = 1;
        final int STATE_SLASH = 2;
        int state = STATE_INIT;
        int offset = 0;
        int prev = -1;
        while (offset < length) {
            if (offset == prev) {
                // Purely here to prevent potential bugs in the state machine from looping
                // infinitely
                offset++;
                if (offset == length) {
                    break;
                }
            }
            prev = offset;

            char c = css.charAt(offset);
            switch (state) {
                case STATE_INIT: {
                    if (c == '/') {
                        state = STATE_SLASH;
                    } else if (c == 'u' && css.startsWith("url(", offset) && offset > 0) {
                        char prevChar = css.charAt(offset-1);
                        if (Character.isWhitespace(prevChar) || prevChar == ':') {
                            int end = css.indexOf(')', offset);
                            offset += 4; // skip url(
                            while (offset < length && Character.isWhitespace(css.charAt(offset))) {
                                offset++;
                            }
                            if (end != -1 && end > offset + 1) {
                                while (end > offset
                                        && Character.isWhitespace(css.charAt(end - 1))) {
                                    end--;
                                }
                                if ((css.charAt(offset) == '"'
                                        && css.charAt(end - 1) == '"')
                                        || (css.charAt(offset) == '\''
                                        && css.charAt(end - 1) == '\'')) {
                                    // Strip " or '
                                    offset++;
                                    end--;
                                }
                                recordCssUrl(from, css.substring(offset, end).trim());
                            }
                            offset = end + 1;
                            continue;
                        }

                    }
                    offset++;
                    break;
                }
                case STATE_SLASH: {
                    if (c == '*') {
                        // CSS comment? Skip the whole block rather than staying within the
                        // character tokenizer.
                        int end = css.indexOf("*/", offset + 1);
                        if (end == -1) {
                            offset = length;
                            break;
                        }
                        offset = end + 2;
                        continue;
                    }
                    state = STATE_INIT;
                    offset++;
                    break;
                }
                default:
                    assert false : state;
            }
        }
    }

    private static byte[] sAndroidResBytes;

    /** Look through binary/unknown files looking for resource URLs */
    public void tokenizeUnknownBinary(@Nullable Resource from, @NonNull File file) {
        try {
            tokenizeUnknownBinary(from, Files.toByteArray(file));
        } catch (IOException e) {
            // Ignore
        }
    }

    /** Look through binary/unknown files looking for resource URLs */
    public void tokenizeUnknownBinary(@Nullable Resource from, @NonNull byte[] bytes)
            throws IOException {
        if (sAndroidResBytes == null) {
            sAndroidResBytes = ANDROID_RES.getBytes(SdkConstants.UTF_8);
        }
        int index = 0;
        while (index != -1) {
            index = indexOf(bytes, sAndroidResBytes, index);
            if (index != -1) {
                index += sAndroidResBytes.length;

                // Find the end of the URL
                int begin = index;
                int end = begin;
                for (; end < bytes.length; end++) {
                    byte c = bytes[end];
                    if (c != '/' && !Character.isJavaIdentifierPart((char) c)) {
                        // android_res/raw/my_drawable.png ⇒ @raw/my_drawable
                        String url = "@" + new String(bytes, begin, end - begin, UTF_8);
                        Resource resource = getResourceFromUrl(url);
                        if (resource != null) {
                            if (from != null) {
                                from.addReference(resource);
                            } else {
                                markReachable(resource);
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    /**
     * Returns the index of the given target array in the first array, looking from the given
     * index
     */
    private static int indexOf(byte[] array, byte[] target, int fromIndex) {
        outer:
        for (int i = fromIndex; i < array.length - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    /** Look through text files of unknown structure looking for resource URLs */
    private void tokenizeUnknownText(@NonNull String text) {
        int index = 0;
        while (index != -1) {
            index = text.indexOf(ANDROID_RES, index);
            if (index != -1) {
                index += ANDROID_RES.length();

                // Find the end of the URL
                int begin = index;
                int end = begin;
                int length = text.length();
                for (; end < length; end++) {
                    char c = text.charAt(end);
                    if (c != '/' && !Character.isJavaIdentifierPart(c)) {
                        // android_res/raw/my_drawable.png ⇒ @raw/my_drawable
                        markReachable(getResourceFromUrl("@" + text.substring(begin, end)));
                        break;
                    }
                }
            }
        }
    }

    /** Adds the resource identifiers found in the given Kotlin code into the reference map */
    public void tokenizeKotlinCode(@NonNull String s) {
        // the Java tokenizer works for Kotlin as well. It doesn't handle nested
        // block comments but that's not common in Kotlin code.
        tokenizeJavaCode(s);
    }

    /** Adds the resource identifiers found in the given Java source code into the reference map */
    public void tokenizeJavaCode(@NonNull String s) {
        if (s.length() <= 2) {
            return;
        }

        // Scan looking for R.{type}.name identifiers
        // Extremely simple state machine which just avoids comments, line comments
        // and strings, and outside of that records any R. identifiers it finds
        int index = 0;
        int length = s.length();

        char c;
        char next;
        for (; index < length; index++) {
            c = s.charAt(index);
            if (index == length - 1) {
                break;
            }
            next = s.charAt(index + 1);
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (c == '/') {
                if (next == '*') {
                    // Block comment
                    while (index < length - 2) {
                        if (s.charAt(index) == '*' && s.charAt(index + 1) == '/') {
                            break;
                        }
                        index++;
                    }
                    index++;
                } else if (next == '/') {
                    // Line comment
                    while (index < length && s.charAt(index) != '\n') {
                        index++;
                    }
                }
            } else if (c == '\'') {
                // Character
                if (next == '\\') {
                    // Skip '\c'
                    index += 2;
                } else {
                    // Skip 'c'
                    index++;
                }
            } else if (c == '\"') {
                // String: Skip to end
                index++;
                while (index < length - 1) {
                    char t = s.charAt(index);
                    if (t == '\\') {
                        index++;
                    } else if (t == '"') {
                        break;
                    }
                    index++;
                }
            } else if (c == 'R' && next == '.') {
                // This might be a pattern
                int begin = index;
                index += 2;
                while (index < length) {
                    char t = s.charAt(index);
                    if (t == '.') {
                        String typeName = s.substring(begin + 2, index);
                        ResourceType type = ResourceType.fromClassName(typeName);
                        if (type != null) {
                            index++;
                            begin = index;
                            while (index < length &&
                                    Character.isJavaIdentifierPart(s.charAt(index))) {
                                index++;
                            }
                            if (index > begin) {
                                String name = s.substring(begin, index);
                                Resource resource = addResource(type, name, null);
                                markReachable(resource);
                            }
                        }
                        index--;
                        break;
                    } else if (!Character.isJavaIdentifierStart(t)) {
                        break;
                    }
                    index++;
                }
            } else if (Character.isJavaIdentifierPart(c)) {
                // Skip to the end of the identifier
                while (index < length && Character.isJavaIdentifierPart(s.charAt(index))) {
                    index++;
                }
                // Back up so the next character can be checked to see if it's a " etc
                index--;
            } // else just punctuation/operators ( ) ;  etc
        }
    }

    protected void referencedString(@NonNull String string) {
    }

    private void recordCssUrl(@Nullable Resource from, @NonNull String value) {
        if (!referencedUrl(from, value)) {
            referencedString(value);
        }
    }

    /**
     * See if the given URL is a URL that we can resolve to a specific resource; if so,
     * record it and return true, otherwise returns false.
     */
    private boolean referencedUrl(@Nullable Resource from, @NonNull String url) {
        Resource resource = getResourceFromFilePath(url);
        if (resource == null && url.indexOf('/') == -1) {
            // URLs are often within the raw folder
            resource =
                    getResource(
                            ResourceType.RAW, resourceNameToFieldName(fileNameToResourceName(url)));
        }
        if (resource != null) {
            if (from != null) {
                from.addReference(resource);
            } else {
                // We don't have an inclusion context, so just assume this resource is reachable
                markReachable(resource);
            }
            return true;
        }

        return false;
    }

    private void recordHtmlAttributeValue(@Nullable Resource from, @Nullable String tagName,
            @Nullable String attribute, @NonNull String value) {
        if ("href".equals(attribute) || "src".equals(attribute)) {
            // In general we'd need to unescape the HTML here (e.g. remove entities) but
            // those wouldn't be valid characters in the resource name anyway
            if (!referencedUrl(from, value)) {
                referencedString(value);
            }

            // If this document includes another, record the reachability of that script/resource
            if (from != null) {
                from.addReference(getResourceFromFilePath(attribute));
            }
        }
    }

    private void recordJsString(@NonNull String string) {
        referencedString(string);
    }

    public List<Resource> getResources() {
        return mResources;
    }

    @NonNull
    public Collection<Map<String, Resource>> getResourceMaps() {
        return mTypeToName.values();
    }
}
