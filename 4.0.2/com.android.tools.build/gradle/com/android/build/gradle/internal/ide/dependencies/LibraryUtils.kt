/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:JvmName("LibraryUtils")
package com.android.build.gradle.internal.ide.dependencies

import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.FD_AAR_LIBS
import com.android.SdkConstants.FD_JARS
import com.android.SdkConstants.FN_RESOURCE_STATIC_LIBRARY
import com.android.build.api.attributes.VariantAttr
import com.android.build.gradle.internal.dependency.ConfigurationDependencyGraphs
import com.android.build.gradle.internal.ide.DependenciesImpl
import com.android.build.gradle.internal.ide.level2.AndroidLibraryImpl
import com.android.build.gradle.internal.ide.level2.EmptyDependencyGraphs
import com.android.build.gradle.internal.ide.level2.FullDependencyGraphsImpl
import com.android.build.gradle.internal.ide.level2.JavaLibraryImpl
import com.android.build.gradle.internal.ide.level2.ModuleLibraryImpl
import com.android.build.gradle.internal.ide.level2.SimpleDependencyGraphsImpl
import com.android.build.gradle.internal.res.namespaced.getAutoNamespacedLibraryFileName
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.AndroidLibrary
import com.android.builder.model.AndroidProject
import com.android.builder.model.Dependencies
import com.android.builder.model.level2.DependencyGraphs
import com.android.builder.model.level2.Library
import com.android.ide.common.caching.CreatingCache
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import java.io.File

fun ResolvedArtifactResult.getVariantName(): String? {
    return variant.attributes.getAttribute(VariantAttr.ATTRIBUTE)?.name
}


fun clone(dependencies: Dependencies, modelLevel: Int): Dependencies {
    if (modelLevel >= AndroidProject.MODEL_LEVEL_4_NEW_DEP_MODEL) {
        return DependenciesImpl.EMPTY
    }

    // these items are already ready for serializable, all we need to clone is
    // the Dependencies instance.
    val libraries = emptyList<AndroidLibrary>()
    val javaLibraries = Lists.newArrayList(dependencies.javaLibraries)
    val projects = emptyList<Dependencies.ProjectIdentifier>()

    return DependenciesImpl(
        libraries,
        javaLibraries,
        projects,
        Lists.newArrayList(dependencies.runtimeOnlyClasses)
    )
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

/**
 * Adds the receiver to the global library cache in a safe way.
 */
fun Library.addToGlobalCache() {
    synchronized(globalLibrary) {
        globalLibrary[this.artifactAddress] = this
    }
}

fun clearCaches() {
    globalLibrary.clear()
    libraryCache.clear()
    localJarCache.clear()
}

fun getGlobalLibMap(): Map<String, Library> {
    return ImmutableMap.copyOf(globalLibrary)
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

    library.addToGlobalCache()

    return library
}

fun findResStaticLibrary(
    variantScope: VariantScope, explodedAar: ResolvedArtifact
): File? {
    val file = findResStaticLibrary(explodedAar)
    if (file != null) {
        return file
    }

    if (variantScope.globalScope.extension.aaptOptions.namespaced && variantScope
            .globalScope
            .projectOptions
            .get(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES)
    ) {
        val artifacts = variantScope.artifacts
        val convertedDirectory =
            artifacts.getFinalProduct(InternalArtifactType.RES_CONVERTED_NON_NAMESPACED_REMOTE_DEPENDENCIES)
        if (convertedDirectory.isPresent) {
            return File(
                convertedDirectory.get().asFile,
                getAutoNamespacedLibraryFileName(
                    explodedAar.componentIdentifier
                )
            )
        }
    }
    // Not auto-namespaced, nor present in the original artifact
    return null
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

val libraryCache =
    CreatingCache<ResolvedArtifact, Library>(CreatingCache.ValueFactory<ResolvedArtifact, Library> {
        instantiateLibrary(it)
    })

private val globalLibrary = Maps.newHashMap<String, Library>()

val localJarCache = CreatingCache<File, List<File>>(CreatingCache.ValueFactory<File, List<File>> {
    val localJarRoot = FileUtils.join(it, FD_JARS, FD_AAR_LIBS)

    if (!localJarRoot.isDirectory) {
        ImmutableList.of<File>()
    } else {
        val jarFiles = localJarRoot.listFiles { _, name -> name.endsWith(DOT_JAR) }
        if (jarFiles != null && jarFiles.isNotEmpty()) {
            ImmutableList.copyOf<File>(jarFiles)
        } else ImmutableList.of<File>()
    }
})