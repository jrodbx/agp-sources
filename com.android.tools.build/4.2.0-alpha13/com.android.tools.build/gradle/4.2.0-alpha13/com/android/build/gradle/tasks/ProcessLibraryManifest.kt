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
package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.api.artifact.ArtifactType
import com.android.build.api.variant.BuiltArtifacts
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.api.variant.impl.dirName
import com.android.build.api.variant.impl.getApiString
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.MANIFEST_MERGE_REPORT
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.manifest.mergeManifests
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.MergingReport
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException

/** a Task that only merge a single manifest with its overlays.  */
@CacheableTask
abstract class ProcessLibraryManifest : ManifestProcessorTask() {

    @get:OutputFile
    abstract val manifestOutputFile: RegularFileProperty

    @get:Optional
    @get:Input
    abstract val packageOverride: Property<String>

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    abstract val manifestOverlays: ListProperty<File>

    @get:Optional
    @get:Input
    abstract val manifestPlaceholders: MapProperty<String, String>

    /** The processed Manifests files folder.  */
    @get:OutputDirectory
    abstract val packagedManifestOutputDirectory: DirectoryProperty

    /**
     * The aapt friendly processed Manifest. In case we are processing a library manifest, some
     * placeholders may not have been resolved (and will be when the library is merged into the
     * importing application). However, such placeholders keys are not friendly to aapt which flags
     * some illegal characters. Such characters are replaced/encoded in this version.
     */
    @get:Optional
    @get:OutputDirectory
    abstract val aaptFriendlyManifestOutputDirectory: DirectoryProperty
    /**
     * The aapt friendly processed Manifest. In case we are processing a library manifest, some
     * placeholders may not have been resolved (and will be when the library is merged into the
     * importing application). However, such placeholders keys are not friendly to aapt which flags
     * some illegal characters. Such characters are replaced/encoded in this version.
     */
    @get:Internal
    val aaptFriendlyManifestOutputFile: File?
        get() = if (aaptFriendlyManifestOutputDirectory.isPresent) FileUtils.join(
            aaptFriendlyManifestOutputDirectory.get().asFile,
            mainSplit.get().dirName(),
            SdkConstants.ANDROID_MANIFEST_XML
        ) else null

    @VisibleForTesting
    @get:Nested
    abstract val mainSplit: Property<VariantOutputImpl>

    private var isNamespaced = false

    override fun doFullTaskAction() {
        workerExecutor.noIsolation().submit(ProcessLibWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.variantName.set(variantName)
            it.aaptFriendlyManifestOutputFile.set(aaptFriendlyManifestOutputFile)
            it.namespaced.set(isNamespaced)
            it.mainManifest.set(mainManifest.get())
            it.manifestOverlays.set(manifestOverlays)
            it.packageOverride.set(packageOverride)
            it.minSdkVersion.set(minSdkVersion)
            it.targetSdkVersion.set(targetSdkVersion)
            it.maxSdkVersion.set(maxSdkVersion)
            it.manifestOutputFile.set(manifestOutputFile)
            it.manifestPlaceholders.set(manifestPlaceholders)
            it.reportFile.set(reportFile)
            it.mergeBlameFile.set(mergeBlameFile)
            it.manifestOutputDirectory.set(packagedManifestOutputDirectory)
            it.aaptFriendlyManifestOutputDirectory.set(aaptFriendlyManifestOutputDirectory)
            it.mainSplit.set(mainSplit.get().toSerializedForm())
        }
    }

    abstract class ProcessLibParams: ProfileAwareWorkAction.Parameters() {
        abstract val variantName: Property<String>
        abstract val aaptFriendlyManifestOutputFile: RegularFileProperty
        abstract val namespaced: Property<Boolean>
        abstract val mainManifest: RegularFileProperty
        abstract val manifestOverlays: ListProperty<File>
        abstract val packageOverride: Property<String>
        abstract val minSdkVersion: Property<String>
        abstract val targetSdkVersion: Property<String>
        abstract val maxSdkVersion: Property<Int>
        abstract val manifestOutputFile: RegularFileProperty
        abstract val manifestPlaceholders: MapProperty<String, Any>
        abstract val reportFile: RegularFileProperty
        abstract val mergeBlameFile: RegularFileProperty
        abstract val manifestOutputDirectory: DirectoryProperty
        abstract val aaptFriendlyManifestOutputDirectory: DirectoryProperty
        abstract val mainSplit: Property<VariantOutputImpl.SerializedForm>
}

    abstract class ProcessLibWorkAction : ProfileAwareWorkAction<ProcessLibParams>() {
        override fun run() {
            val optionalFeatures: Collection<ManifestMerger2.Invoker.Feature> =
                if (parameters.namespaced.get()) listOf(
                    ManifestMerger2.Invoker.Feature.FULLY_NAMESPACE_LOCAL_RESOURCES
                ) else emptyList()
            val mergingReport = mergeManifests(
                parameters.mainManifest.asFile.get(),
                parameters.manifestOverlays.get(), emptyList(), emptyList(),
                null,
                parameters.packageOverride.get(),
                null,
                null,
                parameters.minSdkVersion.orNull,
                parameters.targetSdkVersion.orNull,
                parameters.maxSdkVersion.orNull,
                parameters.manifestOutputFile.asFile.get().absolutePath,
                parameters.aaptFriendlyManifestOutputFile.asFile.orNull?.absolutePath,
                ManifestMerger2.MergeType.LIBRARY /* outInstantRunManifestLocation */,
                parameters.manifestPlaceholders.get() /* outInstantAppManifestLocation */,
                optionalFeatures,
                emptyList(),
                parameters.reportFile.asFile.get(), LoggerWrapper.getLogger(ProcessLibraryManifest::class.java)
            )
            val mergedXmlDocument =
                mergingReport.getMergedXmlDocument(MergingReport.MergedManifestKind.MERGED)
            try {
                outputMergeBlameContents(
                    mergingReport,
                    parameters.mergeBlameFile.asFile.get()
                )
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            }
            val properties =
                if (mergedXmlDocument != null) mapOf(
                    "packageId" to mergedXmlDocument.packageName,
                    "split" to mergedXmlDocument.splitName
                ) else mapOf()
            if (parameters.manifestOutputDirectory.isPresent) {
                BuiltArtifactsImpl(
                    BuiltArtifacts.METADATA_FILE_VERSION,
                    InternalArtifactType.PACKAGED_MANIFESTS,
                    parameters.packageOverride.get(),
                    parameters.variantName.get(),
                    listOf(
                        parameters.mainSplit.get().toBuiltArtifact(
                            parameters.manifestOutputFile.asFile.get(), properties
                        )
                    )
                )
                    .saveToDirectory(parameters.manifestOutputDirectory.asFile.get())
            }
            if (parameters.aaptFriendlyManifestOutputDirectory.isPresent) {
                BuiltArtifactsImpl(
                    BuiltArtifacts.METADATA_FILE_VERSION,
                    InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS,
                    parameters.packageOverride.get(),
                    parameters.variantName.get(),
                    listOf(
                        parameters.mainSplit.get().toBuiltArtifact(
                            parameters.aaptFriendlyManifestOutputFile.asFile.get(), properties
                        )
                    )
                )
                    .saveToDirectory(parameters.aaptFriendlyManifestOutputDirectory.asFile.get())
            }
        }

    }

    @get:Optional
    @get:Input
    abstract val minSdkVersion: Property<String?>

    @get:Optional
    @get:Input
    abstract val targetSdkVersion: Property<String?>

    @get:Optional
    @get:Input
    abstract val maxSdkVersion: Property<Int?>

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFile
    abstract val mainManifest: Property<File>

    class CreationAction
    /** `EagerTaskCreationAction` for the library process manifest task.  */(creationConfig: LibraryCreationConfig) :
        VariantTaskCreationAction<ProcessLibraryManifest, LibraryCreationConfig>(creationConfig) {
        override val name: String
            get() = computeTaskName("process", "Manifest")

        override val type: Class<ProcessLibraryManifest>
            get() = ProcessLibraryManifest::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<ProcessLibraryManifest>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.taskContainer.processManifestTask = taskProvider
            val artifacts = creationConfig.artifacts
            artifacts.setInitialProvider(
                taskProvider,
                ProcessLibraryManifest::aaptFriendlyManifestOutputDirectory
            ).withName("aapt").on(InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS)

            artifacts.setInitialProvider(
                taskProvider,
                ProcessLibraryManifest::packagedManifestOutputDirectory
            ).on(InternalArtifactType.PACKAGED_MANIFESTS)

            artifacts.setInitialProvider(
                taskProvider
            ) { task: ProcessLibraryManifest -> task.manifestOutputFile }
                .on(ArtifactType.MERGED_MANIFEST)

            artifacts.setInitialProvider(
                taskProvider,
                ProcessLibraryManifest::mergeBlameFile
            ).withName("manifest-merger-blame-" + creationConfig.baseName + "-report.txt")
                .on(InternalArtifactType.MANIFEST_MERGE_BLAME_FILE)

            artifacts.setInitialProvider(
                taskProvider,
                ProcessLibraryManifest::reportFile
            )
                .atLocation(
                    FileUtils.join(creationConfig.globalScope.outputsDir, "logs")
                        .absolutePath
                )
                .withName("manifest-merger-" + creationConfig.baseName + "-report.txt")
                .on(MANIFEST_MERGE_REPORT)
        }

        override fun configure(
            task: ProcessLibraryManifest
        ) {
            super.configure(task)
            val variantSources = creationConfig.variantSources
            val project = creationConfig.globalScope.project
            task.minSdkVersion
                .set(project.provider { creationConfig.minSdkVersion.getApiString() })
            task.minSdkVersion.disallowChanges()
            task.targetSdkVersion
                .set(
                    project.provider {
                        val targetSdkVersion =
                            creationConfig.targetSdkVersion
                        if (targetSdkVersion.apiLevel < 0) {
                            return@provider null
                        }
                        targetSdkVersion.apiString
                    }
                )
            task.targetSdkVersion.disallowChanges()
            task.maxSdkVersion.setDisallowChanges(creationConfig.maxSdkVersion)
            task.mainSplit.set(project.provider { creationConfig.outputs.getMainSplit() })
            task.mainSplit.disallowChanges()
            task.isNamespaced =
                creationConfig.globalScope.extension.aaptOptions.namespaced
            task.packageOverride.setDisallowChanges(creationConfig.applicationId)
            task.manifestPlaceholders.setDisallowChanges(creationConfig.manifestPlaceholders)
            task.mainManifest
                .set(project.provider(variantSources::mainManifestFilePath))
            task.mainManifest.disallowChanges()
            task.manifestOverlays.set(
                task.project.provider(variantSources::manifestOverlays)
            )
            task.manifestOverlays.disallowChanges()
        }
    }
}
