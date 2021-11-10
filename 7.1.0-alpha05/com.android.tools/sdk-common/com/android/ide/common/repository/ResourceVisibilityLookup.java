/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.ide.common.repository;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Class which provides information about whether Android resources for a given library are public
 * or private.
 */
public abstract class ResourceVisibilityLookup {

    /**
     * Returns true if the given resource is private. Note that {@link #isPublic} is normally the
     * opposite of {@link #isPrivate}, except for unknown resources - they will both return false in
     * that case.
     *
     * @param type the type of the resource
     * @param name the resource field name of the resource (in other words, for style
     *             Theme:Variant.Cls the name would be Theme_Variant_Cls
     * @return true if the given resource is private
     */
    public abstract boolean isPrivate(@NonNull ResourceType type, @NonNull String name);

    /**
     * Returns true if the given resource is public. Note that {@link #isPublic} is normally the
     * opposite of {@link #isPrivate}, except for unknown resources - they will both return false in
     * that case.
     *
     * @param type the type of the resource
     * @param name the resource field name of the resource (in other words, for style
     *             Theme:Variant.Cls the name would be Theme_Variant_Cls
     * @return true if the given resource is public
     */
    public abstract boolean isPublic(@NonNull ResourceType type, @NonNull String name);

    protected abstract boolean isKnown(@NonNull ResourceType type, @NonNull String name);

    /**
     * Returns true if the given resource is private
     *
     * @param url the resource URL
     * @return true if the given resource is private
     */
    public boolean isPrivate(@NonNull ResourceUrl url) {
        assert !url.isFramework(); // Framework resources are not part of the library
        return isPrivate(url.type, url.name);
    }

    /**
     * For a private resource, return the library that the resource was defined as private in
     *
     * @param type the type of the resource
     * @param name the name of the resource
     * @return the library which defines the resource as private
     */
    @Nullable
    public abstract String getPrivateIn(@NonNull ResourceType type, @NonNull String name);

    /** Returns true if this repository does not declare any resources to be private */
    public abstract boolean isEmpty();

    /**
     * Creates a {@link ResourceVisibilityLookup} for a given library identified by a unique
     * identifier as well as public and all resource files (and the public resource file may not
     * exist.)
     *
     * ResourceVisibilityLookup} instances, e.g. when you have library1 and library2 each
     * referencing libraryBase.
     *
     * @param publicResources the file listing the public resources
     * @param allResources    the file listing all resources
     * @param mapKey          a unique identifier for this library
     * @return a corresponding {@link ResourceVisibilityLookup}
     */
    @NonNull
    public static ResourceVisibilityLookup create(
            @NonNull File publicResources, @NonNull File allResources, @NonNull String mapKey) {
        return new LibraryResourceVisibility(publicResources, allResources, mapKey);
    }

    /**
     * Creates a {@link ResourceVisibilityLookup} for the set of libraries.
     *
     * <p>
     *
     * @param libraries the list of libraries
     * @return a corresponding {@link ResourceVisibilityLookup}
     */
    @NonNull
    public static ResourceVisibilityLookup create(
            @NonNull List<ResourceVisibilityLookup> libraries) {
        if (libraries.size() == 1) {
            return libraries.get(0);
        }
        else {
            return new MultipleLibraryResourceVisibility(libraries);
        }
    }

    @VisibleForTesting
    @NonNull
    static AndroidLibraryResourceVisibility create(
            @NonNull String libraryArtifactAddress,
            @NonNull File librarySymbolFile,
            @NonNull File libraryPublicResources) {
        Multimap<String, ResourceType> publicResources
                = LibraryResourceVisibility.computeVisibilityMap(libraryPublicResources);
        return new AndroidLibraryResourceVisibility(
                libraryArtifactAddress,
                publicResources,
                publicResources != null ? getSymbols(librarySymbolFile) : null);
    }

    public static final ResourceVisibilityLookup NONE = new ResourceVisibilityLookup() {
        @Override
        public boolean isPrivate(@NonNull ResourceType type, @NonNull String name) {
            return false;
        }

        @Override
        public boolean isKnown(@NonNull ResourceType type, @NonNull String name) {
            return false;
        }

        @Override
        public boolean isPublic(@NonNull ResourceType type, @NonNull String name) {
            return false;
        }

        @Nullable
        @Override
        public String getPrivateIn(@NonNull ResourceType type, @NonNull String name) {
            return null;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }
    };

    /** Searches multiple libraries */
    public static class MultipleLibraryResourceVisibility extends ResourceVisibilityLookup {

        private final List<ResourceVisibilityLookup> mRepositories;

        @VisibleForTesting
        public MultipleLibraryResourceVisibility(List<ResourceVisibilityLookup> repositories) {
            mRepositories = repositories;
        }

        // It's anticipated that these methods will be called a lot (e.g. in inner loops
        // iterating over all resources matching code completion etc) so since we know
        // that our list has random access, avoid creating iterators here
        @SuppressWarnings("ForLoopReplaceableByForEach")
        @Override
        public boolean isPrivate(@NonNull ResourceType type, @NonNull String name) {
            for (int i = 0, n = mRepositories.size(); i < n; i++) {
                ResourceVisibilityLookup lookup = mRepositories.get(i);
                if (lookup.isPublic(type, name)) {
                    return false;
                }
            }
            return isKnown(type, name);
        }

        @SuppressWarnings("ForLoopReplaceableByForEach")
        @Override
        public boolean isKnown(@NonNull ResourceType type, @NonNull String name) {
            for (int i = 0, n = mRepositories.size(); i < n; i++) {
                ResourceVisibilityLookup lookup = mRepositories.get(i);
                if (lookup.isKnown(type, name)) {
                    return true;
                }
            }
            return false;
        }

        @SuppressWarnings("ForLoopReplaceableByForEach")
        @Override
        public boolean isPublic(@NonNull ResourceType type, @NonNull String name) {
            for (int i = 0, n = mRepositories.size(); i < n; i++) {
                ResourceVisibilityLookup lookup = mRepositories.get(i);
                if (lookup.isPublic(type, name) && lookup.isKnown(type, name)) {
                    return true;
                }
            }
            return false;
        }

        @SuppressWarnings("ForLoopReplaceableByForEach")
        @Override
        public boolean isEmpty() {
            for (int i = 0, n = mRepositories.size(); i < n; i++) {
                if (!mRepositories.get(i).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @SuppressWarnings("ForLoopReplaceableByForEach")
        @Nullable
        @Override
        public String getPrivateIn(@NonNull ResourceType type, @NonNull String name) {
            for (int i = 0, n = mRepositories.size(); i < n; i++) {
                ResourceVisibilityLookup r = mRepositories.get(i);
                if (r.isPrivate(type, name)) {
                    return r.getPrivateIn(type, name);
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return mRepositories.toString();
        }
    }

    /** Visibility data for a single library */
    private static class LibraryResourceVisibility extends ResourceVisibilityLookup {

        protected final String mMapKey;

        /**
         * A map from name to resource types for all resources known to this library. This is used
         * to make sure that when the {@link #isPrivate(ResourceType, String)} query method is
         * called, it can tell the difference between a resource implicitly private by not being
         * declared as public and a resource unknown to this library (e.g. defined by a different
         * library or the user's own project resources.)
         */
        private final Multimap<String, ResourceType> mAll;

        /**
         * A map of explicitly exposed resources
         */
        private final Multimap<String, ResourceType> mPublic;

        protected LibraryResourceVisibility(
                @Nullable Multimap<String, ResourceType> publicResources,
                @Nullable Multimap<String, ResourceType> allResources,
                @NonNull String mapKey) {
            mPublic = publicResources;
            mAll = allResources;
            mMapKey = mapKey;
        }

        protected LibraryResourceVisibility(
                @NonNull File publicResources, @NonNull File allResources, @NonNull String mapKey) {
            mPublic = computeVisibilityMap(publicResources);
            Multimap<String, ResourceType> all = null;
            //noinspection VariableNotUsedInsideIf
            if (mPublic != null) {
                try {
                    all = readSymbolFile(allResources);
                }
                catch (IOException ignore) {
                }
            }
            mAll = all;
            mMapKey = mapKey;
        }

        @Override
        public String toString() {
            return /*mLibrary != null ? getMapKey(mLibrary) :*/ mMapKey;
        }

        @Override
        public boolean isEmpty() {
            return mPublic == null;
        }

        @Nullable
        @Override
        public String getPrivateIn(@NonNull ResourceType type, @NonNull String name) {
            if (isPrivate(type, name)) {
                return getLibraryName();
            }

            return null;
        }

        @Nullable
        protected String getLibraryName() {
            return mMapKey;
        }

        /**
         * Returns a map from name to applicable resource types where the presence of the type+name
         * combination means that the corresponding resource is explicitly public.
         *
         * <p>If the result is null, there is no {@code public.txt} definition for this library, so
         * all resources should be taken to be public.
         *
         * @return a map from name to resource type for public resources in this library
         */
        @Nullable
        static Multimap<String, ResourceType> computeVisibilityMap(
                @NonNull File publicResources) {
            if (!publicResources.exists()) {
                return null;
            }

            try {
                List<String> lines = Files.readLines(publicResources, Charsets.UTF_8);
                Multimap<String, ResourceType> result = ArrayListMultimap.create(lines.size(), 2);
                for (String line : lines) {
                    // These files are written by code in MergedResourceWriter#postWriteAction
                    // Format for each line: <type><space><name>\n
                    // Therefore, we don't expect/allow variations in the format (we don't
                    // worry about extra spaces needing to be trimmed etc)
                    int index = line.indexOf(' ');
                    if (index == -1 || line.isEmpty()) {
                        continue;
                    }

                    String typeString = line.substring(0, index);
                    ResourceType type = ResourceType.fromClassName(typeString);
                    if (type == null) {
                        // This could in theory happen if in the future a new ResourceType is
                        // introduced, and a newer version of the Gradle build system writes the
                        // name of this type into the public.txt file, and an older version of
                        // the IDE then attempts to read it. Just skip these symbols.
                        continue;
                    }
                    String name = line.substring(index + 1);
                    // Unfortunately the public.txt extraction code has not been flattening
                    // identifiers into the same namespace as aapt does in the R.txt file,
                    // so we'll need to correct for that here.
                    name = name.replace('.', '_');
                    result.put(name, type);
                }
                return result;
            }
            catch (IOException ignore) {
            }
            return null;
        }

        @Override
        public boolean isPrivate(@NonNull ResourceType type, @NonNull String name) {
            //noinspection SimplifiableIfStatement
            if (mPublic == null) {
                // No public definitions: Everything assumed to be public
                return false;
            }

            //noinspection SimplifiableIfStatement
            if (mAll != null && !mAll.containsEntry(name, type)) {
                // Don't respond to resource URLs that are not part of this project
                // since we won't have private information on them
                return false;
            }
            return !mPublic.containsEntry(name, type);
        }

        @Override
        public boolean isKnown(@NonNull ResourceType type, @NonNull String name) {
            return mAll != null && mAll.containsEntry(name, type);
        }

        @Override
        public boolean isPublic(@NonNull ResourceType type, @NonNull String name) {
            if (mAll == null) {
                return true;
            }
            return isKnown(type, name) && (mPublic == null || mPublic.containsEntry(name, type));
        }
    }

    /** Visibility data for a single library */
    public static class AndroidLibraryResourceVisibility extends LibraryResourceVisibility {

        @NonNull private final String mLibraryArtifactAddress;

        private AndroidLibraryResourceVisibility(
                @NonNull String libraryArtifactAddress,
                @Nullable Multimap<String, ResourceType> publicResources,
                Multimap<String, ResourceType> allResources) {
            //noinspection VariableNotUsedInsideIf
            super(publicResources,
                  allResources,
                  libraryArtifactAddress);
            mLibraryArtifactAddress = libraryArtifactAddress;
        }

        @Override
        public String toString() {
            return mMapKey;
        }

        @Nullable
        @Override
        protected String getLibraryName() {
            return mLibraryArtifactAddress;
        }
    }

    @NonNull
    private static Multimap<String, ResourceType> getSymbols(
            @NonNull File symbolFile) {

        // getSymbolFile is marked @NonNull but b/157590682 shows that it can
        // be null in some scenarios, possibly from loader older cached models
        if (!symbolFile.exists()) {
            return ImmutableListMultimap.of();
        }

        try {

            return readSymbolFile(symbolFile);
        }
        catch (IOException ignore) {
            return ImmutableListMultimap.of();
        }
    }

    @NonNull
    private static Multimap<String, ResourceType> readSymbolFile(File symbolFile)
            throws IOException {
        List<String> lines =
                Files.readLines(symbolFile, Charsets.UTF_8); // TODO: Switch to iterator
        Multimap<String, ResourceType> result = ArrayListMultimap.create(lines.size(), 2);

        ResourceType previousType = null;
        String previousTypeString = "";
        int lineIndex = 1;
        final int count = lines.size();
        for (; lineIndex <= count; lineIndex++) {
            String line = lines.get(lineIndex - 1);

            if (line.startsWith("int ")) { // not int[] definitions for styleables
                // format is "int <type> <class> <name> <value>"
                int typeStart = 4;
                int typeEnd = line.indexOf(' ', typeStart);

                // Items are sorted by type, so we can avoid looping over types in
                // ResourceType.getEnum() for each line by sharing type in each section
                String typeString = line.substring(typeStart, typeEnd);
                ResourceType type;
                if (typeString.equals(previousTypeString)) {
                    type = previousType;
                }
                else {
                    type = ResourceType.fromClassName(typeString);
                    previousTypeString = typeString;
                    previousType = type;
                }
                if (type == null) { // some newly introduced type
                    continue;
                }

                int nameStart = typeEnd + 1;
                int nameEnd = line.indexOf(' ', nameStart);
                String name = line.substring(nameStart, nameEnd);
                result.put(name, type);
            }
        }
        return result;
    }
}
