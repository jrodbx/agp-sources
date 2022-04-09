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
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.tasks.PackageAndroidArtifact
import com.android.tools.profgen.ArtProfile
import com.android.tools.profgen.ArtProfileSerializer
import com.android.tools.profgen.DexFile
import com.android.tools.profgen.Diagnostics
import com.android.tools.profgen.HumanReadableProfile
import com.android.tools.profgen.ObfuscationMap
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.lang.RuntimeException

/**
 * Task that transforms a human readable art profile into a binary form version that can be shipped
 * inside an APK or a Bundle.
 */
@CacheableTask
abstract class CompileArtProfileTask: NonIncrementalTask() {

    @get: [InputFiles Optional PathSensitive(PathSensitivity.RELATIVE)]
    abstract val mergedArtProfile: RegularFileProperty

    @get: [InputFiles PathSensitive(PathSensitivity.RELATIVE)]
    abstract val dexFolders: ConfigurableFileCollection

    @get: [InputFiles Optional PathSensitive(PathSensitivity.RELATIVE)]
    abstract val featuresDexFolders: ConfigurableFileCollection

    @get: [InputFiles Optional PathSensitive(PathSensitivity.RELATIVE)]
    abstract val obfuscationMappingFile: RegularFileProperty

    @get: OutputFile
    abstract val binaryArtProfile: RegularFileProperty

    @get: OutputFile
    abstract val binaryArtProfileMetadata: RegularFileProperty

    abstract class CompileArtProfileWorkAction:
            ProfileAwareWorkAction<CompileArtProfileWorkAction.Parameters>() {

        abstract class Parameters : ProfileAwareWorkAction.Parameters() {
            abstract val mergedArtProfile: RegularFileProperty
            abstract val dexFolders: ConfigurableFileCollection
            abstract val obfuscationMappingFile: RegularFileProperty
            abstract val binaryArtProfileOutputFile: RegularFileProperty
            abstract val binaryArtProfileMetadataOutputFile: RegularFileProperty
        }

        override fun run() {
            val diagnostics = Diagnostics {
                    error -> throw RuntimeException("Error parsing baseline-prof.txt : $error")
            }
            val humanReadableProfile = HumanReadableProfile(
                parameters.mergedArtProfile.get().asFile,
                diagnostics
            ) ?: throw RuntimeException(
                "Merged ${SdkConstants.FN_ART_PROFILE} cannot be parsed successfully."
            )

            val artProfile = ArtProfile(
                    humanReadableProfile,
                    if (parameters.obfuscationMappingFile.isPresent) {
                        ObfuscationMap(parameters.obfuscationMappingFile.get().asFile)
                    } else {
                        ObfuscationMap.Empty
                    },
                    parameters.dexFolders.asFileTree.files.map {
                        DexFile(it)
                    }
            )
            // the P compiler is always used, the server side will transcode if necessary.
            parameters.binaryArtProfileOutputFile.get().asFile.outputStream().use {
                artProfile.save(it, ArtProfileSerializer.V0_1_0_P)
            }

            // create the metadata for N and above.
            parameters.binaryArtProfileMetadataOutputFile.get().asFile.outputStream().use {
                artProfile.save(it, ArtProfileSerializer.METADATA_FOR_N)
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
            it.obfuscationMappingFile.set(obfuscationMappingFile)
            it.binaryArtProfileOutputFile.set(binaryArtProfile)
            it.binaryArtProfileMetadataOutputFile.set(binaryArtProfileMetadata)
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
            ).withName(SdkConstants.FN_BINARY_ART_PROFILE
            ).on(InternalArtifactType.BINARY_ART_PROFILE)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                CompileArtProfileTask::binaryArtProfileMetadata
            ).withName(SdkConstants.FN_BINARY_ART_PROFILE_METADATA
            ).on(InternalArtifactType.BINARY_ART_PROFILE_METADATA)
        }

        override fun configure(task: CompileArtProfileTask) {
            super.configure(task)
            task.mergedArtProfile.setDisallowChanges(
                    creationConfig.artifacts.get(
                            InternalArtifactType.MERGED_ART_PROFILE
                    )
            )
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

            task.obfuscationMappingFile.setDisallowChanges(
                    creationConfig.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE)
            )
        }
    }
}
