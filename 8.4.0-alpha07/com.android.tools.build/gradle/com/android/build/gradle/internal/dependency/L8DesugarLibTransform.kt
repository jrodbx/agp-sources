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

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.utils.DESUGARED_DESUGAR_LIB
import com.android.build.gradle.internal.utils.getDesugarLibConfig
import com.android.builder.dexing.KeepRulesConfig
import com.android.builder.dexing.runL8
import com.android.tools.r8.OutputMode
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.InputArtifactDependencies
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.JAR_TYPE
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input

/**
 * Transform desugar lib jar into a desugared version using L8. This desugared desugar lib jar is in
 * classfile format, which is used by trace reference tool to generate keep rules for shrinking the
 * desugar lib jar into dex format.
 */
@CacheableTransform
abstract class L8DesugarLibTransform : TransformAction<L8DesugarLibTransform.Parameters> {
    interface Parameters: GenericTransformParameters {
        @get:Input
        val libConfiguration: Property<String>
        @get:Input
        val minSdkVersion: Property<Int>
        @get:Classpath
        val fullBootClasspath: ConfigurableFileCollection
    }

    @get:Classpath
    @get:InputArtifact
    abstract val primaryInput: Provider<FileSystemLocation>

    @get:Classpath
    @get:InputArtifactDependencies
    abstract val dependencies: FileCollection

    override fun transform(outputs: TransformOutputs) {
        logger.verbose("Running ${L8DesugarLibTransform::class.simpleName}" +
                " with minSdkVersion = ${parameters.minSdkVersion.get()}" +
                " for input file '${primaryInput.get().asFile.path}'"
        )

        val inputFiles = mutableListOf(primaryInput.get().asFile.toPath())
        inputFiles.addAll(dependencies.files.map { it.toPath() })
        val outputDir = outputs.dir(primaryInput.get().asFile.nameWithoutExtension)

        runL8(
            inputFiles,
            outputDir.toPath(),
            parameters.libConfiguration.get(),
            parameters.fullBootClasspath.files.map { it.toPath() },
            parameters.minSdkVersion.get(),
            KeepRulesConfig(emptyList(), emptyList()),
            false,
            OutputMode.ClassFile
        )
    }
}

object L8DesugarLibTransformRegistration {

    /** Parameters that are shared across all [ComponentCreationConfig]s. */
    private class ComponentAgnosticParameters(
        taskCreationServices: TaskCreationServices,
        globalTaskCreationConfig: GlobalTaskCreationConfig
    ) {
        val libConfiguration: Provider<String> = getDesugarLibConfig(taskCreationServices)
        val fullBootClasspath: FileCollection = globalTaskCreationConfig.fullBootClasspath
    }

    /**
     * Parameters that are specific to a given [ComponentCreationConfig].
     *
     * See [DexingRegistration.ComponentSpecificParameters] for more info on this pattern.
     */
    private data class ComponentSpecificParameters(
        val minSdkVersion: Int
    ) {
        constructor(creationConfig: ApkCreationConfig) : this(
            minSdkVersion = creationConfig.dexing.minSdkVersionForDexing,
        )

        fun getAttributes() = AndroidAttributes(
            Attribute.of("${L8DesugarLibTransform::class.simpleName}-attributes", String::class.java) to this.toString()
        )
    }

    /**
     * Registers [L8DesugarLibTransform] with attribute values computed from the given
     * [creationConfig].
     *
     * If this transform with this specific set of attribute values has been registered in the
     * current project, this method will not register it again.
     */
    fun registerTransformIfAbsent(creationConfig: ApkCreationConfig) {
        val parameters = ComponentSpecificParameters(creationConfig)

        val transformRegistered = "_agp_internal_${L8DesugarLibTransform::class.simpleName}_attributes_${parameters.getAttributes()}_registered"
        if (creationConfig.services.extraProperties.has(transformRegistered)) {
            return
        }
        creationConfig.services.extraProperties[transformRegistered] = true

        val sharedParameters = ComponentAgnosticParameters(creationConfig.services, creationConfig.global)

        registerTransform(creationConfig.services.dependencies, sharedParameters, parameters)
    }

    private fun registerTransform(
        dependencyHandler: DependencyHandler,
        sharedParameters: ComponentAgnosticParameters,
        parameters: ComponentSpecificParameters
    ) {
        dependencyHandler.registerTransform(L8DesugarLibTransform::class.java) { spec ->
            spec.parameters.apply {
                libConfiguration.set(sharedParameters.libConfiguration)
                fullBootClasspath.from(sharedParameters.fullBootClasspath)
                minSdkVersion.set(parameters.minSdkVersion)
            }
            parameters.getAttributes().apply {
                addAttributesToContainer(spec.from)
                addAttributesToContainer(spec.to)
            }
            spec.from.attribute(ARTIFACT_TYPE_ATTRIBUTE, JAR_TYPE)
            spec.to.attribute(ARTIFACT_TYPE_ATTRIBUTE, DESUGARED_DESUGAR_LIB)
        }
    }

    /** Returns [AndroidAttributes] for the output artifact of [L8DesugarLibTransform]. */
    fun getOutputArtifactAttributes(creationConfig: ApkCreationConfig): AndroidAttributes {
        return ComponentSpecificParameters(creationConfig).getAttributes() +
                AndroidAttributes(ARTIFACT_TYPE_ATTRIBUTE to DESUGARED_DESUGAR_LIB)
    }

}

private val logger = LoggerWrapper.getLogger(L8DesugarLibTransform::class.java)
