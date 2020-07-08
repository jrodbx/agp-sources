/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.manifmerger.MergingReport;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import javax.annotation.Nonnull;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;

/** A task that processes the manifest */
public abstract class ManifestProcessorTask extends IncrementalTask {

    public ManifestProcessorTask(@NonNull ObjectFactory objectFactory) {
        manifestOutputDirectory = objectFactory.directoryProperty();
        instantAppManifestOutputDirectory = objectFactory.directoryProperty();
        aaptFriendlyManifestOutputDirectory = objectFactory.directoryProperty();
    }

    @SuppressWarnings("unused")
    @Nonnull
    private final DirectoryProperty manifestOutputDirectory;

    @Nonnull private final DirectoryProperty aaptFriendlyManifestOutputDirectory;

    @Nonnull private final DirectoryProperty instantAppManifestOutputDirectory;

    /**
     * The aapt friendly processed Manifest. In case we are processing a library manifest, some
     * placeholders may not have been resolved (and will be when the library is merged into the
     * importing application). However, such placeholders keys are not friendly to aapt which flags
     * some illegal characters. Such characters are replaced/encoded in this version.
     */
    @Nullable
    @Internal
    public abstract File getAaptFriendlyManifestOutputFile();

    /** The processed Manifests files folder. */
    @NonNull
    @OutputDirectory
    public DirectoryProperty getManifestOutputDirectory() {
        return manifestOutputDirectory;
    }

    /**
     * The aapt friendly processed Manifest. In case we are processing a library manifest, some
     * placeholders may not have been resolved (and will be when the library is merged into the
     * importing application). However, such placeholders keys are not friendly to aapt which flags
     * some illegal characters. Such characters are replaced/encoded in this version.
     */
    @OutputDirectory
    @Optional
    @NonNull
    public DirectoryProperty getAaptFriendlyManifestOutputDirectory() {
        return aaptFriendlyManifestOutputDirectory;
    }

    /**
     * The bundle manifest which is consumed by the bundletool (as opposed to the one packaged with
     * the apk when built directly).
     */
    @OutputDirectory
    @Optional
    public abstract DirectoryProperty getBundleManifestOutputDirectory();

    /**
     * The feature manifest which is consumed by its base feature (as opposed to the one packaged
     * with the feature APK). This manifest, unlike the one packaged with the APK, does not specify
     * a minSdkVersion. This is used by by both normal features and dynamic-features.
     */
    @OutputDirectory
    @Optional
    public abstract DirectoryProperty getMetadataFeatureManifestOutputDirectory();

    /** The instant app manifest which is used if we are deploying the app as an instant app. */
    @OutputDirectory
    @Optional
    @NonNull
    public DirectoryProperty getInstantAppManifestOutputDirectory() {
        return instantAppManifestOutputDirectory;
    }

    @OutputFile
    @Optional
    public abstract RegularFileProperty getReportFile();

    @OutputFile
    @Optional
    @NonNull
    public abstract RegularFileProperty getMergeBlameFile();

    protected static void outputMergeBlameContents(
            @NonNull MergingReport mergingReport, @Nullable File mergeBlameFile)
            throws IOException {
        if (mergeBlameFile == null) {
            return;
        }
        String output = mergingReport.getMergedDocument(MergingReport.MergedManifestKind.BLAME);
        if (output == null) {
            return;
        }

        FileUtils.mkdirs(mergeBlameFile.getParentFile());
        try (Writer writer = Files.newWriter(mergeBlameFile, Charsets.UTF_8)) {
            writer.write(output);
        }
    }

    /**
     * Serialize a map key+value pairs into a comma separated list. Map elements are sorted to
     * ensure stability between instances.
     *
     * @param mapToSerialize the map to serialize.
     */
    protected static String serializeMap(Map<String, Object> mapToSerialize) {
        final Joiner keyValueJoiner = Joiner.on(":");
        // transform the map on a list of key:value items, sort it and concatenate it.
        return Joiner.on(",").join(
                Ordering.natural().sortedCopy(Iterables.transform(
                        mapToSerialize.entrySet(),
                        (input) -> keyValueJoiner.join(input.getKey(), input.getValue()))));
    }
}
