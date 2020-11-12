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
import com.android.build.api.variant.BuiltArtifacts
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.api.variant.impl.dirName
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.MANIFEST_MERGE_REPORT
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.manifest.mergeManifestsForApplication
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
import java.io.Serializable
import java.io.UncheckedIOException
import javax.inject.Inject

/** a Task that only merge a single manifest with its overlays.  */
@CacheableTask
abstract class ProcessLibraryManifest : ManifestProcessorTask() {

    @get:OutputFile
    abstract val manifestOutputFile: RegularFileProperty

    @get:Optional
    @get:Input
    abstract val packageOverride: Property<String>

    @get:Input
    @get:Optional
    abstract val versionCode: Property<Int?>

    @get:Optional
    @get:Input
    abstract val versionName: Property<String?>

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    abstract val manifestOverlays: ListProperty<File>

    @get:Optional
    @get:Input
    abstract val manifestPlaceholders: MapProperty<String, Any>

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
        getWorkerFacadeWithWorkers().use { workers ->
            val manifestOutputDirectory = packagedManifestOutputDirectory
            val aaptFriendlyManifestOutputDirectory =
                aaptFriendlyManifestOutputDirectory
            workers.submit(
                ProcessLibRunnable::class.java,
                ProcessLibParams(
                    variantName,
                    aaptFriendlyManifestOutputFile,
                    isNamespaced,
                    mainManifest.get(),
                    manifestOverlays.get(),
                    packageOverride.get(),
                    versionCode.orNull,
                    versionName.orNull,
                    minSdkVersion.orNull,
                    targetSdkVersion.orNull,
                    maxSdkVersion.orNull,
                    manifestOutputFile.get().asFile,
                    manifestPlaceholders.get(),
                    reportFile.get().asFile,
                    mergeBlameFile.get().asFile,
                    if (manifestOutputDirectory.isPresent) manifestOutputDirectory.get().asFile else null,
                    if (aaptFriendlyManifestOutputDirectory.isPresent) aaptFriendlyManifestOutputDirectory.get().asFile else null,
                    mainSplit.get().toSerializedForm()
                )
            )
        }
    }

    class ProcessLibParams(
        val variantName: String,
        val aaptFriendlyManifestOutputFile: File?,
        val isNamespaced: Boolean,
        val mainManifest: File,
        val manifestOverlays: List<File>,
        val packageOverride: String,
        val versionCode: Int?,
        val versionName: String?,
        val minSdkVersion: String?,
        val targetSdkVersion: String?,
        val maxSdkVersion: Int?,
        val manifestOutputFile: File,
        val manifestPlaceholders: Map<String, Any>,
        val reportFile: File,
        val mergeBlameFile: File,
        val manifestOutputDirectory: File?,
        val aaptFriendlyManifestOutputDirectory: File?,
        val mainSplit: VariantOutputImpl.SerializedForm
    ) : Serializable

    class ProcessLibRunnable @Inject constructor(private val params: ProcessLibParams) :
        Runnable {
        override fun run() {
            val optionalFeatures: Collection<ManifestMerger2.Invoker.Feature> =
                if (params.isNamespaced) listOf(
                    ManifestMerger2.Invoker.Feature.FULLY_NAMESPACE_LOCAL_RESOURCES
                ) else emptyList()
            val mergingReport = mergeManifestsForApplication(
                params.mainManifest,
                params.manifestOverlays, emptyList(), emptyList(),
                null,
                params.packageOverride,
                params.versionCode,
                params.versionName,
                params.minSdkVersion,
                params.targetSdkVersion,
                params.maxSdkVersion,
                params.manifestOutputFile.absolutePath,
                if (params.aaptFriendlyManifestOutputFile != null) params.aaptFriendlyManifestOutputFile.absolutePath else null,
                ManifestMerger2.MergeType.LIBRARY /* outInstantRunManifestLocation */,
                params.manifestPlaceholders /* outInstantAppManifestLocation */,
                optionalFeatures,
                emptyList(),
                params.reportFile, LoggerWrapper.getLogger(ProcessLibraryManifest::class.java)
            )
            val mergedXmlDocument =
                mergingReport.getMergedXmlDocument(MergingReport.MergedManifestKind.MERGED)
            try {
                outputMergeBlameContents(
                    mergingReport,
                    params.mergeBlameFile
                )
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            }
            val properties =
                if (mergedXmlDocument != null) mapOf(
                    "packageId" to mergedXmlDocument.packageName,
                    "split" to mergedXmlDocument.splitName
                ) else mapOf()
            if (params.manifestOutputDirectory != null) {
                BuiltArtifactsImpl(
                    BuiltArtifacts.METADATA_FILE_VERSION,
                    InternalArtifactType.PACKAGED_MANIFESTS,
                    params.packageOverride,
                    params.variantName,
                    listOf(
                        params.mainSplit.toBuiltArtifact(
                            params.manifestOutputFile, properties
                        )
                    )
                )
                    .saveToDirectory(params.manifestOutputDirectory)
            }
            if (params.aaptFriendlyManifestOutputDirectory != null) {
                BuiltArtifactsImpl(
                    BuiltArtifacts.METADATA_FILE_VERSION,
                    InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS,
                    params.packageOverride,
                    params.variantName,
                    listOf(
                        params.mainSplit.toBuiltArtifact(
                            params.aaptFriendlyManifestOutputFile!!, properties
                        )
                    )
                )
                    .saveToDirectory(params.aaptFriendlyManifestOutputDirectory)
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
                .withName(SdkConstants.ANDROID_MANIFEST_XML)
                .on(InternalArtifactType.LIBRARY_MANIFEST)

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
            val variantDslInfo = creationConfig.variantDslInfo
            val variantSources = creationConfig.variantSources
            val project = creationConfig.globalScope.project
            task.minSdkVersion
                .set(project.provider { creationConfig.minSdkVersion.apiString })
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
            task.maxSdkVersion
                .set(project.provider(creationConfig::maxSdkVersion))
            task.maxSdkVersion.disallowChanges()
            task.mainSplit.set(project.provider { creationConfig.outputs.getMainSplit() })
            task.mainSplit.disallowChanges()
            task.isNamespaced =
                creationConfig.globalScope.extension.aaptOptions.namespaced
            task.versionName.setDisallowChanges(variantDslInfo.versionName)
            task.versionCode.setDisallowChanges(variantDslInfo.versionCode)
            task.packageOverride.set(creationConfig.applicationId)
            task.packageOverride.disallowChanges()
            task.manifestPlaceholders.set(
                task.project.provider(
                    variantDslInfo::manifestPlaceholders
                )
            )
            task.manifestPlaceholders.disallowChanges()
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