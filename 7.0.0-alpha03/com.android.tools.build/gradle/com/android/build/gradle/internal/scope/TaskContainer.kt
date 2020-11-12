/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.scope

import com.android.build.gradle.internal.tasks.CheckManifest
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask
import com.android.build.gradle.tasks.AidlCompile
import com.android.build.gradle.tasks.ExternalNativeBuildTask
import com.android.build.gradle.tasks.ExtractAnnotations
import com.android.build.gradle.tasks.GenerateBuildConfig
import com.android.build.gradle.tasks.ManifestProcessorTask
import com.android.build.gradle.tasks.MergeResources
import com.android.build.gradle.tasks.MergeSourceSetFolders
import com.android.build.gradle.tasks.PackageAndroidArtifact
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.build.gradle.tasks.RenderscriptCompile
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile

/**
 * Task container for the tasks needed by the Variant API.
 */
interface TaskContainer {

    val assembleTask: TaskProvider<out Task>
    val javacTask: TaskProvider<out JavaCompile>
    // empty anchor compile task to set all compilations tasks as dependents.
    val compileTask: TaskProvider<out Task>
    val preBuildTask: TaskProvider<out Task>
    val checkManifestTask: TaskProvider<out CheckManifest>?
    val aidlCompileTask: TaskProvider<out AidlCompile>?
    val renderscriptCompileTask: TaskProvider<out RenderscriptCompile>?
    val mergeResourcesTask: TaskProvider<out MergeResources>
    val mergeAssetsTask: TaskProvider<out MergeSourceSetFolders>
    val processJavaResourcesTask: TaskProvider<out Sync>
    val generateBuildConfigTask: TaskProvider<out GenerateBuildConfig>?
    val processAndroidResTask: TaskProvider<out ProcessAndroidResources>?
    val processManifestTask: TaskProvider<out ManifestProcessorTask>?
    val packageAndroidTask: TaskProvider<out PackageAndroidArtifact>?
    val bundleLibraryTask: TaskProvider<out Zip>?

    val installTask: TaskProvider<out DefaultTask>?
    val uninstallTask: TaskProvider<out DefaultTask>?

    val connectedTestTask: TaskProvider<out DeviceProviderInstrumentTestTask>?
    val providerTestTaskList: List<TaskProvider<out DeviceProviderInstrumentTestTask>>

    var generateAnnotationsTask: TaskProvider<out ExtractAnnotations>?

    val externalNativeBuildTask: TaskProvider<out ExternalNativeBuildTask>?
}
