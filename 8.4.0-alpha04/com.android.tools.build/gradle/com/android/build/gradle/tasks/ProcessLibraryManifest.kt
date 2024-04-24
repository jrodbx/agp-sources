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
import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.getApiString
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.MANIFEST_MERGE_REPORT
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.manifest.mergeManifests
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.buildanalyzer.common.TaskCategory
import com.android.manifmerger.ManifestMerger2
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
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
@BuildAnalyzer(primaryTaskCategory = TaskCategory.MANIFEST)
abstract class ProcessLibraryManifest : ManifestProcessorTask() {

    @get:OutputFile
    abstract val manifestOutputFile: RegularFileProperty

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles // Note: The files may not exist
    abstract val manifestOverlayFilePaths: ListProperty<File>

    @get:Optional
    @get:Input
    abstract val manifestPlaceholders: MapProperty<String, String>

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
            SdkConstants.ANDROID_MANIFEST_XML
        ) else null

    private var isNamespaced = false

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(ProcessLibWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.variantName.set(variantName)
            it.aaptFriendlyManifestOutputFile.set(aaptFriendlyManifestOutputFile)
            it.namespaced.set(isNamespaced)
            it.mainManifest.set(
                    if (mainManifest.get().asFile.isFile) {
                        mainManifest.get().asFile
                    } else {
                        createTempLibraryManifest(tmpDir.get().asFile, namespace.orNull)
                    }
            )
            it.manifestOverlays.set(manifestOverlayFilePaths.get().filter(File::isFile))
            it.namespace.set(namespace)
            it.minSdkVersion.set(minSdkVersion)
            it.targetSdkVersion.set(targetSdkVersion)
            it.maxSdkVersion.set(maxSdkVersion)
            it.manifestOutputFile.set(manifestOutputFile)
            it.manifestPlaceholders.set(manifestPlaceholders)
            it.reportFile.set(reportFile)
            it.mergeBlameFile.set(mergeBlameFile)
            it.aaptFriendlyManifestOutputDirectory.set(aaptFriendlyManifestOutputDirectory)
            it.tmpDir.set(tmpDir.get())
            it.disableMinSdkVersionCheck.set(disableMinSdkVersionCheck)
        }
    }

    abstract class ProcessLibParams: ProfileAwareWorkAction.Parameters() {
        abstract val variantName: Property<String>
        abstract val aaptFriendlyManifestOutputFile: RegularFileProperty
        abstract val namespaced: Property<Boolean>
        abstract val mainManifest: RegularFileProperty
        abstract val manifestOverlays: ListProperty<File>
        abstract val namespace: Property<String>
        abstract val minSdkVersion: Property<String>
        abstract val targetSdkVersion: Property<String>
        abstract val maxSdkVersion: Property<Int>
        abstract val manifestOutputFile: RegularFileProperty
        abstract val manifestPlaceholders: MapProperty<String, Any>
        abstract val reportFile: RegularFileProperty
        abstract val mergeBlameFile: RegularFileProperty
        abstract val aaptFriendlyManifestOutputDirectory: DirectoryProperty
        abstract val tmpDir: DirectoryProperty
        abstract val disableMinSdkVersionCheck: Property<Boolean>
    }

    abstract class ProcessLibWorkAction : ProfileAwareWorkAction<ProcessLibParams>() {
        override fun run() {
            val optionalFeatures: Collection<ManifestMerger2.Invoker.Feature> =
                if (parameters.namespaced.get()) listOf(
                    ManifestMerger2.Invoker.Feature.FULLY_NAMESPACE_LOCAL_RESOURCES
                ) else emptyList()
            if (parameters.disableMinSdkVersionCheck.get()) {
                optionalFeatures.plus(ManifestMerger2.Invoker.Feature.DISABLE_MINSDKLIBRARY_CHECK)
            }
            val mergingReport = mergeManifests(
                parameters.mainManifest.asFile.get(),
                parameters.manifestOverlays.get(),
                dependencies = emptyList(),
                navigationJsons = emptyList(),
                featureName = null,
                packageOverride = parameters.namespace.get(),
                namespace = parameters.namespace.get(),
                profileable = false,
                versionCode = null,
                versionName = null,
                parameters.minSdkVersion.orNull,
                parameters.targetSdkVersion.orNull,
                parameters.maxSdkVersion.orNull,
                testOnly = false,
                extractNativeLibs = null,
                parameters.manifestOutputFile.asFile.get().absolutePath,
                parameters.aaptFriendlyManifestOutputFile.asFile.orNull?.absolutePath /* outInstantRunManifestLocation */,
                ManifestMerger2.MergeType.LIBRARY /* outInstantAppManifestLocation */,
                parameters.manifestPlaceholders.get(),
                optionalFeatures,
                emptyList(),
                generatedLocaleConfigAttribute = null,
                parameters.reportFile.asFile.get(),
                LoggerWrapper.getLogger(ProcessLibraryManifest::class.java)
            )
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
            if (parameters.aaptFriendlyManifestOutputDirectory.isPresent) {
                BuiltArtifactsImpl(
                    BuiltArtifacts.METADATA_FILE_VERSION,
                    InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS,
                    parameters.namespace.get(),
                    parameters.variantName.get(),
                    listOf(
                        BuiltArtifactImpl.make(
                            outputFile = parameters.aaptFriendlyManifestOutputFile.asFile.get().absolutePath
                        )
                    )
                ).saveToDirectory(parameters.aaptFriendlyManifestOutputDirectory.asFile.get())
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

    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputFiles // Use InputFiles rather than InputFile to allow the file not to exist
    abstract val mainManifest: RegularFileProperty

    @get:Optional
    @get:Input
    abstract val namespace: Property<String?>

    @get:Internal
    abstract val tmpDir: DirectoryProperty

    @get:Optional
    @get:Input
    abstract val disableMinSdkVersionCheck: Property<Boolean>

    class CreationAction(
        creationConfig: ComponentCreationConfig,
        private val targetSdkVersion: AndroidVersion?,
        private val maxSdkVersion: Int?
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
                ProcessLibraryManifest::manifestOutputFile
            ).on(SingleArtifact.MERGED_MANIFEST)

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
            task.minSdkVersion.setDisallowChanges(creationConfig.minSdk.getApiString())
            task.targetSdkVersion
                .setDisallowChanges(
                    if (targetSdkVersion == null || targetSdkVersion.apiLevel < 1) null
                    else targetSdkVersion.getApiString()
                )
            task.maxSdkVersion.setDisallowChanges(maxSdkVersion)
            task.isNamespaced = creationConfig.global.namespacedAndroidResources
            creationConfig.manifestPlaceholdersCreationConfig?.placeholders?.let {
                task.manifestPlaceholders.setDisallowChanges(it)
            }
            task.mainManifest.fileProvider(creationConfig.sources.manifestFile)
            task.mainManifest.disallowChanges()
            task.manifestOverlayFilePaths.setDisallowChanges(creationConfig.sources.manifestOverlayFiles)
            task.namespace.setDisallowChanges(creationConfig.namespace)
            task.tmpDir.setDisallowChanges(creationConfig.paths.intermediatesDir(
                    "tmp",
                    "ProcessLibraryManifest",
                    creationConfig.dirName
            ))
            task.disableMinSdkVersionCheck.setDisallowChanges(
                    creationConfig.services.projectOptions[BooleanOption.DISABLE_MINSDKLIBRARY_CHECK])
        }
    }
}

fun createTempLibraryManifest(tempDir: File, namespace: String?): File {
    Preconditions.checkNotNull(namespace, "namespace cannot be null.")
    val manifestFile = File.createTempFile("tempAndroidManifest", ".xml", FileUtils.mkdirs(tempDir))
    val content = """<?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"/>
            """.trimIndent()
    manifestFile.writeText(content)
    return manifestFile
}
