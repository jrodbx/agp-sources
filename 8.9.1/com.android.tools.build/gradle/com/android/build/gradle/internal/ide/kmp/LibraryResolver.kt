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
    private val inputsMap = mutableMapOf<KotlinSourceSet, ArtifactCollectionsInputs>()
    private val artifactsMap = mutableMapOf<KotlinSourceSet, Map<VariantKey, ResolvedArtifact>>()
    private val javaDocArtifactsMap = mutableMapOf<KotlinSourceSet, Map<ComponentIdentifier, File>>()
    private val sourceArtifactsMap = mutableMapOf<KotlinSourceSet, Map<ComponentIdentifier, File>>()
    private val sampleArtifactsMap = mutableMapOf<KotlinSourceSet, Map<ComponentIdentifier, File>>()

    fun registerSourceSetArtifacts(
        sourceSet: KotlinSourceSet
    ) {
        if (inputsMap.containsKey(sourceSet)) {
            return
        }

        val component = sourceSetToCreationConfigMap.value[sourceSet]
            ?: throw IllegalArgumentException("Unable to find a component attached to sourceSet ${sourceSet.name}")

        val configType = AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH

        val inputs = inputsMap.getOrPut(sourceSet) {
            ArtifactCollectionsInputsImpl(
                variantDependencies = component.variantDependencies,
                projectPath = project.path,
                variantName = component.name,
                runtimeType = ArtifactCollectionsInputs.RuntimeType.FULL,
            )
        }

        artifactsMap.computeIfAbsent(sourceSet) {
            inputs.getAllArtifacts(configType).associateBy {
                it.variant.toKey()
            }
        }

        javaDocArtifactsMap.computeIfAbsent(sourceSet) {
            component.variantDependencies
                .getAdditionalArtifacts(configType, AdditionalArtifactType.JAVADOC)
                .associate { it.variant.owner to it.file }
        }
        sourceArtifactsMap.computeIfAbsent(sourceSet) {
            component.variantDependencies
                .getAdditionalArtifacts(configType, AdditionalArtifactType.SOURCE)
                .associate { it.variant.owner to it.file }
        }

        sampleArtifactsMap.computeIfAbsent(sourceSet) {
            component.variantDependencies
                .getAdditionalArtifacts(configType, AdditionalArtifactType.SAMPLE)
                .associate { it.variant.owner to it.file }
        }
    }

    private fun getInputs(sourceSet: KotlinSourceSet) = inputsMap[sourceSet]!!
    private fun getArtifacts(sourceSet: KotlinSourceSet) = artifactsMap[sourceSet]!!
    private fun getJavaDoc(sourceSet: KotlinSourceSet) = javaDocArtifactsMap[sourceSet]!!
    private fun getSources(sourceSet: KotlinSourceSet) = sourceArtifactsMap[sourceSet]!!
    private fun getSamples(sourceSet: KotlinSourceSet) = sampleArtifactsMap[sourceSet]!!

    fun getLibrary(
        variant: ResolvedVariantResult,
        sourceSet: KotlinSourceSet
    ) = com.android.build.gradle.internal.ide.dependencies.getLibrary(
        getInputs(sourceSet).projectPath,
        libraryService = libraryService,
        variant = variant,
        variantDependencies = emptyList(),
        artifactMap = getArtifacts(sourceSet),
        javadocArtifacts = getJavaDoc(sourceSet),
        sourceArtifacts = getSources(sourceSet),
        sampleArtifacts = getSamples(sourceSet)
    )
}
