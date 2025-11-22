/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.instrumentation.AsmInstrumentationManager
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.services.ClassesHierarchyBuildService
import com.android.build.gradle.internal.services.getBuildService
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.InputArtifactDependencies
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested

@CacheableTransform
abstract class AsmClassesTransform : TransformAction<AsmClassesTransform.Parameters> {
    companion object {
        val ATTR_ASM_TRANSFORMED_VARIANT: Attribute<String> =
            Attribute.of("asm-transformed-variant", String::class.java)

        fun getAttributesForConfig(creationConfig: ComponentCreationConfig)
                : AndroidAttributes {
            return AndroidAttributes(
                mapOf(ATTR_ASM_TRANSFORMED_VARIANT to creationConfig.name)
            )
        }

        fun registerAsmTransformForComponent(
            projectName: String,
            dependencyHandler: DependencyHandler,
            creationConfig: ComponentCreationConfig
        ) {
            val instrumentationCreationConfig = creationConfig.instrumentationCreationConfig
                ?: return
            if (instrumentationCreationConfig.dependenciesClassesAreInstrumented) {
                dependencyHandler.registerTransform(AsmClassesTransform::class.java) { spec ->
                    spec.parameters { parameters ->
                        parameters.projectName.set(projectName)
                        parameters.asmApiVersion.set(creationConfig.global.asmApiVersion)
                        parameters.framesComputationMode.set(
                            instrumentationCreationConfig.asmFramesComputationMode
                        )
                        parameters.excludes.set(
                            instrumentationCreationConfig.instrumentation.excludes
                        )
                        parameters.visitorsList.set(
                            instrumentationCreationConfig.registeredDependenciesClassesVisitors
                        )
                        parameters.bootClasspath.set(creationConfig.global.fullBootClasspathProvider)
                        parameters.classesHierarchyBuildService.set(
                            getBuildService(creationConfig.services.buildServiceRegistry)
                        )
                        parameters.profilingTransforms.set(
                                if (creationConfig is ApkCreationConfig) {
                                    creationConfig.advancedProfilingTransforms
                                } else emptyList()
                        )
                    }

                    spec.from.attribute(
                        ARTIFACT_TYPE_ATTRIBUTE,
                        AndroidArtifacts.ArtifactType.CLASSES_JAR.type
                    )
                    spec.to.attribute(
                        ARTIFACT_TYPE_ATTRIBUTE,
                        AndroidArtifacts.ArtifactType.ASM_INSTRUMENTED_JARS.type
                    )

                    getAttributesForConfig(creationConfig)
                        .stringAttributes?.forEach { (name, value) ->
                            spec.from.attribute(name, value)
                            spec.to.attribute(name, value)
                        }
                }
            }
        }

    }

    @get:CompileClasspath
    @get:InputArtifactDependencies
    abstract val classpath: FileCollection

    @get:Classpath
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        //TODO(b/162813654) record transform execution span
        val inputFile = inputArtifact.get().asFile

        val classesHierarchyResolver = parameters.classesHierarchyBuildService.get()
            .getClassesHierarchyResolverBuilder()
            .addDependenciesSources(inputArtifact.get().asFile)
            .addDependenciesSources(classpath.files)
            .addDependenciesSources(parameters.bootClasspath.get().map { it.asFile })
            .build()

        AsmInstrumentationManager(
            parameters.visitorsList.get(),
            parameters.asmApiVersion.get(),
            classesHierarchyResolver,
            parameters.classesHierarchyBuildService.get().issueHandler,
            parameters.framesComputationMode.get(),
            parameters.excludes.get(),
            parameters.profilingTransforms.get()
        ).use {
            it.instrumentClassesFromJarToJar(
                    inputFile,
                    outputs.file(inputFile.name)
            )
        }
    }

    interface Parameters : GenericTransformParameters {
        @get:Internal
        val asmApiVersion: Property<Int>

        @get:Input
        val framesComputationMode: Property<FramesComputationMode>

        @get:Input
        val excludes: SetProperty<String>

        @get:Nested
        val visitorsList: ListProperty<AsmClassVisitorFactory<*>>

        @get:CompileClasspath
        val bootClasspath: ListProperty<RegularFile>

        @get:Internal
        val classesHierarchyBuildService: Property<ClassesHierarchyBuildService>

        @get:Input
        val profilingTransforms: ListProperty<String>
    }
}
