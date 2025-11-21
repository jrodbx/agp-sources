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
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.LINKED_RESOURCES_PROTO_FORMAT
import com.android.build.gradle.internal.scope.InternalArtifactType.SHRUNK_RESOURCES_PROTO_FORMAT
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.builder.dexing.ResourceShrinkingConfig
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
     * NOTE: The other properties in this class will be set if and only if [enabled] == true.
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

    @get:Input
    @get:Optional // Set iff enabled == true
    abstract val usePreciseShrinking: Property<Boolean>

    @get:OutputFile
    @get:Optional // Set iff enabled == true && a log file is provided
    abstract val logFile: RegularFileProperty

    @get:OutputDirectory
    @get:Optional // Set iff enabled == true
    abstract val shrunkResourcesOutputDir: DirectoryProperty

    // This is to compute multi-APK file names
    @get:Nested
    @get:Optional // Set iff enabled == true
    abstract val multiOutputHandler: Property<MultiOutputHandler>

    fun toConfig(): ResourceShrinkingConfig? {
        return if (enabled.get()) {
            val inputArtifacts = loadInputBuiltArtifacts().elements
            ResourceShrinkingConfig(
                linkedResourcesInputFiles = inputArtifacts.map { File(it.outputFile) },
                mergedNotCompiledResourcesInputDir = mergedNotCompiledResourcesInputDir.get().asFile,
                usePreciseShrinking = usePreciseShrinking.get(),
                logFile = logFile.asFile.orNull,
                shrunkResourcesOutputFiles = inputArtifacts.map { File(getOutputBuiltArtifact(it).outputFile) }
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

fun R8ResourceShrinkingParameters.initialize(
    creationConfig: ConsumableCreationConfig,
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
    usePreciseShrinking.setDisallowChanges(
        creationConfig.services.projectOptions.get(BooleanOption.ENABLE_NEW_RESOURCE_SHRINKER_PRECISE)
    )
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
