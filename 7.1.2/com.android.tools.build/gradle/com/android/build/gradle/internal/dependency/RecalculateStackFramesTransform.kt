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

import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.dependency.AsmClassesTransform.Companion.ATTR_ASM_TRANSFORMED_VARIANT
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.services.ClassesHierarchyBuildService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.FixStackFramesDelegate
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.InputArtifactDependencies
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.artifacts.ArtifactAttributes
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

@CacheableTransform
abstract class RecalculateStackFramesTransform :
    TransformAction<RecalculateStackFramesTransform.Parameters> {

    companion object {
        fun getAttributesForConfig(creationConfig: ComponentCreationConfig)
                : AndroidAttributes {
            return if (creationConfig.dependenciesClassesAreInstrumented &&
                creationConfig.asmFramesComputationMode ==
                FramesComputationMode.COMPUTE_FRAMES_FOR_ALL_CLASSES
            ) {
                AndroidAttributes(
                    mapOf(ATTR_ASM_TRANSFORMED_VARIANT to creationConfig.name)
                )
            } else {
                AndroidAttributes(
                    mapOf(ATTR_ASM_TRANSFORMED_VARIANT to "")
                )
            }
        }

        fun registerGlobalRecalculateStackFramesTransform(
            projectName: String,
            dependencyHandler: DependencyHandler,
            bootClasspathProvider: Provider<List<RegularFile>>,
            buildServiceRegistry: BuildServiceRegistry
        ) {
            registerRecalculateStackFramesTransform(
                projectName,
                dependencyHandler,
                bootClasspathProvider,
                buildServiceRegistry,
                AndroidAttributes(
                    mapOf(ATTR_ASM_TRANSFORMED_VARIANT to "")
                ),
                false
            )
        }

        fun registerRecalculateStackFramesTransformForComponent(
            projectName: String,
            dependencyHandler: DependencyHandler,
            creationConfig: ComponentCreationConfig
        ) {
            if (creationConfig.dependenciesClassesAreInstrumented &&
                creationConfig.asmFramesComputationMode ==
                FramesComputationMode.COMPUTE_FRAMES_FOR_ALL_CLASSES
            ) {
                registerRecalculateStackFramesTransform(
                    projectName,
                    dependencyHandler,
                    creationConfig.globalScope.fullBootClasspathProvider,
                    creationConfig.services.buildServiceRegistry,
                    getAttributesForConfig(creationConfig),
                    true
                )
            }
        }

        private fun registerRecalculateStackFramesTransform(
            projectName: String,
            dependencyHandler: DependencyHandler,
            bootClasspathProvider: Provider<List<RegularFile>>,
            buildServiceRegistry: BuildServiceRegistry,
            attributes: AndroidAttributes,
            fixInstrumentedJars: Boolean
        ) {
            dependencyHandler.registerTransform(RecalculateStackFramesTransform::class.java) { spec ->
                if (fixInstrumentedJars) {
                    spec.from.attribute(
                        ArtifactAttributes.ARTIFACT_FORMAT,
                        AndroidArtifacts.ArtifactType.ASM_INSTRUMENTED_JARS.type
                    )
                } else {
                    spec.from.attribute(
                        ArtifactAttributes.ARTIFACT_FORMAT,
                        AndroidArtifacts.ArtifactType.CLASSES_JAR.type
                    )
                }

                spec.to.attribute(
                    ArtifactAttributes.ARTIFACT_FORMAT,
                    AndroidArtifacts.ArtifactType.CLASSES_FIXED_FRAMES_JAR.type
                )

                spec.parameters { params ->
                    params.projectName.set(projectName)
                    params.bootClasspath.set(bootClasspathProvider)
                    params.classesHierarchyBuildService.set(
                        getBuildService(buildServiceRegistry)
                    )
                }

                attributes.stringAttributes?.forEach { name, value ->
                    spec.from.attribute(name, value)
                    spec.to.attribute(name, value)
                }
            }
        }
    }

    @get:CompileClasspath
    @get:InputArtifactDependencies
    abstract val classpath: FileCollection

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        //TODO(b/162813654) record transform execution span
        val inputFile = inputArtifact.get().asFile
        val classesHierarchyResolver = parameters.classesHierarchyBuildService.get()
            .getClassesHierarchyResolverBuilder()
            .addDependenciesSources(parameters.bootClasspath.get().map { it.asFile })
            .addDependenciesSources(inputArtifact.get().asFile)
            .addDependenciesSources(classpath.files)
            .build()

        FixStackFramesDelegate.transformJar(
            inputFile,
            outputs.file(inputFile.name),
            classesHierarchyResolver
        )
    }

    interface Parameters : GenericTransformParameters {
        @get:CompileClasspath
        val bootClasspath: ListProperty<RegularFile>

        @get:Internal
        val classesHierarchyBuildService: Property<ClassesHierarchyBuildService>
    }
}
