/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.buildanalyzer.common.TaskCategory
import com.android.utils.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.lang.IllegalStateException
import java.util.zip.ZipFile

/**
 * Task to produce a directory containing .apks files generated from Privacy Sandbox ASAR files.
 *
 * The apks files are used for deploying applications using Privacy Sandbox to device that do not
 * have support for the SandboxSdkManager service.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.APK_PACKAGING)
abstract class ExtractPrivacySandboxCompatApks: NonIncrementalTask() {

    @get:Classpath
    abstract val privacySandboxSplitCompatApks: ConfigurableFileCollection

    @get:Input
    abstract val privacySandboxEnabled: Property<Boolean>

    @get:Input
    abstract val applicationId: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:OutputFile
    abstract val apksFromBundleIdeModel: RegularFileProperty

    override fun doTaskAction() {
        if (!privacySandboxEnabled.get()) {
            throw IllegalStateException(
                    "Unable to execute task as Privacy Sandbox support is not enabled. \n" +
                            "To enable support, add\n" +
                            "    ${BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT.propertyName}=true\n" +
                            "to your project's gradle.properties file."
            )
        }

        val elements = mutableListOf<BuiltArtifactImpl>()

        privacySandboxSplitCompatApks.files.forEach { zippedApks ->
            ZipFile(zippedApks).use { apks ->
                if (apks.getEntry("toc.pb") != null) {
                    throw IllegalStateException(
                            "Apks ${zippedApks.absolutePath} must not contain a toc.pb file.")
                }
                apks.entries().asIterator().forEach {
                    if (it.name.contains(".apk")) {
                        val apkBytes = apks.getInputStream(it).readBytes()
                        val outputApkFile = File(outputDir.get().asFile, it.name)
                        FileUtils.createFile(outputApkFile, "")
                        outputApkFile.writeBytes(apkBytes)
                        elements.add(BuiltArtifactImpl.make(outputFile = outputApkFile.absolutePath))
                    }
                }
            }
        }

        writeMetadata(elements)
    }

    private fun writeMetadata(elementList: MutableList<BuiltArtifactImpl>) {
        BuiltArtifactsImpl(
                artifactType = InternalArtifactType.EXTRACTED_SDK_APKS,
                applicationId = applicationId.get(),
                variantName = "",
                elements = elementList

        ).saveToFile(outputDir.file(BuiltArtifactsImpl.METADATA_FILE_NAME).get().asFile)
    }

    class CreationAction(creationAction: ApkCreationConfig) :
            VariantTaskCreationAction<ExtractPrivacySandboxCompatApks, ApkCreationConfig>(creationAction) {

        override val name: String
            get() = computeTaskName("extractApksFromSdkSplitsFor")
        override val type: Class<ExtractPrivacySandboxCompatApks>
            get() = ExtractPrivacySandboxCompatApks::class.java

        override fun handleProvider(
                taskProvider: TaskProvider<ExtractPrivacySandboxCompatApks>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    ExtractPrivacySandboxCompatApks::outputDir)
                    .on(InternalArtifactType.EXTRACTED_SDK_APKS)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    ExtractPrivacySandboxCompatApks::apksFromBundleIdeModel)
                    .withName(BuiltArtifactsImpl.METADATA_FILE_NAME)
                    .on(InternalArtifactType.APK_FROM_SDKS_IDE_MODEL)
        }

        override fun configure(task: ExtractPrivacySandboxCompatApks) {
            super.configure(task)
            task.privacySandboxSplitCompatApks.from(
                    creationConfig.artifacts.get(InternalArtifactType.SDK_SPLITS_APKS)
                            .map { it.asFileTree }
            )
            task.applicationId.setDisallowChanges(creationConfig.applicationId)
            task.privacySandboxEnabled.setDisallowChanges(creationConfig.services.projectOptions[BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT])
        }
    }
}
