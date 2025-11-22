/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.build.gradle.internal.ide.dependencies.ResolvedArtifact
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.publishing.getAttributes
import com.google.common.collect.Sets
import org.gradle.api.Action
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.model.ObjectFactory

/**
 * Resolvable dependencies of a test suite. Do not resolve these configurations before execution
 * phase.
 */
class TestSuiteSourceClasspath(
    /**
     * The test suite classpath that can be used to compile the test suite sources
     */
    val compileClasspath: Configuration,

    /**
     * The test suite runtime classpath that can be used when configuring the test task or to
     * package in the resulting test APK depending on source type.
     */
    val runtimeClasspath: Configuration,

    val objectFactory: ObjectFactory,
): ResolutionResultProvider {

    fun resolvedArtifacts(
        artifactCollection: ArtifactCollection,
        dependencyFailureHandler: (Collection<Throwable>) -> Any = {}
    ): Set<ResolvedArtifact> {

        val resolvedArtifacts = artifactCollection.artifacts

        // use a linked hash set to keep the artifact order.
        val artifacts =
            Sets.newLinkedHashSetWithExpectedSize<ResolvedArtifact>(resolvedArtifacts.size)

        resolvedArtifacts.forEach { resolvedArtifact ->
            artifacts.add(
                ResolvedArtifact(
                    mainArtifactResult = resolvedArtifact,
                    artifactFile = resolvedArtifact.file,
                    extractedFolder = null,
                    publishedLintJar = null,
                    dependencyType = ResolvedArtifact.DependencyType.JAVA,
                    isWrappedModule = false
                )
            )

        }
        return artifacts
    }

    fun getArtifactCollectionForToolingModel(
        configType: ConsumedConfigType,
        artifactType: AndroidArtifacts.ArtifactType,
        configurationFactory: (Configuration) -> Configuration = { it }
    ): ArtifactCollection {
        val configuration = configurationFactory(
            when (configType) {
                ConsumedConfigType.COMPILE_CLASSPATH -> compileClasspath
                ConsumedConfigType.RUNTIME_CLASSPATH -> runtimeClasspath
                else -> throw RuntimeException("Test suite do not support $configType")
            }
        )
        val attributesAction =
            Action { container: AttributeContainer ->
                container.attribute(AndroidArtifacts.ARTIFACT_TYPE, artifactType.type)
                artifactType.getAttributes { type, name ->
                    objectFactory.named(type, name)
                }.addAttributesToContainer(container)
            }

        return configuration
            .incoming
            .artifactView { config: ArtifactView.ViewConfiguration ->
                config.attributes(attributesAction)
            }
            .artifacts
    }

    override fun getResolutionResult(configType: ConsumedConfigType): ResolutionResult = when (configType) {
        ConsumedConfigType.COMPILE_CLASSPATH -> compileClasspath.incoming.resolutionResult
        ConsumedConfigType.RUNTIME_CLASSPATH -> runtimeClasspath.incoming.resolutionResult
        else -> throw RuntimeException("Unsupported ConsumedConfigType value: $configType")
    }
    override fun getAdditionalArtifacts(
        configType: AndroidArtifacts.ConsumedConfigType,
        type: AdditionalArtifactType
    ): ArtifactCollection {
        throw RuntimeException("Not yet implemented")
    }
}
