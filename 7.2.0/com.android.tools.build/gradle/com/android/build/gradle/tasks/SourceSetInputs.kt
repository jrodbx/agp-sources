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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.utils.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import java.io.File

/**
 * Provides necessary inputs for retrieving resource source set directories.
 *
 * Annotate with [org.gradle.api.tasks.Nested] when using as property.
 */
abstract class SourceSetInputs {

    @get:Internal
    abstract val extraGeneratedResDir: ConfigurableFileCollection

    @get:Internal
    abstract val generatedResDir: DirectoryProperty

    @get:Internal
    abstract val renderscriptResOutputDir: DirectoryProperty

    @get:Internal
    abstract val mergeResourcesOutputDir: DirectoryProperty

    @get:Internal
    abstract val incrementalMergedDir: DirectoryProperty

    @get:Internal
    abstract val localResources: MapProperty<String, FileCollection>

    @get:Internal
    abstract val resourceSourceSets: ConfigurableFileCollection

    @get:Internal
    abstract val librarySourceSets : ConfigurableFileCollection

    fun initialise(
        creationConfig: ComponentCreationConfig,
        includeDependencies: Boolean = true
    ) {
        val androidResources = creationConfig.variantData.androidResources
        localResources.setDisallowChanges(androidResources)
        resourceSourceSets.setFrom(androidResources.values)
        generatedResDir.setDisallowChanges(
            creationConfig.artifacts.get(InternalArtifactType.GENERATED_RES))
        renderscriptResOutputDir.setDisallowChanges(
            creationConfig.artifacts.get(InternalArtifactType.RENDERSCRIPT_GENERATED_RES))
        extraGeneratedResDir.setFrom(
            creationConfig.variantData.extraGeneratedResFolders)
        if (includeDependencies) {
            librarySourceSets.setFrom(
                creationConfig.variantDependencies.getArtifactCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.ANDROID_RES
                ).artifactFiles
            )
        }
        mergeResourcesOutputDir.setDisallowChanges(
            creationConfig.artifacts.get(InternalArtifactType.MERGED_RES))

        incrementalMergedDir.setDisallowChanges(
            creationConfig.artifacts.get(InternalArtifactType.MERGED_RES_INCREMENTAL_FOLDER)
        )
    }

    fun listConfigurationSourceSets(additionalSourceSets: List<String>): List<File> {
        val uncreatedSourceSets = listOfNotNull(
            getPathIfPresentOrNull(incrementalMergedDir, listOf(SdkConstants.FD_MERGED_DOT_DIR)),
            getPathIfPresentOrNull(incrementalMergedDir, listOf(SdkConstants.FD_STRIPPED_DOT_DIR))
        )

        return resourceSourceSets.asSequence()
            .plus(librarySourceSets)
            .plus(extraGeneratedResDir.files)
            .plus(uncreatedSourceSets.map(::File))
            .plus(additionalSourceSets.map(::File)).toList()
    }

    private fun getPathIfPresentOrNull(property: Provider<Directory>, paths: List<String>) : String? {
        return if (property.isPresent && property.orNull != null) {
            FileUtils.join(listOf(property.get().asFile.absolutePath) + paths)
        } else {
            null
        }
    }
}
