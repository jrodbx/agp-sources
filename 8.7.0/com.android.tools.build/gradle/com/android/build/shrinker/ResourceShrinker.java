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

import com.android.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/**
 * Interface for unit that analyzes all resources (after resource merging, compilation and code
 * shrinking has been completed) and figures out which resources are unused, and replaces them with
 * dummy content inside zip archive file.
 */
public interface ResourceShrinker extends AutoCloseable {

    /**
     * Analyzes resources and detects unreachable ones. It includes the following steps:
     *
     * <ul>
     *     <li>Gather resources available in project.
     *     <li>Record resource usages via analyzing compiled code, AndroidManifest etc.
     *     <li>Build reference graph and connect dependent resources.
     *     <li>Detects WebView and/or {@code Resources#getIdentifier} usages and guess which
     *         resources are reachable analyzing string constants available in compiled code.
     *     <li>Processes resources explicitly asked to keep and discard. &lt;tools:keep&gt; and
     *         &lt;tools:discard&gt; attribute.
     *     <li>Based on the root references computes unreachable resources.
     * </ul>
     */
    void analyze() throws IOException, ParserConfigurationException, SAXException;

    /**
     * Returns count of unused resources. Should be called after {@code ResourceShrinker#analyze}.
     */
    int getUnusedResourceCount();

    /**
     * Replaces entries in {@param source} zip archive that belong to unused resources with dummy
     * content and produces a new {@param dest} zip archive. Zip archive should contain resources
     * in 'res/' folder like it is stored in APK.
     *
     * <p>For now, doesn't change resource table and applies to file-based resources like layouts,
     * menus and drawables, not value-based resources like strings and dimensions.
     *
     * <p>Should be called after {@code ResourceShrinker#analyze}.
     */
    void rewriteResourcesInApkFormat(
            @NonNull File source,
            @NonNull File dest,
            @NonNull LinkedResourcesFormat format
    ) throws IOException;

    /**
     * Replaces entries in {@param source} zip archive that belong to unused resources with dummy
     * content and produces a new {@param dest} zip archive. Zip archive represents App Bundle which
     * may have multiple modules and each module has its own resource directory: '${module}/res/'.
     * Package name for each bundle module should be passed as {@param moduleToPackageName}.
     *
     * <p>For now, doesn't change resource table and applies to file-based resources like layouts,
     * menus and drawables, not value-based resources like strings and dimensions.
     *
     * <p>Should be called after {@code ResourceShrinker#analyze}.
     */
    void rewriteResourcesInBundleFormat(
            @NonNull File source,
            @NonNull File dest,
            @NonNull Map<String, String> moduleToPackageName
    ) throws IOException;
}
