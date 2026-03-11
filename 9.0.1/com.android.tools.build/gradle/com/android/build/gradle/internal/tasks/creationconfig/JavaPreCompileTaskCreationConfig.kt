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

package com.android.build.gradle.internal.tasks.creationconfig

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.impl.AnnotationProcessorImpl
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.KmpComponentCreationConfig
import com.android.build.gradle.internal.component.TaskCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ARTIFACT_TYPE
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.utils.findKaptOrKspConfigurationsForVariant
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Provider

interface JavaPreCompileTaskCreationConfig: TaskCreationConfig {
    val annotationProcessorArtifacts: ArtifactCollection?
    val finalListOfClassNames: Provider<List<String>>
    val kspProcessorArtifacts: ArtifactCollection?
}

fun createJavaPreCompileConfig(
    creationConfig: ComponentCreationConfig,
    usingKapt: Boolean,
    usingKsp: Boolean,
): JavaPreCompileTaskCreationConfig {
    return when (creationConfig) {
        is KmpComponentCreationConfig -> object : BaseJavaPreCompileTaskCreationConfig(
            creationConfig,
            usingKapt,
            usingKsp
        ) {
            override val annotationProcessorArtifacts: ArtifactCollection?
                get() = if (usingKapt) super.annotationProcessorArtifacts else null
        }

        else -> BaseJavaPreCompileTaskCreationConfig(creationConfig, usingKapt, usingKsp)
    }
}

internal open class BaseJavaPreCompileTaskCreationConfig(
    val creationConfig: ComponentCreationConfig,
    val usingKapt: Boolean,
    usingKsp: Boolean
) :
    JavaPreCompileTaskCreationConfig, TaskCreationConfig by creationConfig {

    private val kaptClasspath: Configuration? = if (usingKapt) {
        createKaptOrKspClassPath("kapt")
    } else null

    // Create the configuration early to avoid issues with composite builds (e.g., bug 183952598)
    private val kspClasspath: Configuration? = if (usingKsp) {
        createKaptOrKspClassPath("ksp")
    } else null

    override val artifacts: ArtifactsImpl
        get() = creationConfig.artifacts
    override val annotationProcessorArtifacts: ArtifactCollection?
        get() {
            // Query for JAR instead of PROCESSED_JAR as this task only cares about the original
            // jars.
            return if (usingKapt) {
                kaptClasspath!!.incoming
                    .artifactView { config: ArtifactView.ViewConfiguration ->
                        config.attributes { it.attribute(ARTIFACT_TYPE, ArtifactType.JAR.type) }
                    }
                    .artifacts
            } else {
                creationConfig.variantDependencies
                    .getArtifactCollection(
                        ConsumedConfigType.ANNOTATION_PROCESSOR,
                        ArtifactScope.ALL,
                        ArtifactType.JAR
                    )
            }
        }
    override val finalListOfClassNames: Provider<List<String>>
        get() = (creationConfig.javaCompilation.annotationProcessor as AnnotationProcessorImpl)
            .finalListOfClassNames
    override val kspProcessorArtifacts: ArtifactCollection?
        get() = kspClasspath?.incoming
            ?.artifactView { config: ArtifactView.ViewConfiguration ->
                config.attributes { it.attribute(ARTIFACT_TYPE, ArtifactType.JAR.type) }
            }
            ?.artifacts

    // Create the configuration early to avoid issues with composite builds (e.g., bug 183952598)
    private fun createKaptOrKspClassPath(kaptOrKsp: String): Configuration {
        val configurations = findKaptOrKspConfigurationsForVariant(
            creationConfig,
            kaptOrKsp
        )
        // This is a private detail, so we want to use a detached configuration, but it's not
        // possible because of https://github.com/gradle/gradle/issues/6881.
        return creationConfig.services.configurations
            .create("_agp_internal_${name}_${kaptOrKsp}Classpath")
            .setExtendsFrom(configurations)
            .apply {
                isVisible = false
                isCanBeResolved = true
                isCanBeConsumed = false
            }
    }
}
