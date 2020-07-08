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

package com.android.build.gradle.internal.tasks

import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.utils.toImmutableSet
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.bundle.Devices.DeviceSpec
import com.android.tools.build.bundletool.commands.ExtractApksCommand
import com.android.utils.FileUtils
import com.google.protobuf.util.JsonFormat
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.Serializable
import java.nio.file.Files
import javax.inject.Inject

/**
 * Task that extract APKs from the apk zip (created with [BundleToApkTask] into a folder. a Device
 * info file indicate which APKs to extract. Only APKs for that particular device are extracted.
 */
abstract class ExtractApksTask : NonIncrementalTask() {

    companion object {
        const val namePrefix = "extractApksFor"
        fun getTaskName(componentProperties: ComponentPropertiesImpl): String {
            return componentProperties.computeTaskName(namePrefix)
        }
    }

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val apkSetArchive: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    var deviceConfig: File? = null
        private set

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:OutputFile
    abstract val apksFromBundleIdeModel: RegularFileProperty

    @get:Input
    abstract val applicationId: Property<String>

    @get:Input
    @get:Optional
    abstract val dynamicModulesToInstall: ListProperty<String>

    @get:Input
    var extractInstant = false
        private set

    override fun doTaskAction() {

        getWorkerFacadeWithWorkers().use {
            it.submit(
                BundleToolRunnable::class.java,
                Params(
                    apkSetArchive.get().asFile,
                    deviceConfig
                            ?: throw RuntimeException("Calling ExtractApk with no device config"),
                    outputDir.get().asFile,
                    extractInstant,
                    apksFromBundleIdeModel.get().asFile,
                    applicationId.get(),
                    variantName,
                    dynamicModulesToInstall.getOrElse(listOf())
                )
            )
        }
    }

    private data class Params(
        val apkSetArchive: File,
        val deviceConfig: File,
        val outputDir: File,
        val extractInstant: Boolean,
        val apksFromBundleIdeModel: File,
        val applicationId: String,
        val variantName: String,
        val optionalListOfDynamicModulesToInstall: List<String>
    ) : Serializable

    private class BundleToolRunnable @Inject constructor(private val params: Params): Runnable {
        override fun run() {
            FileUtils.cleanOutputDir(params.outputDir)

            val builder: DeviceSpec.Builder = DeviceSpec.newBuilder()

            Files.newBufferedReader(params.deviceConfig.toPath(), Charsets.UTF_8).use {
                JsonFormat.parser().merge(it, builder)
            }

            val command = ExtractApksCommand
                .builder()
                .setApksArchivePath(params.apkSetArchive.toPath())
                .setDeviceSpec(builder.build())
                .setOutputDirectory(params.outputDir.toPath())
                .setInstant(params.extractInstant)
                .also {
                    if (params.optionalListOfDynamicModulesToInstall.isNotEmpty())
                        it.setModules(
                            params.optionalListOfDynamicModulesToInstall.toImmutableSet())
                }

            command.build().execute()

            BuiltArtifactsImpl(
                artifactType = InternalArtifactType.EXTRACTED_APKS,
                applicationId = params.applicationId,
                variantName = params.variantName,
                elements = listOf(
                    BuiltArtifactImpl.make(outputFile = params.outputDir.absolutePath)
                )
            ).saveToFile(params.apksFromBundleIdeModel)
        }
    }

    class CreationAction(creationConfig: ApkCreationConfig) :
        VariantTaskCreationAction<ExtractApksTask, ApkCreationConfig>(
            creationConfig
        ) {

        override val name: String
            get() = computeTaskName(namePrefix)
        override val type: Class<ExtractApksTask>
            get() = ExtractApksTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<ExtractApksTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ExtractApksTask::outputDir)
                .on(InternalArtifactType.EXTRACTED_APKS)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    ExtractApksTask::apksFromBundleIdeModel)
                .withName(BuiltArtifactsImpl.METADATA_FILE_NAME)
                .on(InternalArtifactType.APK_FROM_BUNDLE_IDE_MODEL)
        }

        override fun configure(
            task: ExtractApksTask
        ) {
            super.configure(task)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.APKS_FROM_BUNDLE,
                task.apkSetArchive)

            val devicePath = creationConfig.services.projectOptions.get(StringOption.IDE_APK_SELECT_CONFIG)
            if (devicePath != null) {
                task.deviceConfig = File(devicePath)
            }

            task.extractInstant = creationConfig.services.projectOptions.get(BooleanOption.IDE_EXTRACT_INSTANT)
            task.applicationId.setDisallowChanges(creationConfig.applicationId)
            val optionalListOfDynamicModulesToInstall = creationConfig.services.projectOptions.get(
                StringOption.IDE_INSTALL_DYNAMIC_MODULES_LIST
            )
            if (!optionalListOfDynamicModulesToInstall.isNullOrEmpty()) {
                task.dynamicModulesToInstall.addAll(
                    optionalListOfDynamicModulesToInstall.split(','))
            }
            task.dynamicModulesToInstall.disallowChanges()
        }
    }
}
