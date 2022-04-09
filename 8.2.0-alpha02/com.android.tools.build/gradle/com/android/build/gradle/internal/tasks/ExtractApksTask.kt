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

import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.utils.toImmutableSet
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.buildanalyzer.common.TaskCategory
import com.android.bundle.Devices.DeviceSpec
import com.android.tools.build.bundletool.commands.ExtractApksCommand
import com.android.utils.FileUtils
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
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
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.FileReader
import java.nio.file.Files

/**
 * Task that extract APKs from the apk zip (created with [BundleToApkTask] into a folder. a Device
 * info file indicate which APKs to extract. Only APKs for that particular device are extracted.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.APK_PACKAGING)
abstract class ExtractApksTask : NonIncrementalTask() {

    companion object {
        const val namePrefix = "extractApksFromBundleFor"
        fun getTaskName(componentImpl: ComponentCreationConfig): String {
            return componentImpl.computeTaskName(namePrefix)
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

    @get:Input
    abstract val setIncludeMetadata: Property<Boolean>

    @get:Input
    abstract val enablePrivacySandboxSdkConsumption: Property<Boolean>

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(BundleToolRunnable::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.apkSetArchive.set(apkSetArchive)
            it.deviceConfig.set(
                deviceConfig
                    ?: throw RuntimeException("Calling ExtractApk with no device config")
            )
            it.outputDir.set(outputDir)
            it.extractInstant.set(extractInstant)
            it.apksFromBundleIdeModel.set(apksFromBundleIdeModel)
            it.applicationId.set(applicationId)
            it.variantName.set(variantName)
            it.optionalListOfDynamicModulesToInstall.set(dynamicModulesToInstall.orElse(listOf()))
            it.setIncludeMetadata.set(setIncludeMetadata)
            it.enablePrivacySandboxSdkConsumption.set(enablePrivacySandboxSdkConsumption)
        }
    }

    abstract class Params : ProfileAwareWorkAction.Parameters() {
        abstract val apkSetArchive: RegularFileProperty
        abstract val deviceConfig: Property<File>
        abstract val outputDir: DirectoryProperty
        abstract val extractInstant: Property<Boolean>
        abstract val apksFromBundleIdeModel: RegularFileProperty
        abstract val applicationId: Property<String>
        abstract val variantName: Property<String>
        abstract val optionalListOfDynamicModulesToInstall: ListProperty<String>
        abstract val setIncludeMetadata: Property<Boolean>
        abstract val enablePrivacySandboxSdkConsumption: Property<Boolean>

    }

    abstract class BundleToolRunnable : ProfileAwareWorkAction<Params>() {
        override fun run() {
            FileUtils.cleanOutputDir(parameters.outputDir.asFile.get())

            val builder: DeviceSpec.Builder = DeviceSpec.newBuilder()
            val elementList = mutableListOf<BuiltArtifactImpl>()

            Files.newBufferedReader(parameters.deviceConfig.get().toPath(), Charsets.UTF_8).use {
                JsonFormat.parser().merge(it, builder)
            }
            if (parameters.enablePrivacySandboxSdkConsumption.get() && builder.codename == "TiramisuPrivacySandbox" && builder.sdkVersion == 32) {
                // Bundle tool changed this behavior to use the 'feature version', but Studio
                // doesn't use bundle tool to generate the device config
                // so to unblock b/235469089 just hard code this special case
                builder.sdkVersion = 33
            }

            val command = ExtractApksCommand
                .builder()
                .setApksArchivePath(parameters.apkSetArchive.asFile.get().toPath())
                .setDeviceSpec(builder.build())
                .setOutputDirectory(parameters.outputDir.asFile.get().toPath())
                .setInstant(parameters.extractInstant.get())
                .also {
                    if (parameters.optionalListOfDynamicModulesToInstall.get().isNotEmpty()) {
                        it.setModules(
                                parameters.optionalListOfDynamicModulesToInstall.get().toImmutableSet()
                        )
                        it.setIncludeMetadata(parameters.setIncludeMetadata.get())
                    }

                }

            command.build().execute()

            if (parameters.setIncludeMetadata.get()) {
                val metadataJson = parameters.outputDir.file("metadata.json").get().asFile
                val fileReader = FileReader(metadataJson)
                val reader = JsonReader(fileReader)
                val deliveryTypeMap = mutableMapOf<String, String>()
                var path = ""
                reader.beginObject()
                while (reader.hasNext() && reader.peek() != JsonToken.END_OBJECT) {
                    val name = reader.nextName()
                    if (name == "apks") {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "moduleName" -> reader.nextString()
                                    "path" -> path = reader.nextString()
                                    "deliveryType" -> deliveryTypeMap[path] = reader.nextString()
                                }
                            }
                            reader.endObject()
                        }
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
                deliveryTypeMap.forEach { elementList.add(BuiltArtifactImpl.make(outputFile = parameters.outputDir.asFile.get().absolutePath + "/" + it.key,
                        attributes = mapOf("deliveryType" to it.value))) }

                fileReader.close()
                FileUtils.deleteIfExists(metadataJson)
            }

            BuiltArtifactsImpl(
                artifactType = InternalArtifactType.EXTRACTED_APKS,
                applicationId = parameters.applicationId.get(),
                variantName = parameters.variantName.get(),
                elements = if (elementList.isEmpty()) {
                    parameters.outputDir.asFileTree.files.forEach { elementList.add(BuiltArtifactImpl.make(outputFile = it.absolutePath)) }
                    elementList
                }
            else elementList

            ).saveToFile(parameters.apksFromBundleIdeModel.asFile.get())
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
            task.setIncludeMetadata.setDisallowChanges(creationConfig.services.projectOptions[BooleanOption.ENABLE_LOCAL_TESTING])
            task.enablePrivacySandboxSdkConsumption.setDisallowChanges(creationConfig.services.projectOptions[BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT])
        }
    }
}
