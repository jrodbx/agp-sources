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

package com.android.build.gradle.internal.ide.dependencies

import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.FD_AAR_LIBS
import com.android.SdkConstants.FD_JARS
import com.android.SdkConstants.FN_RESOURCE_STATIC_LIBRARY
import com.android.build.gradle.internal.dependency.ConfigurationDependencyGraphs
import com.android.build.gradle.internal.ide.level2.AndroidLibraryImpl
import com.android.build.gradle.internal.ide.level2.EmptyDependencyGraphs
import com.android.build.gradle.internal.ide.level2.FullDependencyGraphsImpl
import com.android.build.gradle.internal.ide.level2.JavaLibraryImpl
import com.android.build.gradle.internal.ide.level2.ModuleLibraryImpl
import com.android.build.gradle.internal.ide.level2.SimpleDependencyGraphsImpl
import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.builder.model.AndroidProject
import com.android.builder.model.level2.DependencyGraphs
import com.android.builder.model.level2.Library
import com.android.ide.common.caching.CreatingCache
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Maps
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File

/** Build service used to cache library dependencies used in the model builder. */
abstract class LibraryDependencyCacheBuildService
    : BuildService<BuildServiceParameters.None>, AutoCloseable {

    val libraryCache =
        CreatingCache(CreatingCache.ValueFactory<ResolvedArtifact, Library> {
            instantiateLibrary(it)
        })

    private val globalLibrary = Maps.newHashMap<String, Library>()

    val localJarCache = CreatingCache<File, List<File>>(CreatingCache.ValueFactory {
        val localJarRoot = FileUtils.join(it, FD_JARS, FD_AAR_LIBS)

        if (!localJarRoot.isDirectory) {
            ImmutableList.of<File>()
        } else {
            val jarFiles = localJarRoot.listFiles { _, name -> name.endsWith(DOT_JAR) }
            if (jarFiles != null && jarFiles.isNotEmpty()) {
                ImmutableList.copyOf(jarFiles)
            } else ImmutableList.of()
        }
    })

    fun getGlobalLibMap(): Map<String, Library> {
        return ImmutableMap.copyOf(globalLibrary)
    }

    fun clone(
        dependencyGraphs: DependencyGraphs,
        modelLevel: Int,
        modelWithFullDependency: Boolean
    ): DependencyGraphs {
        if (modelLevel < AndroidProject.MODEL_LEVEL_4_NEW_DEP_MODEL) {
            return EmptyDependencyGraphs.EMPTY
        }

        Preconditions.checkState(dependencyGraphs is ConfigurationDependencyGraphs)
        val cdg = dependencyGraphs as ConfigurationDependencyGraphs

        // these items are already ready for serializable, all we need to clone is
        // the DependencyGraphs instance.

        val libs = cdg.libraries
        synchronized(globalLibrary) {
            for (library in libs) {
                globalLibrary[library.artifactAddress] = library
            }
        }

        val nodes = cdg.compileDependencies

        return if (modelWithFullDependency) {
            FullDependencyGraphsImpl(
                nodes, nodes, ImmutableList.of(), ImmutableList.of()
            )
        } else SimpleDependencyGraphsImpl(nodes, cdg.providedLibraries)

        // just need to register the libraries in the global libraries.
    }

    override fun close() {
        libraryCache.clear()
        globalLibrary.clear()
        localJarCache.clear()
    }

    private fun instantiateLibrary(artifact: ResolvedArtifact): Library {
        val library: Library
        val id = artifact.componentIdentifier
        val address = artifact.computeModelAddress()

        if (id !is ProjectComponentIdentifier || artifact.isWrappedModule) {
            if (artifact.dependencyType === ResolvedArtifact.DependencyType.ANDROID) {
                val extractedFolder = Preconditions.checkNotNull<File>(artifact.extractedFolder)
                library = AndroidLibraryImpl(
                    address,
                    artifact.artifactFile,
                    extractedFolder,
                    // TODO(b/110879504): Auto-namespacing in level4 model
                    findResStaticLibrary(artifact),
                    findLocalJarsAsStrings(extractedFolder)
                )
            } else {
                library = JavaLibraryImpl(address, artifact.artifactFile)
            }
        } else {
            // get the build ID
            val buildId = id.getBuildId(artifact.buildMapping)

            library = ModuleLibraryImpl(
                address,
                buildId!!,
                id.projectPath,
                artifact.variantName
            )
        }

        synchronized(globalLibrary) {
            globalLibrary[library.artifactAddress] = library
        }

        return library
    }

    private fun findLocalJarsAsStrings(folder: File): List<String> {
        val localJarRoot = FileUtils.join(folder, FD_JARS, FD_AAR_LIBS)

        if (!localJarRoot.isDirectory) {
            return ImmutableList.of()
        }

        val jarFiles = localJarRoot.list { _, name -> name.endsWith(DOT_JAR) }
        if (jarFiles != null && jarFiles.isNotEmpty()) {
            val list = ImmutableList.builder<String>()
            for (jarFile in jarFiles) {
                list.add(FD_JARS + File.separatorChar + FD_AAR_LIBS + File.separatorChar + jarFile)
            }

            return list.build()
        }

        return ImmutableList.of()
    }

    private fun findResStaticLibrary(explodedAar: ResolvedArtifact): File? {
        if (explodedAar.extractedFolder == null) {
            return null
        }

        val file = File(explodedAar.extractedFolder, FN_RESOURCE_STATIC_LIBRARY)
        return if (!file.exists()) {
            null
        } else file
    }

    class RegistrationAction(
        project: Project) :
        ServiceRegistrationAction<LibraryDependencyCacheBuildService, BuildServiceParameters.None>(
            project,
            LibraryDependencyCacheBuildService::class.java
        ) {
        override fun configure(parameters: BuildServiceParameters.None) {
            // do nothing
        }
    }
}