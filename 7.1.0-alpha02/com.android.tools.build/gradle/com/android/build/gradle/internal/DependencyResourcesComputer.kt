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
package com.android.build.gradle.internal

import com.android.SdkConstants.FD_RES_VALUES
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ANDROID_RES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.ProcessApplicationManifest
import com.android.build.gradle.tasks.SourceSetInputs
import com.android.builder.core.BuilderConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceSet
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.MapProperty
import java.io.File

class DependencyResourcesComputer {
    @set:VisibleForTesting
    lateinit var resources: Map<String, FileCollection>

    @set:VisibleForTesting
    var libraries: ArtifactCollection? = null

    @set:VisibleForTesting
    lateinit var renderscriptResOutputDir: FileCollection

    @set:VisibleForTesting
    lateinit var generatedResOutputDir: FileCollection

    @set:VisibleForTesting
    var microApkResDirectory: FileCollection? = null

    @set:VisibleForTesting
    var extraGeneratedResFolders: FileCollection? = null

    var validateEnabled: Boolean = false
        private set

    private fun addLibraryResources(
        libraries: ArtifactCollection?,
        resourceSetList: MutableList<ResourceSet>,
        resourceArePrecompiled: Boolean,
        aaptEnv: String?
    ) {
        // add at the beginning since the libraries are less important than the folder based
        // resource sets.
        // get the dependencies first
        libraries?.let {
            val libArtifacts = it.artifacts

            // the order of the artifact is descending order, so we need to reverse it.
            for (artifact in libArtifacts) {
                val resourceSet = ResourceSet(
                    ProcessApplicationManifest.getArtifactName(artifact),
                    ResourceNamespace.RES_AUTO, null,
                    validateEnabled,
                    aaptEnv
                )
                resourceSet.isFromDependency = true
                resourceSet.addSource(artifact.file)

                if (resourceArePrecompiled) {
                    // For values resources we impose stricter rules different from aapt so they need to go
                    // through the merging step.
                    resourceSet.setAllowedFolderPrefix(FD_RES_VALUES)
                }

                // add to 0 always, since we need to reverse the order.
                resourceSetList.add(0, resourceSet)
            }
        }
    }

    /**
     * Computes resource sets for merging, if [precompileDependenciesResources] flag is enabled we
     * filter out the non-values resources as it's precompiled and is consumed directly in the
     * linking step.
     */
    @JvmOverloads
    fun compute(
        precompileDependenciesResources: Boolean = false, aaptEnv: String?): List<ResourceSet> {
        val sourceFolderSets = getResSet(resources, aaptEnv)
        var size = sourceFolderSets.size
        libraries?.let {
            size += it.artifacts.size
        }

        val resourceSetList = ArrayList<ResourceSet>(size)

        addLibraryResources(
            libraries,
            resourceSetList,
            precompileDependenciesResources,
            aaptEnv
        )

        // add the folder based next
        resourceSetList.addAll(sourceFolderSets)

        // We add the generated folders to the main set
        val generatedResFolders = mutableListOf<File>()

        generatedResFolders.addAll(renderscriptResOutputDir.files)

        generatedResFolders.addAll(generatedResOutputDir.files)

        extraGeneratedResFolders?.let {
            generatedResFolders.addAll(it.files)
        }
        microApkResDirectory?.let {
            generatedResFolders.addAll(it.files)
        }

        // add the generated files to the main set.
        if (sourceFolderSets.isNotEmpty()) {
            val mainResourceSet = sourceFolderSets[0]
            assert(
                mainResourceSet.configName == BuilderConstants.MAIN ||
                        // The main source set will not be included when building app android test
                        mainResourceSet.configName == BuilderConstants.ANDROID_TEST
            )
            mainResourceSet.addSources(generatedResFolders)
        }

        return resourceSetList
    }

    private fun getResSet(
        resourcesMap: Map<String, FileCollection> = resources, aaptEnv: String?)
    : List<ResourceSet> {
        return resourcesMap.map {
            val resourceSet = ResourceSet(
                it.key, ResourceNamespace.RES_AUTO, null, validateEnabled, aaptEnv)
            resourceSet.addSources(it.value.files)
            resourceSet
        }
    }

    fun initFromVariantScope(
        creationConfig: ComponentCreationConfig,
        sourceSetInputs: SourceSetInputs,
        microApkResDir: FileCollection,
        libraryDependencies: ArtifactCollection?) {
        val projectOptions = creationConfig.services.projectOptions
        val services = creationConfig.services

        validateEnabled = !projectOptions.get(BooleanOption.DISABLE_RESOURCE_VALIDATION)
        this.libraries = libraryDependencies

        resources = sourceSetInputs.localResources.forUseAtConfigurationTime().get()

        extraGeneratedResFolders = sourceSetInputs.extraGeneratedResDir
        renderscriptResOutputDir = services.fileCollection(
            File(sourceSetInputs.renderscriptResOutputDir.forUseAtConfigurationTime().get()))

        generatedResOutputDir = services.fileCollection(
            File(sourceSetInputs.generatedResDir.forUseAtConfigurationTime().get()))

        if (creationConfig.taskContainer.generateApkDataTask != null) {
            microApkResDirectory = microApkResDir

        }
    }
}
