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

package com.android.build.gradle.tasks

import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.FD_JNI
import com.android.SdkConstants.FN_NAVIGATION_JSON
import com.android.SdkConstants.FN_PROGUARD_TXT
import com.android.build.api.artifact.Artifact
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryConstants
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryGlobalScope
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType.*
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.internal.tasks.AarMetadataTask.Companion.AAR_METADATA_FILE_NAME
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.MergeNativeLibsTask
import com.android.build.gradle.internal.tasks.NonIncrementalGlobalTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.tasks.mergeJavaNativeLibs
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.packaging.JarFlinger
import com.android.ide.common.r8.ConsumerRuleGlobalGuardian
import com.android.utils.usLocaleCapitalize
import org.gradle.api.attributes.DocsType
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.File.*

/**
 * Responsible for merging multiple library artifacts of the same artifact type into a single
 * artifact in the fused library aar, for future consumption. The task is intended to be configured
 * for each artifact type. Eventually these operations can be migrated to artifact transforms
 * once this feature is supported in the fused library plugin.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.MISC, secondaryTaskCategories = [TaskCategory.MERGING, TaskCategory.FUSING])
abstract class FusedLibraryMergeArtifactTask : NonIncrementalGlobalTask() {

    @get:Input
    abstract val artifactType: Property<ArtifactType>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val artifactFiles: ConfigurableFileCollection

    @get:OutputDirectory
    @get:Optional
    abstract val outputDir: DirectoryProperty

    @get:OutputFile
    @get:Optional
    abstract val outputFile: RegularFileProperty

    @get:Nested
    abstract val aarMetadataInputs: AarMetadataInputs

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(FusedLibraryMergeArtifactWorkAction::class.java) {
            it.initializeFromBaseTask(this)
            it.artifactType.set(artifactType)
            it.aarMetadataInputs = aarMetadataInputs
            it.input.setFrom(artifactFiles)
            it.output.setDisallowChanges(
                    if (outputDir.isPresent) outputDir else outputFile
            )
        }
    }

    abstract class FusedLibraryMergeArtifactParams : ProfileAwareWorkAction.Parameters() {
        abstract var aarMetadataInputs: AarMetadataInputs
        abstract val artifactType: Property<ArtifactType>
        abstract val input: ConfigurableFileCollection
        abstract val output: Property<FileSystemLocation>
    }

    abstract class FusedLibraryMergeArtifactWorkAction
        : ProfileAwareWorkAction<FusedLibraryMergeArtifactParams>() {

        override fun run() {
            with(parameters!!) {
                val inputFiles = input.files.toList()
                when (val currentArtifactType = artifactType.get()) {
                    ArtifactType.AAR_METADATA -> {
                        writeMergedMetadata(inputFiles, output.get().asFile,
                            aarMetadataInputs.minAgpVersion.orNull,
                            aarMetadataInputs.minCompileSdk.orNull,
                            aarMetadataInputs.minCompileSdkExtension.orNull)
                    }
                    ArtifactType.ASSETS -> {
                        val aarOutputAssetsOutputDir = output.get().asFile
                        for (dir in inputFiles.reversed()) {
                            dir.copyRecursively(aarOutputAssetsOutputDir, overwrite = true)
                        }
                    }
                    ArtifactType.JNI -> {
                        val aarOutputJniOutputDir = output.get().asFile.resolve(FD_JNI)
                        val relativeInputFiles = inputFiles
                            .flatMap { it.walkBottomUp() }
                            .filter { it.isFile }
                            .map {
                                MergeNativeLibsTask.InputFile(it,
                                    it.toString()
                                        .substringAfter("$separator$FD_JNI$separator")
                                )
                            }
                        mergeJavaNativeLibs(
                            inputFiles = relativeInputFiles,
                            emptySet(),
                            emptySet(),
                            emptySet(),
                            testOnlyDir = null,
                            projectNativeLibs = emptySet(),
                            outputDir = aarOutputJniOutputDir
                        )
                    }
                    ArtifactType.SOURCES_JAR -> {
                        output.get().asFile.createNewFile()
                        JarFlinger(output.get().asFile.toPath()).use { jarFlinger ->
                            input.files.forEach {
                                jarFlinger.addJar(
                                    it.toPath(),
                                    // Avoid merging non-code artifacts for now, as this would need
                                    // to fully emulate the plugin's logic.
                                    {
                                        it.endsWith(DOT_JAVA) ||
                                                it.endsWith(DOT_KT)
                                    },
                                    null
                                )
                            }
                        }
                    }
                    ArtifactType.UNFILTERED_PROGUARD_RULES -> {
                        val consumerProguardFiles = inputFiles
                            .flatMap(File::walkBottomUp)
                            .asSequence()
                            .filter(File::isFile)
                            .filterNot { it.length() == 0L }
                            .onEach {
                                if (it.name != FN_PROGUARD_TXT) {
                                    error("Expected a file named '$FN_PROGUARD_TXT' but found file entry named '${it.name}'.")
                                }
                                ConsumerRuleGlobalGuardian.validateConsumerRulesHasNoBannedGlobals(
                                    it,
                                    false,
                                    { error(it.errorMessage) }
                                )
                            }.toList()
                        if (consumerProguardFiles.none()) {
                            return
                        }
                        val outputFile = output.get().asFile
                        buildString {
                            append("# Merged by Fused Library.\n")
                            consumerProguardFiles.forEach {
                                append(it.readText())
                                append('\n')
                            }
                            outputFile.writeText(toString())
                        }
                    }
                    else -> {
                        val supportedArtifacts = mergeArtifactMap.map { it.first }
                        if (currentArtifactType !in supportedArtifacts) {
                            error("${currentArtifactType.type} is not a supported artifact type for " +
                                    "fused library artifact republishing.")
                        }
                    }
                }
            }
        }
    }

    class CreateActionFusedLibrary(
        private val creationConfig: FusedLibraryGlobalScope,
        private val androidArtifactType: ArtifactType,
        private val fusedArtifact: FusedArtifact
    ) : GlobalTaskCreationAction<FusedLibraryMergeArtifactTask>() {

        override val name: String
            get() = "mergingArtifact${androidArtifactType.name.usLocaleCapitalize()}"
        override val type: Class<FusedLibraryMergeArtifactTask>
            get() = FusedLibraryMergeArtifactTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<FusedLibraryMergeArtifactTask>) {
            super.handleProvider(taskProvider)

            when (fusedArtifact) {
                is FusedArtifact.Directory ->
                    creationConfig.artifacts.setInitialProvider(
                            taskProvider,
                            FusedLibraryMergeArtifactTask::outputDir
                    ).withName(fusedArtifact.artifactType.name().lowercase())
                            .on(fusedArtifact.artifactType as Artifact.Single<Directory>)
                is FusedArtifact.File ->
                    creationConfig.artifacts.setInitialProvider(
                            taskProvider,
                            FusedLibraryMergeArtifactTask::outputFile
                    ).withName(fusedArtifact.filename)
                            .on(fusedArtifact.artifactType as Artifact.Single<RegularFile>)
            }
        }

        override fun configure(task: FusedLibraryMergeArtifactTask) {
            super.configure(task)

            task.artifactFiles.setFrom(
                when (androidArtifactType) {
                    ArtifactType.SOURCES_JAR -> {
                        val fusedSources = task.project.configurations.getByName(
                            FusedLibraryConstants.FUSED_SOURCES_CONFIGURATION_NAME
                        )
                        fusedSources.incoming.artifactView { config ->
                            config.attributes {
                                it.attribute(
                                    AndroidArtifacts.ARTIFACT_TYPE,
                                    ArtifactType.SOURCES_JAR.type
                                )
                            }
                        }.files
                    }
                    else -> {
                        creationConfig.dependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            androidArtifactType
                        )
                    }
                }
            )

            task.artifactType.setDisallowChanges(androidArtifactType)

            creationConfig.aarMetadata.minAgpVersion?.let {
                task.aarMetadataInputs.minAgpVersion.setDisallowChanges(it)
            }
            creationConfig.aarMetadata.minCompileSdk?.let {
                task.aarMetadataInputs.minCompileSdk.setDisallowChanges(it)
            }
            creationConfig.aarMetadata.minCompileSdkExtension?.let {
                task.aarMetadataInputs.minCompileSdkExtension.setDisallowChanges(it)
            }
        }

    }

    abstract class AarMetadataInputs {

        @get:Input
        @get:Optional
        abstract val minCompileSdk: Property<Int>

        @get:Input
        @get:Optional
        abstract val minCompileSdkExtension: Property<Int>

        @get:Input
        @get:Optional
        abstract val minAgpVersion: Property<String>
    }

    companion object {

        private val mergeArtifactMap: List<Pair<ArtifactType, FusedArtifact>> =
                listOf(
                        ArtifactType.AIDL to FusedArtifact.Directory(MERGED_AIDL),
                        ArtifactType.RENDERSCRIPT to FusedArtifact.Directory(MERGED_RENDERSCRIPT_HEADERS),
                        ArtifactType.PREFAB_PACKAGE to FusedArtifact.Directory(MERGED_PREFAB_PACKAGE),
                        ArtifactType.PREFAB_PACKAGE_CONFIGURATION to FusedArtifact.Directory(MERGED_PREFAB_PACKAGE_CONFIGURATION),
                        ArtifactType.ASSETS to FusedArtifact.Directory(MERGED_ASSETS),
                        ArtifactType.JNI to FusedArtifact.Directory(MERGED_JNI),
                        ArtifactType.NAVIGATION_JSON to FusedArtifact.File(MERGED_NAVIGATION_JSON, FN_NAVIGATION_JSON),
                        ArtifactType.SOURCES_JAR to FusedArtifact.File(MERGED_SOURCES_JAR, "${DocsType.SOURCES}.jar"),
                        ArtifactType.AAR_METADATA to FusedArtifact.File(MERGED_AAR_METADATA, AAR_METADATA_FILE_NAME),
                        ArtifactType.UNFILTERED_PROGUARD_RULES to FusedArtifact.File(MERGED_CONSUMER_PROGUARD_RULES, FN_PROGUARD_TXT)
                )
        fun getCreationActions(creationConfig: FusedLibraryGlobalScope) :
                List<CreateActionFusedLibrary> {
            return mergeArtifactMap.map { CreateActionFusedLibrary(creationConfig, it.first, it.second) }
        }
    }
}

sealed class FusedArtifact(val artifactType: Artifact.Single<*>) {
    internal class Directory(artifactType: Artifact.Single<org.gradle.api.file.Directory>) :
        FusedArtifact(artifactType)

    internal class File(artifactType: Artifact.Single<RegularFile>, val filename: String) :
        FusedArtifact(artifactType)
}
