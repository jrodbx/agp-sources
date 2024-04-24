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

package com.android.build.gradle.internal.res

import com.android.SdkConstants
import com.android.build.gradle.internal.AndroidJarInput
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.initialize
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.res.Aapt2FromMaven.Companion.create
import com.android.build.gradle.internal.res.namespaced.Aapt2LinkRunnable
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.services.getLeasingAapt2
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.configureVariantProperties
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.core.ComponentTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File

/**
 * Invokes AAPT2 link on the merged resources of all library dependencies into the .ap_ format.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ANDROID_RESOURCES, secondaryTaskCategories = [TaskCategory.LINKING])
abstract class PrivacySandboxSdkLinkAndroidResourcesTask : NonIncrementalTask() {

    @get:Input
    abstract val minSdk: Property<Int>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val mergedResourcesDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val manifestFile: RegularFileProperty

    @get:Nested
    abstract val aapt2: Aapt2Input

    @get:Nested
    abstract val androidJarInput: AndroidJarInput

    @get:OutputFile
    abstract val outputResFile: RegularFileProperty

    @get:OutputFile
    abstract val runtimeSymbolList: RegularFileProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation()
                .submit(PrivacySandboxSdkLinkAndroidResourcesWorkerAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.aapt2.setDisallowChanges(aapt2)
            it.androidJar.set(androidJarInput.getAndroidJar().get())
            it.manifestFile.setDisallowChanges(manifestFile)
            it.resourceDirs.setFrom(mergedResourcesDirectory)
            it.minSdk.setDisallowChanges(minSdk)
            it.outputResFile.setDisallowChanges(outputResFile)
            it.runtimeSymbolList.setDisallowChanges(runtimeSymbolList)
        }
    }


    abstract class PrivacySandboxSdkLinkAndroidResourcesParams
        : ProfileAwareWorkAction.Parameters() {
        abstract val aapt2: Property<Aapt2Input>
        abstract val androidJar: RegularFileProperty
        abstract val manifestFile: RegularFileProperty
        abstract val resourceDirs: ConfigurableFileCollection
        abstract val minSdk: Property<Int>
        abstract val outputResFile: RegularFileProperty
        abstract val runtimeSymbolList: RegularFileProperty
    }

    abstract class PrivacySandboxSdkLinkAndroidResourcesWorkerAction
        : ProfileAwareWorkAction<PrivacySandboxSdkLinkAndroidResourcesParams>() {

        override fun run() {
            with(parameters!!) {
                val manifestFile = manifestFile.get().asFile
                val outputFile = outputResFile.get().asFile
                val symbolOutputDir = runtimeSymbolList.get().asFile.parentFile
                FileUtils.mkdirs(outputFile.parentFile)
                val config = AaptPackageConfig(
                        androidJarPath = androidJar.asFile.get().absolutePath,
                        generateProtos = true,
                        manifestFile = manifestFile,
                        options = AaptOptions(null, null),
                        resourceOutputApk = outputFile,
                        componentType = ComponentTypeImpl.BASE_APK,
                        packageId = null,
                        allowReservedPackageId = minSdk.get() < AndroidVersion.VersionCodes.O,
                        symbolOutputDir = symbolOutputDir,
                        resourceDirs = ImmutableList.Builder<File>().apply {
                            for (dir in resourceDirs.files) {
                                val resDir = File(dir, SdkConstants.FD_RES)
                                if (resDir.exists()) {
                                    add(resDir)
                                }
                            }
                        }.build(),
                )
                aapt2.get().getLeasingAapt2()
                        .link(config,
                                LoggerWrapper(Logging.getLogger(Aapt2LinkRunnable::class.java)))
            }
        }
    }

    class CreationAction(val creationConfig: PrivacySandboxSdkVariantScope) :
            TaskCreationAction<PrivacySandboxSdkLinkAndroidResourcesTask>() {

        override val name: String
            get() = "linkPrivacySandboxResources"
        override val type: Class<PrivacySandboxSdkLinkAndroidResourcesTask>
            get() = PrivacySandboxSdkLinkAndroidResourcesTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<PrivacySandboxSdkLinkAndroidResourcesTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    PrivacySandboxSdkLinkAndroidResourcesTask::outputResFile
            ).withName("bundled-res.ap_")
                    .on(PrivacySandboxSdkInternalArtifactType.LINKED_MERGE_RES_FOR_ASB)
            creationConfig.artifacts
                    .setInitialProvider(taskProvider, PrivacySandboxSdkLinkAndroidResourcesTask::runtimeSymbolList)
                    .withName(SdkConstants.FN_RESOURCE_TEXT)
                    .on(PrivacySandboxSdkInternalArtifactType.RUNTIME_SYMBOL_LIST)
        }

        override fun configure(task: PrivacySandboxSdkLinkAndroidResourcesTask) {
            task.configureVariantProperties("", task.project.gradle.sharedServices)
            task.aapt2.let { aapt2Input ->
                aapt2Input.buildService.setDisallowChanges(
                        getBuildService(task.project.gradle.sharedServices)
                )
                aapt2Input.threadPoolBuildService.setDisallowChanges(
                        getBuildService(task.project.gradle.sharedServices)
                )
                val aapt2Bin = create(task.project) { System.getenv(it.propertyName) }
                aapt2Input.binaryDirectory.setFrom(aapt2Bin.aapt2Directory)
                aapt2Input.version.setDisallowChanges(aapt2Bin.version)
                aapt2Input.maxWorkerCount.setDisallowChanges(
                        task.project.gradle.startParameter.maxWorkerCount
                )
            }
            task.androidJarInput.initialize(creationConfig, task)

            val mergedRes =
                    creationConfig.artifacts.get(PrivacySandboxSdkInternalArtifactType.MERGED_RES)
            task.mergedResourcesDirectory.setDisallowChanges(mergedRes)

            task.minSdk.setDisallowChanges(creationConfig.minSdkVersion.apiLevel)
            task.manifestFile.setDisallowChanges(
                    creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_MANIFEST))
        }
    }
}
