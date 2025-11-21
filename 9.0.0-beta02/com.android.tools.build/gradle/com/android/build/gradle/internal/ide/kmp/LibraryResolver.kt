/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.gradle.internal.ide.kmp

import com.android.build.gradle.internal.component.KmpComponentCreationConfig
import com.android.build.gradle.internal.dependency.AdditionalArtifactType
import com.android.build.gradle.internal.ide.dependencies.ArtifactCollectionsInputs
import com.android.build.gradle.internal.ide.dependencies.ArtifactCollectionsInputsImpl
import com.android.build.gradle.internal.ide.dependencies.LibraryService
import com.android.build.gradle.internal.ide.dependencies.ResolvedArtifact
import com.android.build.gradle.internal.ide.dependencies.VariantKey
import com.android.build.gradle.internal.ide.dependencies.toKey
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.builder.model.v2.ide.Library
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import java.io.File

class LibraryResolver(
    private val project: Project,
    private val libraryService: LibraryService,
    private val sourceSetToCreationConfigMap: Lazy<Map<KotlinSourceSet, KmpComponentCreationConfig>>,
) {
    private val inputsMap = mutableMapOf<SourceSetConfigurationSpec, ArtifactCollectionsInputs>()
    private val artifactsMap = mutableMapOf<SourceSetConfigurationSpec, Map<VariantKey, ResolvedArtifact>>()
    private val javaDocArtifactsMap = mutableMapOf<SourceSetConfigurationSpec, Map<ComponentIdentifier, File>>()
    private val sourceArtifactsMap = mutableMapOf<SourceSetConfigurationSpec, Map<ComponentIdentifier, List<File>>>()

    fun registerSourceSetArtifacts(
        sourceSet: KotlinSourceSet,
        configType: AndroidArtifacts.ConsumedConfigType
    ) {
        val sourceSetConfigurationSpec = SourceSetConfigurationSpec(sourceSet, configType)
        if (inputsMap.containsKey(sourceSetConfigurationSpec)) {
            return
        }

        val component = sourceSetToCreationConfigMap.value[sourceSet]
            ?: throw IllegalArgumentException("Unable to find a component attached to sourceSet ${sourceSet.name}")

        val inputs = inputsMap.getOrPut(sourceSetConfigurationSpec) {
            ArtifactCollectionsInputsImpl(
                variantDependencies = component.variantDependencies,
                projectPath = project.path,
                variantName = component.name,
                runtimeType = ArtifactCollectionsInputs.RuntimeType.FULL,
            )
        }

        artifactsMap.getOrPut(sourceSetConfigurationSpec) {
            inputs.getAllArtifacts(configType).associateBy {
                it.variant.toKey()
            }
        }

        javaDocArtifactsMap.getOrPut(sourceSetConfigurationSpec) {
            component.variantDependencies
                .getAdditionalArtifacts(configType, AdditionalArtifactType.JAVADOC)
                .associate { it.variant.owner to it.file }
        }
        sourceArtifactsMap.getOrPut(sourceSetConfigurationSpec) {
            component.variantDependencies
                .getAdditionalArtifacts(configType, AdditionalArtifactType.SOURCE)
                .groupBy({ it.variant.owner} ) { it.file }
        }
    }

    private fun getInputs(sourceSetConfigurationSpec: SourceSetConfigurationSpec) = inputsMap[sourceSetConfigurationSpec]!!
    private fun getArtifacts(sourceSetConfigurationSpec: SourceSetConfigurationSpec) = artifactsMap[sourceSetConfigurationSpec]!!
    private fun getJavaDoc(sourceSetConfigurationSpec: SourceSetConfigurationSpec) = javaDocArtifactsMap[sourceSetConfigurationSpec]!!
    private fun getSources(sourceSetConfigurationSpec: SourceSetConfigurationSpec) = sourceArtifactsMap[sourceSetConfigurationSpec]!!

    fun getLibrary(
        variant: ResolvedVariantResult,
        sourceSet: KotlinSourceSet,
        configType: AndroidArtifacts.ConsumedConfigType
    ): Library? {
        val sourceSetConfigurationSpec = SourceSetConfigurationSpec(sourceSet, configType)
        return com.android.build.gradle.internal.ide.dependencies.getLibrary(
            getInputs(sourceSetConfigurationSpec).projectPath,
            libraryService = libraryService,
            variant = variant,
            variantDependencies = emptyList(),
            artifactMap = getArtifacts(sourceSetConfigurationSpec),
            javadocArtifacts = getJavaDoc(sourceSetConfigurationSpec),
            sourceArtifacts = getSources(sourceSetConfigurationSpec),
        )
    }
}

private data class SourceSetConfigurationSpec(
    val sourceSet: KotlinSourceSet,
    val configType: AndroidArtifacts.ConsumedConfigType
)
