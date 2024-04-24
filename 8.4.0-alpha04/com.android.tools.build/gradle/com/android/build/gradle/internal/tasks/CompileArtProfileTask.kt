/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationInstallation
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.dsl.ModulePropertyKey
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.MergeFileTask.Companion.mergeFiles
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.tasks.PackageAndroidArtifact
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.packaging.DexFileComparator
import com.android.builder.packaging.DexFileNameSupplier
import com.android.tools.profgen.ArtProfile
import com.android.tools.profgen.ArtProfileSerializer
import com.android.tools.profgen.DexFile
import com.android.tools.profgen.Diagnostics
import com.android.tools.profgen.HumanReadableProfile
import com.android.tools.profgen.ObfuscationMap
import com.android.tools.profgen.buildArtProfileWithDexMetadata
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import shadow.bundletool.com.android.utils.PathUtils
import java.io.File

/**
 * Task that transforms a human readable art profile into a binary form version that can be shipped
 * inside an APK or a Bundle.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ART_PROFILE, secondaryTaskCategories = [TaskCategory.COMPILATION])
abstract class CompileArtProfileTask: NonIncrementalTask() {

    @get: [InputFiles Optional PathSensitive(PathSensitivity.NAME_ONLY)]
    abstract val mergedArtProfile: RegularFileProperty

    @get: [InputFiles Optional PathSensitive(PathSensitivity.NAME_ONLY)]
    abstract val l8ArtProfile: RegularFileProperty

    @get: [InputFiles PathSensitive(PathSensitivity.RELATIVE)]
    abstract val dexFolders: ConfigurableFileCollection

    @get: [InputFiles Optional PathSensitive(PathSensitivity.RELATIVE)]
    abstract val featuresDexFolders: ConfigurableFileCollection

    @get: Input
    abstract val useMappingFile: Property<Boolean>

    @get: [InputFiles Optional PathSensitive(PathSensitivity.NAME_ONLY)]
    abstract val obfuscationMappingFile: RegularFileProperty

    @get: OutputFile
    abstract val binaryArtProfile: RegularFileProperty

    @get: OutputFile
    abstract val binaryArtProfileMetadata: RegularFileProperty

    @get: OutputDirectory
    @get: Optional
    abstract val dexMetadataDirectory: DirectoryProperty

    @get: OutputFile
    abstract val combinedArtProfile: RegularFileProperty

    abstract class CompileArtProfileWorkAction:
            ProfileAwareWorkAction<CompileArtProfileWorkAction.Parameters>() {

        abstract class Parameters : ProfileAwareWorkAction.Parameters() {
            abstract val mergedArtProfile: RegularFileProperty
            abstract val dexFolders: ConfigurableFileCollection
            abstract val obfuscationMappingFile: RegularFileProperty
            abstract val binaryArtProfileOutputFile: RegularFileProperty
            abstract val binaryArtProfileMetadataOutputFile: RegularFileProperty
            abstract val dexMetadataDirectory: DirectoryProperty
            abstract val l8ArtProfile: RegularFileProperty
            abstract val combinedArtProfile: RegularFileProperty
        }

        override fun run() {
            val filesToCompile = mutableListOf(parameters.mergedArtProfile.get().asFile)
            if (parameters.l8ArtProfile.isPresent) {
                filesToCompile.add(parameters.l8ArtProfile.get().asFile)
            }
            mergeFiles(filesToCompile, parameters.combinedArtProfile.get().asFile)

            val diagnostics = Diagnostics {
                    error -> throw RuntimeException("Error parsing baseline-prof.txt : $error")
            }
            val humanReadableProfile = HumanReadableProfile(
                parameters.combinedArtProfile.get().asFile,
                diagnostics
            ) ?: throw RuntimeException(
                "Merged ${SdkConstants.FN_ART_PROFILE} cannot be parsed successfully."
            )
            val obfuscationMap = if (parameters.obfuscationMappingFile.isPresent) {
                ObfuscationMap(parameters.obfuscationMappingFile.get().asFile)
            } else {
                ObfuscationMap.Empty
            }
            val supplier = DexFileNameSupplier()
            // need to rename dex files with sequential numbers the same way [DexIncrementalRenameManager] does
            val dexFiles =
                parameters.dexFolders.asFileTree.files.sortedWith(DexFileComparator()).map {
                    DexFile(it.inputStream(), supplier.get())
                }

            val artProfile = if (parameters.dexMetadataDirectory.isPresent) {
                val artProfileWithDexMetadata = buildArtProfileWithDexMetadata(
                    humanReadableProfile,
                    obfuscationMap,
                    dexFiles,
                    outputDir = parameters.dexMetadataDirectory.get().asFile
                )

                val dexMetadataMap =
                    artProfileWithDexMetadata.dexMetadata.entries.joinToString("\n") {
                        it.key.toString() + "=" +
                            PathUtils.toSystemIndependentPath(
                                parameters.dexMetadataDirectory.get().asFile.toPath()
                                    .relativize(it.value.toPath())
                            )
                    }
                FileUtils.writeToFile(
                    File(parameters.dexMetadataDirectory.get().asFile, SdkConstants.FN_DEX_METADATA_PROP),
                    dexMetadataMap
                )
                artProfileWithDexMetadata.profile
            } else {
                ArtProfile(humanReadableProfile, obfuscationMap, dexFiles)
            }

            // the P compiler is always used, the server side will transcode if necessary.
            parameters.binaryArtProfileOutputFile.get().asFile.outputStream().use {
                artProfile.save(it, ArtProfileSerializer.V0_1_0_P)
            }

            // create the metadata.
            parameters.binaryArtProfileMetadataOutputFile.get().asFile.outputStream().use {
                artProfile.save(it, ArtProfileSerializer.METADATA_0_0_2)
            }
        }
    }

    override fun doTaskAction() {
        // if we do not have a merged human readable profile, just return.
        if (!mergedArtProfile.isPresent || !mergedArtProfile.get().asFile.exists()) return

        workerExecutor.noIsolation().submit(CompileArtProfileWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.mergedArtProfile.set(mergedArtProfile)
            it.dexFolders.from(dexFolders)
            if (useMappingFile.get()) {
                it.obfuscationMappingFile.set(obfuscationMappingFile)
            }
            it.binaryArtProfileOutputFile.set(binaryArtProfile)
            it.binaryArtProfileMetadataOutputFile.set(binaryArtProfileMetadata)
            it.dexMetadataDirectory.set(dexMetadataDirectory)
            it.l8ArtProfile.set(l8ArtProfile)
            it.combinedArtProfile.set(combinedArtProfile)
        }
    }

    class CreationAction(
            creationConfig: ApkCreationConfig
    ) : VariantTaskCreationAction<CompileArtProfileTask, ApkCreationConfig>(creationConfig) {

        override val name: String
            get() = creationConfig.computeTaskName("compile", "ArtProfile")
        override val type: Class<CompileArtProfileTask>
            get() = CompileArtProfileTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<CompileArtProfileTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                CompileArtProfileTask::binaryArtProfile
            ).on(InternalArtifactType.BINARY_ART_PROFILE)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                CompileArtProfileTask::binaryArtProfileMetadata
            ).on(InternalArtifactType.BINARY_ART_PROFILE_METADATA)

            // Only include the dex metadata (.dm) files for release builds since these are used
            // along with the APKs for installation on devices
            // Additionally, do not generate the .dm files if opt-out is specified in the DSL
            if (!creationConfig.debuggable &&
                creationConfig.global.installationOptions is ApplicationInstallation &&
                (creationConfig.global.installationOptions as ApplicationInstallation)
                    .enableBaselineProfile) {
                creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    CompileArtProfileTask::dexMetadataDirectory
                ).on(InternalArtifactType.DEX_METADATA_DIRECTORY)
            }

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                CompileArtProfileTask::combinedArtProfile
            ).on(InternalArtifactType.COMBINED_ART_PROFILE)
        }

        override fun configure(task: CompileArtProfileTask) {
            super.configure(task)
            task.mergedArtProfile.setDisallowChanges(
                creationConfig.artifacts.get(
                    if (creationConfig.optimizationCreationConfig.minifiedEnabled) {
                        InternalArtifactType.R8_ART_PROFILE
                    } else {
                        InternalArtifactType.MERGED_ART_PROFILE
                    }
                )
            )
            if (creationConfig.dexing.shouldPackageDesugarLibDex) {
                task.l8ArtProfile.setDisallowChanges(
                    creationConfig.artifacts.get(InternalArtifactType.L8_ART_PROFILE)
                )
            }
            task.dexFolders.fromDisallowChanges(
                    PackageAndroidArtifact.CreationAction.getDexFolders(creationConfig)
            )

            PackageAndroidArtifact.CreationAction.getFeatureDexFolder(
                    creationConfig,
                    task.project.path
            )?.let {
                task.featuresDexFolders.from(it)
            }
            task.featuresDexFolders.disallowChanges()

            configureObfuscationMappingFile(task)
        }

        @VisibleForTesting
        internal fun configureObfuscationMappingFile(task: CompileArtProfileTask) {
            if (creationConfig is VariantCreationConfig) {
                task.useMappingFile.setDisallowChanges(
                    creationConfig.experimentalProperties.map {
                        !ModulePropertyKey.BooleanWithDefault.ART_PROFILE_R8_REWRITING.getValue(it)
                    })
            } else {
                task.useMappingFile.setDisallowChanges(true)
            }
            task.obfuscationMappingFile.setDisallowChanges(
                creationConfig.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE)
            )
        }
    }
}
