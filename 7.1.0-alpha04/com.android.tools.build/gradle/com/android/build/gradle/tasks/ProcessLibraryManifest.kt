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
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.BuiltArtifacts
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.api.variant.impl.dirName
import com.android.build.api.variant.impl.getApiString
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.ComponentCreationConfig
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
import com.google.common.base.Preconditions
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
            it.mainManifest.set(
                    if (mainManifest.isPresent()) {
                        mainManifest.get()
                    } else {
                        createTempLibraryManifest()
                    }
            )
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
            it.tmpDir.set(tmpDir.get())
        }
    }

    private fun createTempLibraryManifest(): File {
        Preconditions.checkNotNull(
                namespace.get(),
                "namespace cannot be null."
        )
        val manifestFile = File.createTempFile("tempAndroidManifest", ".xml", FileUtils.mkdirs(tmpDir.get().asFile))
        val content = """<?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
               package="${namespace.get()}" />
            """.trimIndent()
        manifestFile.writeText(content)
        return manifestFile
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
        abstract val tmpDir: DirectoryProperty
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
                if (FileUtils.isFileInDirectory(parameters.mainManifest.get().asFile, parameters.tmpDir.get().asFile)) {
                    FileUtils.deleteIfExists(parameters.mainManifest.get().asFile)
                }
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
    @get:Optional
    abstract val mainManifest: Property<File>

    @get:Optional
    @get:Input
    abstract val namespace: Property<String?>

    @get:Internal
    abstract val tmpDir: DirectoryProperty

    class CreationAction(
        creationConfig: ComponentCreationConfig,
        private val targetSdkVersion: AndroidVersion?,
        private val maxSdkVersion: Int?,
        private val manifestPlaceholders: MapProperty<String, String>?
    ) : VariantTaskCreationAction<ProcessLibraryManifest, ComponentCreationConfig>(
        creationConfig
    ) {
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
                .on(SingleArtifact.MERGED_MANIFEST)

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
                    FileUtils.join(creationConfig.services.projectInfo.getOutputsDir(), "logs")
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
            val project = creationConfig.services.projectInfo.getProject()
            task.minSdkVersion.setDisallowChanges(creationConfig.minSdkVersion.getApiString())
            task.targetSdkVersion
                .setDisallowChanges(
                    if (targetSdkVersion == null || targetSdkVersion.apiLevel < 1) null
                    else targetSdkVersion.getApiString()
                )
            task.maxSdkVersion.setDisallowChanges(maxSdkVersion)
            task.mainSplit.set(project.provider { creationConfig.outputs.getMainSplit() })
            task.mainSplit.disallowChanges()
            task.isNamespaced =
                creationConfig.services.projectInfo.getExtension().aaptOptions.namespaced
            task.packageOverride.setDisallowChanges(creationConfig.applicationId)
            manifestPlaceholders?.let {
                task.manifestPlaceholders.setDisallowChanges(it)
            }
            task.mainManifest
                .set(project.provider(variantSources::mainManifestIfExists))
            task.mainManifest.disallowChanges()
            task.manifestOverlays.set(
                task.project.provider(variantSources::manifestOverlays)
            )
            task.manifestOverlays.disallowChanges()
            task.namespace.setDisallowChanges(creationConfig.namespace)
            task.tmpDir.setDisallowChanges(creationConfig.paths.intermediatesDir(
                    "tmp",
                    "ProcessLibraryManifest",
                    creationConfig.dirName
            ))
        }
    }
}
