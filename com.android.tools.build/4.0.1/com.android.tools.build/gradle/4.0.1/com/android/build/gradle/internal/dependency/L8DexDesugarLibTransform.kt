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
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.utils.DESUGAR_LIB_DEX
import com.android.build.gradle.internal.utils.getDesugarLibConfig
import com.android.builder.dexing.KeepRulesConfig
import com.android.builder.dexing.runL8
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.artifacts.ArtifactAttributes
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input

abstract class L8DexDesugarLibTransform : TransformAction<L8DexDesugarLibTransform.Parameters> {
    interface Parameters: GenericTransformParameters {
        @get:Input
        val libConfiguration: Property<String>
        @get:Input
        val minSdkVersion: Property<Int>
        @get:CompileClasspath
        val bootClasspath: ConfigurableFileCollection
    }

    @get:Classpath
    @get:InputArtifact
    abstract val primaryInput: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val inputFile = primaryInput.get().asFile
        val outputDir = outputs.dir(inputFile.nameWithoutExtension)

        runL8(
            listOf(inputFile.toPath()),
            outputDir.toPath(),
            parameters.libConfiguration.get(),
            parameters.bootClasspath.map { it.toPath() },
            parameters.minSdkVersion.get(),
            KeepRulesConfig(null, null)
            )
    }
}

val ATTR_L8_MIN_SDK: Attribute<String> = Attribute.of("l8-min-sdk", String::class.java)

data class DesugarLibConfiguration(
    private val libConfiguration: Provider<String>,
    private val bootClasspath: FileCollection,
    private val minSdkVersion: Int) {

    /**
     * Registers the transform which converts desugar lib jar to its dex file
     */
    fun registerTransform(
        dependencyHandler: DependencyHandler
    ) {
        dependencyHandler.registerTransform(L8DexDesugarLibTransform::class.java) { spec ->
            spec.parameters { parameters ->
                parameters.libConfiguration.set(libConfiguration)
                parameters.bootClasspath.from(bootClasspath)
                parameters.minSdkVersion.set(minSdkVersion)
            }
            spec.from.attribute(ArtifactAttributes.ARTIFACT_FORMAT, ArtifactTypeDefinition.JAR_TYPE)
            spec.to.attribute(ArtifactAttributes.ARTIFACT_FORMAT, DESUGAR_LIB_DEX)
            spec.from.attribute(ATTR_L8_MIN_SDK, minSdkVersion.toString())
            spec.to.attribute(ATTR_L8_MIN_SDK, minSdkVersion.toString())
        }
    }
}

fun getDesugarLibConfigurations(scopes: Collection<VariantScope>): Set<DesugarLibConfiguration> {
    return scopes
        .filter { it.isCoreLibraryDesugaringEnabled }
        .map { getDesugarLibConfiguration(it) }
        .toSet()
}

private fun getDesugarLibConfiguration(scope: VariantScope): DesugarLibConfiguration {
    val libConfiguration = getDesugarLibConfig(scope.globalScope.project)
    val bootClasspath = scope.bootClasspath.filter { it.name == SdkConstants.FN_FRAMEWORK_LIBRARY }
    val minSdkVersion = scope.variantDslInfo.minSdkVersionWithTargetDeviceApi.featureLevel

    return DesugarLibConfiguration(libConfiguration, bootClasspath, minSdkVersion)
}