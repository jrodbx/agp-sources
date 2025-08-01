/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.shrinker;

import com.android.aapt.Resources.ResourceTable;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.shrinker.obfuscation.ObfuscatedClasses;
import com.android.ide.common.resources.usage.ResourceStore;
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource;
import com.android.resources.ResourceType;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents resource shrinker state that is shared between shrinker stages ResourcesGatherer,
 * ObfuscationMappingsRecorder, ResourceUsageRecorder, ResourcesGraphBuilder and each stage
 * contributes an information via changing its state.
 */
public class ResourceShrinkerModel {

    private final ShrinkerDebugReporter debugReporter;

    private final ResourceStore resourceStore;
    private ObfuscatedClasses obfuscatedClasses = ObfuscatedClasses.NO_OBFUSCATION;

    private final Set<String> strings = Sets.newHashSetWithExpectedSize(300);

    private final Map<String, ResourceTable> resourceTableCache = Maps.newHashMap();

    private boolean foundGetIdentifier = false;
    private boolean foundWebContent = false;

    public ResourceShrinkerModel(
            ShrinkerDebugReporter debugReporter, boolean supportMultipackages) {
        this.debugReporter = debugReporter;
        resourceStore = new ResourceStore(supportMultipackages);
    }

    @NonNull
    public ShrinkerDebugReporter getDebugReporter() {
        return debugReporter;
    }

    @NonNull
    public ResourceStore getResourceStore() {
        return resourceStore;
    }

    @NonNull
    public ObfuscatedClasses getObfuscatedClasses() {
        return obfuscatedClasses;
    }

    /** Reports recorded mappings from obfuscated classes to original classes */
    public void setObfuscatedClasses(@NonNull ObfuscatedClasses obfuscatedClasses) {
        this.obfuscatedClasses = obfuscatedClasses;
    }

    /** Adds a new gathered resource to model. */
    @NonNull
    public Resource addResource(
            @NonNull ResourceType type,
            @Nullable String packageName,
            @NonNull String name,
            @Nullable String value) {
        int intValue = -1;
        try {
            intValue = value != null ? Integer.decode(value) : -1;
        } catch (NumberFormatException e) {
            // ignore
        }
        return addResource(type, packageName, name, intValue);
    }

    /** Adds a new gathered resource to model. */
    @NonNull
    public Resource addResource(
            @NonNull ResourceType type,
            @Nullable String packageName,
            @NonNull String name,
            int value) {
        return resourceStore.addResource(new Resource(packageName, type, name, value));
    }

    /** Adds string constant found in code to strings pool. */
    public void addStringConstant(@NonNull String string) {
        strings.add(string);
    }

    /** Returns is reference to {@code Resources#getIdentifier} is found in code. */
    public boolean isFoundGetIdentifier() {
        return foundGetIdentifier;
    }

    /** Reports that reference to {@code Resources#getIdentifier} is found in code. */
    public void setFoundGetIdentifier(boolean foundGetIdentifier) {
        this.foundGetIdentifier = foundGetIdentifier;
    }

    /** Returns is web content is found in code. */
    public boolean isFoundWebContent() {
        return foundWebContent;
    }

    /** Reports that reference to web content is found in code. */
    public void setFoundWebContent(boolean foundWebContent) {
        this.foundWebContent = foundWebContent;
    }

    /** Returns all string constants gathered from compiled classes. */
    public Set<String> getStrings() {
        return strings;
    }

    /**
     * Mark resources that match string constants as reachable in case invocation of
     * {@code Resources#getIdentifier} or web content is found in code and safe mode in enabled.
     */
    public void keepPossiblyReferencedResources() {
        if (strings.isEmpty()
                || !resourceStore.getSafeMode()
                || (!foundGetIdentifier && !foundWebContent)) {
            // No calls to android.content.res.Resources#getIdentifier; or user specifically asked
            // for us not to guess resources to keep
            return;
        }

        debugReporter.debug(() -> ""
                        + "android.content.res.Resources#getIdentifier present: " + foundGetIdentifier + "\n"
                        + "Web content present: " + foundWebContent + "\n"
                        + "Referenced Strings:\n"
                        + strings.stream()
                            .map(s -> s.trim().replace("\n", "\\n"))
                            .filter(s -> !s.isEmpty())
                            .map(s -> s.length() > 40 ? s.substring(0, 37) + "..." : s)
                            .collect(Collectors.joining("\n"))
        );

        new PossibleResourcesMarker(debugReporter, resourceStore, strings, foundWebContent)
                .markPossibleResourcesReachable();
    }

    /**
     * Reads resource table from specified path and stores it in cache to be able to reuse it if
     * the same resource table is requested by another unit.
     */
    public ResourceTable readResourceTable(Path resourceTablePath) {
        return resourceTableCache.computeIfAbsent(resourceTablePath.toString(), (path) -> {
            try {
                return ResourceTable.parseFrom(Files.readAllBytes(resourceTablePath));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
