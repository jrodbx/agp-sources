/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.android.build.api.variant.MultiOutputHandler
import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.LINKED_RESOURCES_PROTO_FORMAT
import com.android.build.gradle.internal.scope.InternalArtifactType.SHRUNK_RESOURCES_PROTO_FORMAT
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.builder.dexing.ResourceShrinkingConfig
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File

/** Parameters required for running resource shrinking. */
abstract class R8ResourceShrinkingParameters {

    /**
     * Indicates whether resource shrinking will be performed.
     *
     * NOTE: The other properties in this class will be set only if [enabled] == true.
     */
    @get:Input
    abstract val enabled: Property<Boolean>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional // Set iff enabled == true
    abstract val linkedResourcesInputDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional // Set iff enabled == true
    abstract val mergedNotCompiledResourcesInputDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional // Set iff enabled == true
    abstract val mergedNotCompiledNavigationResourcesInputDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional // Set iff enabled == true and the application has dynamic features
    abstract val featureLinkedResourcesInputFiles: ConfigurableFileCollection

    @get:Input
    @get:Optional // Set iff enabled == true
    abstract val usePreciseShrinking: Property<Boolean>

    @get:Input
    @get:Optional // Set iff enabled == true
    abstract val optimizedShrinking: Property<Boolean>

    @get:OutputFile
    @get:Optional // Set iff enabled == true && a log file is provided
    abstract val logFile: RegularFileProperty

    @get:OutputDirectory
    @get:Optional // Set iff enabled == true
    abstract val shrunkResourcesOutputDir: DirectoryProperty

    @get:OutputDirectory
    @get:Optional // Set iff enabled == true and the application has dynamic features
    abstract val featureShrunkResourcesOutputDir: DirectoryProperty

    // This is to compute multi-APK file names
    @get:Nested
    @get:Optional // Set iff enabled == true
    abstract val multiOutputHandler: Property<MultiOutputHandler>

    fun toConfig(): ResourceShrinkingConfig? {
        return if (enabled.get()) {
            val inputArtifacts = loadInputBuiltArtifacts().elements
            ResourceShrinkingConfig(
                linkedResourcesInputFiles = inputArtifacts.map { File(it.outputFile) },
                mergedNotCompiledResourcesInputDirs = listOf(
                    mergedNotCompiledResourcesInputDir.get().asFile,
                    mergedNotCompiledNavigationResourcesInputDir.get().asFile
                ),
                featureLinkedResourcesInputFiles = featureLinkedResourcesInputFiles.files.toList(),
                usePreciseShrinking = usePreciseShrinking.get(),
                optimizedShrinking = optimizedShrinking.get(),
                logFile = logFile.asFile.orNull,
                shrunkResourcesOutputFiles = inputArtifacts.map { File(getOutputBuiltArtifact(it).outputFile) },
                featureShrunkResourcesOutputDir = featureShrunkResourcesOutputDir.asFile.orNull
            )
        } else null
    }

    fun loadInputBuiltArtifacts(): BuiltArtifactsImpl {
        return BuiltArtifactsLoaderImpl().load(linkedResourcesInputDir.get())!!
    }

    fun saveOutputBuiltArtifactsMetadata() {
        val inputArtifacts = loadInputBuiltArtifacts()
        val outputArtifacts = inputArtifacts.copy(
            artifactType = SHRUNK_RESOURCES_PROTO_FORMAT,
            elements = inputArtifacts.elements.map { getOutputBuiltArtifact(it) }
        )
        outputArtifacts.save(shrunkResourcesOutputDir.get())
    }

    private fun getOutputBuiltArtifact(inputBuiltArtifact: BuiltArtifactImpl): BuiltArtifactImpl {
        val outputFileName = multiOutputHandler.get().getOutputNameForSplit(
            prefix = SHRUNK_RESOURCES_PROTO_FORMAT.name().lowercase().replace("_", "-"),
            suffix = "",
            outputType = inputBuiltArtifact.outputType,
            filters = inputBuiltArtifact.filters
        ) + SdkConstants.DOT_RES

        return inputBuiltArtifact.newOutput(shrunkResourcesOutputDir.get().asFile.resolve(outputFileName).toPath())
    }

}

/** Returns true if resource shrinking is enabled. */
fun ApplicationCreationConfig.runResourceShrinking(): Boolean {
    return androidResourcesCreationConfig?.useResourceShrinker == true
}

/**
 * Returns true if resource shrinking is enabled AND it will be performed by [R8Task] instead of
 * a separate task.
 */
fun ApplicationCreationConfig.runResourceShrinkingWithR8(): Boolean {
    return runResourceShrinking()
            && services.projectOptions[BooleanOption.R8_INTEGRATED_RESOURCE_SHRINKING]
}

/**
 * Returns true if R8 will run optimized shrinking for both code and resources. That is:
 *   - [runResourceShrinkingWithR8] == true, and
 *   - [BooleanOption.R8_OPTIMIZED_RESOURCE_SHRINKING] == true, and
 *   - the feature additionally requires that [BooleanOption.USE_NON_FINAL_RES_IDS] == true
 */
fun ApplicationCreationConfig.runOptimizedShrinkingWithR8(): Boolean {
    return runResourceShrinkingWithR8()
            && services.projectOptions[BooleanOption.R8_OPTIMIZED_RESOURCE_SHRINKING]
            && services.projectOptions[BooleanOption.USE_NON_FINAL_RES_IDS]
}

fun R8ResourceShrinkingParameters.initialize(
    creationConfig: ApplicationCreationConfig,
    mappingFile: RegularFileProperty
) {
    enabled.setDisallowChanges(true)
    creationConfig.artifacts.setTaskInputToFinalProduct(
        LINKED_RESOURCES_PROTO_FORMAT,
        linkedResourcesInputDir
    )
    creationConfig.artifacts.setTaskInputToFinalProduct(
        InternalArtifactType.MERGED_NOT_COMPILED_RES,
        mergedNotCompiledResourcesInputDir
    )
    creationConfig.artifacts.setTaskInputToFinalProduct(
        InternalArtifactType.UPDATED_NAVIGATION_XML,
        mergedNotCompiledNavigationResourcesInputDir
    )
    if (creationConfig.shrinkingWithDynamicFeatures) {
        featureLinkedResourcesInputFiles.fromDisallowChanges(
            creationConfig.variantDependencies.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                AndroidArtifacts.ArtifactScope.PROJECT,
                AndroidArtifacts.ArtifactType.REVERSE_METADATA_LINKED_RESOURCES_PROTO_FORMAT
            )
        )
    }
    usePreciseShrinking.setDisallowChanges(
        creationConfig.services.projectOptions.get(BooleanOption.ENABLE_NEW_RESOURCE_SHRINKER_PRECISE)
    )
    optimizedShrinking.setDisallowChanges(creationConfig.runOptimizedShrinkingWithR8())
    logFile.setDisallowChanges(
        mappingFile.flatMap {
            creationConfig.services.fileProvider(
                creationConfig.services.provider {
                    it.asFile.resolveSibling("resources.txt")
                }
            )
        }
    )
    multiOutputHandler.setDisallowChanges(MultiOutputHandler.create(creationConfig))
}
