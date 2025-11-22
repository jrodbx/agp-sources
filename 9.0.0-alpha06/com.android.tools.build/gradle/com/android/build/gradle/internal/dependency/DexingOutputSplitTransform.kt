/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency

import com.android.SdkConstants
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Transform dexes & keep rules bundle from dexing artifact transform into dexes or keep rules
 * depending on the [DexingOutputSplitTransform.Parameters]
 */
@DisableCachingByDefault
abstract class DexingOutputSplitTransform : TransformAction<DexingOutputSplitTransform.Parameters> {

    enum class DexOutput {
        DEX,
        GLOBAL_SYNTHETICS,
    }

    interface Parameters: GenericTransformParameters {
        @get:Input
        val dexOutput: Property<DexOutput>
    }

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val primaryInput: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val inputDir = primaryInput.get().asFile
        when (parameters.dexOutput.get()) {
            DexOutput.DEX -> {
                outputs.dir(File(inputDir, computeDexDirName(inputDir)))
            }
            DexOutput.GLOBAL_SYNTHETICS -> {
                outputs.dir(File(inputDir, computeGlobalSyntheticsDirName(inputDir)))
            }
        }
    }
}

fun registerDexingOutputSplitTransform(dependencyHandler: DependencyHandler) {
    // In release builds we can shrink the core java libraries as part of the D8 pipeline,
    // even if R8 is not used.
    registerTransformWithOutputType(dependencyHandler, DexingOutputSplitTransform.DexOutput.DEX)
    registerTransformWithOutputType(dependencyHandler, DexingOutputSplitTransform.DexOutput.GLOBAL_SYNTHETICS)
}

private fun registerTransformWithOutputType(
    dependencyHandler: DependencyHandler,
    dexOutput: DexingOutputSplitTransform.DexOutput
) {
    dependencyHandler.registerTransform(DexingOutputSplitTransform::class.java) { spec ->
        spec.parameters { parameters ->
            parameters.dexOutput.set(dexOutput)
        }
        spec.from.attribute(ARTIFACT_TYPE_ATTRIBUTE, AndroidArtifacts.ArtifactType.D8_OUTPUTS.type)
        when (dexOutput) {
            DexingOutputSplitTransform.DexOutput.DEX -> {
                spec.to.attribute(ARTIFACT_TYPE_ATTRIBUTE, AndroidArtifacts.ArtifactType.DEX.type)
            }
            DexingOutputSplitTransform.DexOutput.GLOBAL_SYNTHETICS -> {
                spec.to.attribute(ARTIFACT_TYPE_ATTRIBUTE, AndroidArtifacts.ArtifactType.GLOBAL_SYNTHETICS.type)
            }
        }
    }
}

private const val DEX_DIR_NAME = SdkConstants.FD_DEX
private const val GLOBAL_SYNTHETICS_DIR_NAME = "global-synthetics"

// Gradle identifies ResolvedArtifactResult by ComponentIdentifier and the file name of output file
// or output directory. When test fixtures feature is enabled, there could be two
// ResolvedArtifactResult with same ComponentIdentifier, so we need to make the file name different
// (e.g. prefix dexOutput name) to avoid collision of identification. We should consider moving away
// from this approach when https://github.com/gradle/gradle/issues/18458 is addressed.
fun computeDexDirName(dexOutput: File): String = dexOutput.name + "_" + DEX_DIR_NAME
fun computeGlobalSyntheticsDirName(dexOutput: File): String = dexOutput.name + "_" + GLOBAL_SYNTHETICS_DIR_NAME
