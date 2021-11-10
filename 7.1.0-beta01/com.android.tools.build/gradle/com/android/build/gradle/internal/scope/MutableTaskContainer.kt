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
import com.android.build.gradle.internal.tasks.ValidateSigningTask
import com.android.build.gradle.tasks.AidlCompile
import com.android.build.gradle.tasks.ExternalNativeBuildTask
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
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
 * DO NOT ADD NEW TASKS TO THIS CLASS.
 *
 * Container for the tasks for a variant.
 *
 * This contains 2 different types of tasks.
 * - the tasks needed by the variant API. The goal here is to revamp the API to get rid of the need
 *   to expose the tasks.
 * - tasks for internal usage and wiring. This should not be needed, except in rare cases (anchors).
 *   The goal is to get rid of this as much as possible, progressively; and to use buildable
 *   artifact exclusively to wire tasks.
 *
 * DO NOT ADD NEW TASKS TO THIS CLASS.
 */
class MutableTaskContainer : TaskContainer {

    // implementation of the API setter/getters as required by our current APIs.
    override lateinit var assembleTask: TaskProvider<out Task>
    override lateinit var javacTask: TaskProvider<out JavaCompile>
    override lateinit var compileTask: TaskProvider<out Task>
    override lateinit var preBuildTask: TaskProvider<out Task>
    override var checkManifestTask: TaskProvider<out CheckManifest>? = null
    override var aidlCompileTask: TaskProvider<out AidlCompile>? = null
    override var renderscriptCompileTask: TaskProvider<out RenderscriptCompile>? = null
    override lateinit var mergeResourcesTask: TaskProvider<out MergeResources>
    override lateinit var mergeAssetsTask: TaskProvider<out MergeSourceSetFolders>
    override lateinit var processJavaResourcesTask: TaskProvider<out Sync>
    override var generateBuildConfigTask: TaskProvider<out GenerateBuildConfig>? = null
    override var processAndroidResTask: TaskProvider<out ProcessAndroidResources>? = null
    override var processManifestTask: TaskProvider<out ManifestProcessorTask>? = null
    override var packageAndroidTask: TaskProvider<out PackageAndroidArtifact>? = null
    override var bundleLibraryTask: TaskProvider<out Zip>? = null

    override var installTask: TaskProvider<out DefaultTask>? = null
    override var uninstallTask: TaskProvider<out DefaultTask>? = null

    override var connectedTestTask: TaskProvider<out DeviceProviderInstrumentTestTask>? = null
    override val providerTestTaskList: List<TaskProvider<out DeviceProviderInstrumentTestTask>> = mutableListOf()

    override var generateAnnotationsTask: TaskProvider<out ExtractAnnotations>? = null

    override var externalNativeBuildTask: TaskProvider<out ExternalNativeBuildTask>? = null

    // required by the model.
    lateinit var sourceGenTask: TaskProvider<out Task>

    // anything below is scheduled for removal, using BuildableArtifact to link tasks.

    var bundleTask: TaskProvider<out Task>? = null
    lateinit var resourceGenTask: TaskProvider<Task>
    lateinit var assetGenTask: TaskProvider<Task>
    var microApkTask: TaskProvider<out Task>? = null
    var cxxConfigurationModel: CxxConfigurationModel? = null
    var packageSplitResourcesTask: TaskProvider<out Task>? = null
    var packageSplitAbiTask: TaskProvider<out Task>? = null
    var generateResValuesTask: TaskProvider<out Task>? = null
    var generateApkDataTask: TaskProvider<out Task>? = null
    var coverageReportTask: TaskProvider<out Task>? = null

    var validateSigningTask: TaskProvider<out ValidateSigningTask>? = null
}
