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
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ANDROID_RES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH

import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.ProcessApplicationManifest
import com.android.builder.core.BuilderConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceSet
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.FileCollection
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
        resourceArePrecompiled: Boolean
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
                    validateEnabled
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
    fun compute(precompileDependenciesResources: Boolean = false): List<ResourceSet> {
        val sourceFolderSets = getResSet()
        var size = sourceFolderSets.size
        libraries?.let {
            size += it.artifacts.size
        }

        val resourceSetList = ArrayList<ResourceSet>(size)

        addLibraryResources(libraries, resourceSetList, precompileDependenciesResources)

        // add the folder based next
        resourceSetList.addAll(sourceFolderSets)

        // We add the generated folders to the main set
        val generatedResFolders = java.util.ArrayList<File>()

        renderscriptResOutputDir?.let {
            generatedResFolders.addAll(it.files)
        }

        generatedResOutputDir?.let {
            generatedResFolders.addAll(it.files)
        }

        extraGeneratedResFolders?.let {
            generatedResFolders.addAll(it.files)
        }
        microApkResDirectory?.let {
            generatedResFolders.addAll(it.files)
        }

        // add the generated files to the main set.
        if (sourceFolderSets.isNotEmpty()) {
            val mainResourceSet = sourceFolderSets[0]
            assert(mainResourceSet.configName == BuilderConstants.MAIN)
            mainResourceSet.addSources(generatedResFolders)
        }

        return resourceSetList
    }

    private fun getResSet(): List<ResourceSet> {
        val builder = ImmutableList.builder<ResourceSet>()
        resources?.let {
            for ((key, value) in it) {
                val resourceSet = ResourceSet(
                    key, ResourceNamespace.RES_AUTO, null, validateEnabled)
                resourceSet.addSources(value.files)
                builder.add(resourceSet)
            }
        }
        return builder.build()
    }

    fun initFromVariantScope(variantScope: VariantScope, includeDependencies: Boolean) {
        val globalScope = variantScope.globalScope
        val variantData = variantScope.variantData
        val project = globalScope.project

        validateEnabled = !globalScope.projectOptions.get(BooleanOption.DISABLE_RESOURCE_VALIDATION)

        if (includeDependencies) {
            this.libraries = variantScope.getArtifactCollection(RUNTIME_CLASSPATH, ALL, ANDROID_RES)
        }

        resources = variantData.androidResources

        extraGeneratedResFolders = variantData.extraGeneratedResFolders
        renderscriptResOutputDir = project.files(variantScope.renderscriptResOutputDir)

        generatedResOutputDir = project.files(variantScope.generatedResOutputDir)

        if (variantScope.taskContainer.microApkTask != null &&
            variantData.variantDslInfo.isEmbedMicroApp) {
            microApkResDirectory = project.files(variantScope.microApkResDirectory)
        }
    }
}
