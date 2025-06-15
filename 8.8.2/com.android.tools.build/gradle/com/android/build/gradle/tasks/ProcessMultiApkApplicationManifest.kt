/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.MultiOutputHandler
import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.api.variant.impl.dirName
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.manifest.mergeManifests
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.manifmerger.ManifestMerger2
import com.android.utils.FileUtils
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File

/**
 * Task that consumes [SingleArtifact.MERGED_MANIFEST] single merged manifest and create several
 * versions that are each suitable for all [VariantOutputImpl] for this variant.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.MANIFEST)
abstract class ProcessMultiApkApplicationManifest: ManifestProcessorTask() {

    @get:Nested
    abstract val outputsHandler: Property<MultiOutputHandler>

    @get:Input
    abstract val applicationId: Property<String>

    @get:Input
    abstract val namespace: Property<String>

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputFile
    abstract val mainMergedManifest: RegularFileProperty

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    @get:InputFiles
    abstract val compatibleScreensManifest: DirectoryProperty

    /** The merged Manifests files folder.  */
    @get:OutputDirectory
    abstract val multiApkManifestOutputDirectory: DirectoryProperty

    override fun doTaskAction() {
        // read the output of the compatible screen manifest.
        val compatibleScreenManifests =
            BuiltArtifactsLoaderImpl().load(compatibleScreensManifest)
                ?: throw RuntimeException(
                    "Cannot find generated compatible screen manifests, file a bug"
                )

        val multiApkManifestOutputs = mutableListOf<BuiltArtifactImpl>()

        for (variantOutput in outputsHandler.get().getOutputs { true }) {
            val compatibleScreenManifestForSplit =
                compatibleScreenManifests.getBuiltArtifact(variantOutput.variantOutputConfiguration)

            val mergedManifestOutputFile =
                processVariantOutput(compatibleScreenManifestForSplit?.outputFile, variantOutput)

            multiApkManifestOutputs.add(variantOutput.toBuiltArtifact(mergedManifestOutputFile))
        }
        BuiltArtifactsImpl(
            artifactType = InternalArtifactType.MERGED_MANIFESTS,
            applicationId = applicationId.get(),
            variantName = variantName,
            elements = multiApkManifestOutputs,
        ).save(multiApkManifestOutputDirectory.get())
    }

    private fun processVariantOutput(
        compatibleScreensManifestFilePath: String?,
        variantOutput: VariantOutputImpl.SerializedForm,
    ): File {
        val dirName = variantOutput.variantOutputConfiguration.dirName()

        val mergedManifestOutputFile = File(
            multiApkManifestOutputDirectory.get().asFile,
            FileUtils.join(
                dirName,
                SdkConstants.ANDROID_MANIFEST_XML
            )
        )

        if (compatibleScreensManifestFilePath == null) {
            if (variantOutput.versionCode == outputsHandler.get().mainVersionCode
                && variantOutput.versionName == outputsHandler.get().mainVersionName) {

                mainMergedManifest.get().asFile.copyTo(mergedManifestOutputFile, overwrite = true)
                return mergedManifestOutputFile
            }
        }
        mergeManifests(
            mainMergedManifest.get().asFile,
            if (compatibleScreensManifestFilePath != null)
                listOf(File(compatibleScreensManifestFilePath))
            else listOf(),
            listOf(),
            listOf(),
            null,
            packageOverride = null,
            namespace = namespace.get(),
            false,
            variantOutput.versionCode,
            variantOutput.versionName,
            null,
            null,
            null,
            testOnly = false,
            extractNativeLibs = null,
            mergedManifestOutputFile.absolutePath /* aaptFriendlyManifestOutputFile */,
            null,
            ManifestMerger2.MergeType.APPLICATION,
            mapOf(),
            listOf(),
            listOf(),
            generatedLocaleConfigAttribute = null,
            null,
            LoggerWrapper.getLogger(ProcessApplicationManifest::class.java),
            checkIfPackageInMainManifest = false
        )
        return mergedManifestOutputFile
    }

    class CreationAction(
        creationConfig: ApplicationCreationConfig
    ) : VariantTaskCreationAction<ProcessMultiApkApplicationManifest, ApplicationCreationConfig>(creationConfig) {
        override val name: String
            get() = computeTaskName("process", "Manifest")
        override val type: Class<ProcessMultiApkApplicationManifest>
            get() = ProcessMultiApkApplicationManifest::class.java

        override fun handleProvider(taskProvider: TaskProvider<ProcessMultiApkApplicationManifest>) {
            super.handleProvider(taskProvider)
            creationConfig.taskContainer.processManifestTask = taskProvider
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ProcessMultiApkApplicationManifest::multiApkManifestOutputDirectory
            ).on(InternalArtifactType.MERGED_MANIFESTS)
        }

        override fun configure(task: ProcessMultiApkApplicationManifest) {
            super.configure(task)

            task.outputsHandler.setDisallowChanges(
                MultiOutputHandler.create(creationConfig)
            )

            task.compatibleScreensManifest.setDisallowChanges(
                creationConfig.artifacts.get(InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST)
            )

            creationConfig
                .artifacts
                .setTaskInputToFinalProduct(
                    SingleArtifact.MERGED_MANIFEST,
                    task.mainMergedManifest
                )

            task.applicationId.setDisallowChanges(creationConfig.applicationId)
            task.namespace.setDisallowChanges(creationConfig.namespace)
        }
    }
}
