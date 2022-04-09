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
import static com.android.ide.common.resources.ResourcesUtil.resourceNameToFieldName;
import static com.android.support.FragmentTagUtil.isFragmentTag;
import static com.android.utils.SdkUtils.endsWithIgnoreCase;
import static com.android.utils.SdkUtils.fileNameToResourceName;
import static com.google.common.base.Charsets.UTF_8;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.ResourcesUtil;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.utils.SdkUtils;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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
    /** All known resources store. */
    protected ResourceStore mResourceStore;

    public ResourceUsageModel() {
        mResourceStore = new ResourceStore();
    }

    ResourceUsageModel(ResourceStore resourceStore) {
        mResourceStore = resourceStore;
    }

    /**
     * Next id suffix to be appended for the {@code <aapt:attr>} inlined resources created by
     * aapt
     */
    private int nextInlinedResourceSuffix;

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
        return mResourceStore.getResource(value);
    }

    @Nullable
    public Resource getResource(@NonNull ResourceType type, @NonNull String name) {
        return Iterables.getFirst(mResourceStore.getResources(type, name), null);
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

        // Semi-visible for serialization in ResourceStore.Companion.serialize */
        int mFlags;

        /**
         * Describes the resource flags in a mnenonic way: "E" means empty, "D" declared, "R"
         * reachable, "K" keep, "P" public and "X" discard. Also "U" means declared and reachable
         * (since this is a very common combination). A combination of these are possible.
         */
        @NonNull
        public String flagString() {
            switch (mFlags) {
                case 0:
                    return "E";
                case RESOURCE_DECLARED:
                    return "D";
                case RESOURCE_DECLARED | RESOURCE_REACHABLE:
                    return "U";
                default:
                    {
                        StringBuilder sb = new StringBuilder();
                        if (isDeclared()) {
                            sb.append('D');
                        }
                        if (isReachable()) {
                            sb.append('R');
                        }
                        if (isPublic()) {
                            sb.append('P');
                        }
                        if (isKeep()) {
                            sb.append('K');
                        }
                        if (isDiscard()) {
                            sb.append('X');
                        }
                        return sb.toString();
                    }
            }
        }

        /** Reverses the {@link #flagString()} method */
        public static int stringToFlag(@NonNull String s) {
            int flags = 0;
            for (int i = 0; i < s.length(); i++) {
                switch (s.charAt(i)) {
                    case 'E':
                        return 0;
                    case 'U':
                        flags = flags | RESOURCE_DECLARED | RESOURCE_REACHABLE;
                        break;
                    case 'D':
                        flags = flags | RESOURCE_DECLARED;
                        break;
                    case 'R':
                        flags = flags | RESOURCE_REACHABLE;
                        break;
                    case 'P':
                        flags = flags | RESOURCE_PUBLIC;
                        break;
                    case 'K':
                        flags = flags | RESOURCE_KEEP;
                        break;
                    case 'X':
                        flags = flags | RESOURCE_DISCARD;
                        break;
                    default:
                        assert false : s;
                }
            }

            return flags;
        }

        /** Type of resource */
        public final ResourceType type;
        /** Name of resource */
        public final String name;
        /** Package of resource */
        public String packageName;
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
            this(null, type, name, value);
        }

        public Resource(String packageName, ResourceType type, String name, int value) {
            this.packageName = packageName;
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

            return Objects.equals(packageName, resource.packageName)
                    && Objects.equals(name, resource.name)
                    && type == resource.type;
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
            String packagePart = packageName == null ? "" : packageName + ":";
            return '@' + packagePart + type.getName() + '/' + name;
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
        return findUnused(mResourceStore.getResources());
    }

    public String dumpKeepResources() {
        return mResourceStore.dumpKeepResources();
    }

    public String dumpConfig() {
        return mResourceStore.dumpConfig();
    }

    public String dumpReferences() {
        return mResourceStore.dumpReferences();
    }

    public String dumpResourceModel() {
        return mResourceStore.dumpResourceModel();
    }

    /** Writes out this model into a string */
    public String serialize(boolean includeValues) {
        return ResourceStore.Companion.serialize(mResourceStore, includeValues);
    }

    /** Recreates a model from a previously created string via {@link #serialize(boolean)} */
    public static ResourceUsageModel deserialize(String s) {
        ResourceStore store = ResourceStore.Companion.deserialize(s);
        return new ResourceUsageModel(store);
    }

    /** Merges the other {@linkplain ResourceUsageModel} into this one */
    public void merge(@NonNull ResourceUsageModel other) {
        mResourceStore.merge(other.mResourceStore);
    }

    public List<Resource> findUnused(List<Resource> resources) {
        return ResourcesUtil.findUnusedResources(resources, this::onRootResourcesFound);
    }

    protected void onRootResourcesFound(@NonNull List<Resource> roots) {
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

    @NonNull
    public Resource addResource(
            @NonNull ResourceType type, @NonNull String name, @Nullable String value) {
        return addResource(type, name, value != null ? Integer.decode(value) : -1);
    }

    @NonNull
    public Resource addResource(
            @NonNull ResourceType type, @NonNull String name, int realValue) {
        return mResourceStore.addResource(
                createResource(type, resourceNameToFieldName(name), realValue));
    }

    @NonNull
    protected Resource createResource(
            @NonNull ResourceType type, @NonNull String name, int realValue) {
        return new Resource(type, name, realValue);
    }

    public boolean isSafeMode() {
        return mResourceStore.getSafeMode();
    }

    public void processToolsAttributes() {
        mResourceStore.processToolsAttributes();
    }

    public void recordToolsAttributes(@Nullable Attr attr) {
        if (attr == null) {
            return;
        }
        String localName = attr.getLocalName();
        String value = attr.getValue();
        if (ATTR_KEEP.equals(localName)) {
            mResourceStore.recordKeepToolAttribute(value);
        } else if (ATTR_DISCARD.equals(localName)) {
            mResourceStore.recordDiscardToolAttribute(value);
        } else if (ATTR_SHRINK_MODE.equals(localName)) {
            recordShrinkModeAttribute(value);
        }
    }

    public void recordShrinkModeAttribute(@NonNull String value) {
        if (VALUE_STRICT.equals(value)) {
            mResourceStore.setSafeMode(false);
        } else if (VALUE_SAFE.equals(value)) {
            mResourceStore.setSafeMode(true);
        }
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
                                } else if (isFragmentTag(element.getTagName())) {
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
                                resourceStartIndex += 2; // skip "R." in "BR." so we don't repeat
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
        new WebTokenizers(new ReferencesWebTokensCallback(from)).tokenizeHtml(html);
    }

    public void tokenizeJs(@Nullable Resource from, @NonNull String js) {
        new WebTokenizers(new ReferencesWebTokensCallback(from)).tokenizeJs(js);
    }

    public void tokenizeCss(@Nullable Resource from, @NonNull String css) {
        new WebTokenizers(new ReferencesWebTokensCallback(from)).tokenizeCss(css);
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
        // Look for
        //    (1) URLs of the form /android_res/drawable/foo.ext
        //        which we will use to keep R.drawable.foo
        // and
        //    (2) Filenames. If the web content is loaded with something like
        //        WebView.loadDataWithBaseURL("file:///android_res/drawable/", ...)
        //        this is similar to Resources#getIdentifier handling where all
        //        *potentially* aliased filenames are kept to play it safe.
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
        return mResourceStore.getResources();
    }

    @NonNull
    public Collection<ListMultimap<String, Resource>> getResourceMaps() {
        return mResourceStore.getResourceMaps();
    }

    private class ReferencesWebTokensCallback implements WebTokenizers.WebTokensCallback {
        private final Resource from;

        public ReferencesWebTokensCallback(@Nullable Resource from) {
            this.from = from;
        }

        @Override
        public void referencedHtmlAttribute(
                @Nullable String tag, @Nullable String attribute, @NonNull String value) {
            recordHtmlAttributeValue(from, tag, attribute, value);
        }

        @Override
        public void referencedJsString(@NonNull String jsString) {
            recordJsString(jsString);
        }

        @Override
        public void referencedCssUrl(@NonNull String url) {
            recordCssUrl(from, url);
        }
    }
}
