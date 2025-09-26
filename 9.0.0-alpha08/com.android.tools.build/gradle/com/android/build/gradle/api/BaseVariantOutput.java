/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.OutputFile;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.model.Managed;

/**
 * A Build variant output and all its public data. This is the base class for items common to apps,
 * test apps, and libraries
 */
@Deprecated
@Managed
public interface BaseVariantOutput extends OutputFile {

    /**
     * Returns the Android Resources processing task.
     *
     * @deprecated Use {@link #getProcessResourcesProvider()}
     */
    @NonNull
    @Deprecated
    ProcessAndroidResources getProcessResources();

    /**
     * Returns the {@link TaskProvider} for the Android Resources processing task.
     *
     * <p>Prefer this to {@link #getProcessResources()} as it triggers eager configuration of the
     * task.
     */
    @NonNull
    TaskProvider<ProcessAndroidResources> getProcessResourcesProvider();

    /**
     * Returns the manifest merging task.
     *
     * @deprecated Use {@link #getProcessManifestProvider()}
     */
    @NonNull
    @Deprecated
    ManifestProcessorTask getProcessManifest();

    /**
     * Returns the {@link TaskProvider} for the manifest merging task
     *
     * <p>Prefer this to {@link #getProcessManifest()} as it triggers eager configuration of the
     * task.
     */
    @NonNull
    TaskProvider<ManifestProcessorTask> getProcessManifestProvider();

    /**
     * Returns the assemble task for this particular output
     *
     * @deprecated Use {@link BaseVariant#getAssembleProvider()}
     */
    @Nullable
    @Deprecated
    Task getAssemble();

    /**
     * Returns the name of the variant. Guaranteed to be unique.
     */
    @NonNull
    String getName();

    /**
     * Returns the base name for the output of the variant. Guaranteed to be unique.
     */
    @NonNull
    String getBaseName();

    /**
     * Returns a subfolder name for the variant output. Guaranteed to be unique.
     *
     * This is usually a mix of build type and flavor(s) (if applicable).
     * For instance this could be:
     * "debug"
     * "debug/myflavor"
     * "release/Flavor1Flavor2"
     */
    @NonNull
    String getDirName();

}
