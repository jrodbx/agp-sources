/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.build.api.attributes.BuildTypeAttr
import com.android.build.api.attributes.ProductFlavorAttr
import com.android.build.gradle.internal.ide.v2.EdgeImpl
import com.android.build.gradle.internal.ide.v2.LibraryImpl
import com.android.build.gradle.internal.ide.v2.LibraryInfoImpl
import com.android.build.gradle.internal.ide.v2.ProjectInfoImpl
import com.android.build.gradle.internal.testFixtures.isLibraryTestFixturesCapability
import com.android.build.gradle.internal.testFixtures.isProjectTestFixturesCapability
import com.android.builder.model.v2.ide.Edge
import com.android.builder.model.v2.ide.Library
import com.android.ide.common.caching.CreatingCache
import com.android.utils.FileUtils
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import java.io.File

/**
 * a Service that can create a [Library] for a given [ResolvedArtifact].
 *
 * The implementation will cache the created instances globally across build to reuse them.
 */
interface LibraryService {
    fun getAllLibraries(): Map<String, Library>

    fun getLibrary(artifact: ResolvedArtifact, additionalArtifacts: AdditionalArtifacts): Library
}

/** A cache for libraries that's used to cache instances globally across the build. */
interface LibraryCache {
    fun getLibrary(artifact: ResolvedArtifact, additionalArtifacts: AdditionalArtifacts): Library
}

/**
 * A String caching services.
 *
 * This is used to share instances of string as much as possible (similar to [String.intern] which
 * has performance issues.
 */
interface StringCache {

    fun cacheString(string: String): String
}

interface GraphEdgeCache {
    fun getEdge(from: String, to: String): Edge
}
/**
 * A Cache for Local Jars.
 *
 * Provided a [File] to an exploded AAR, this goes through the File system to find local jars
 * in the folder and cache the result so that subsequent calls for the same folder does not
 * have to do IO again.
 */
interface LocalJarCache {

    fun getLocalJarsForAar(aar: File): List<File>?
}

class GraphEdgeCacheImpl : GraphEdgeCache {
    private val cache = mutableMapOf<Pair<String, String>, Edge>()

    override fun getEdge(from: String, to: String): Edge {
        synchronized(cache) {
            return cache.computeIfAbsent(from to to) {EdgeImpl(from, to)}
        }
    }

    fun clear() {
        cache.clear()
    }
}

class StringCacheImpl : StringCache {

    private val cache = mutableMapOf<String, String>()
    override fun cacheString(string: String): String {
        synchronized(cache) {
            return cache.putIfAbsent(string, string) ?: string
        }
    }

    fun clear() {
        cache.clear()
    }
}

class LocalJarCacheImpl : LocalJarCache {

    override fun getLocalJarsForAar(aar: File): List<File>? = cache[aar]

    fun clear() {
        cache.clear()
    }

    private val cache = CreatingCache<File, List<File>> {
        val localJarRoot = FileUtils.join(it, SdkConstants.FD_JARS, SdkConstants.FD_AAR_LIBS)

        if (!localJarRoot.isDirectory) {
            listOf()
        } else {
            val jarFiles = localJarRoot.listFiles { _, name -> name.endsWith(SdkConstants.DOT_JAR) }
            if (!jarFiles.isNullOrEmpty()) {
                // Sort by name, rather than relying on the file system iteration order
                jarFiles.sortedBy(File::getName)
            } else {
                listOf()
            }
        }
    }
}

class LibraryServiceImpl(private val libraryCache : LibraryCache) : LibraryService {
    private val libraries = mutableMapOf<String, Library>()
    override fun getAllLibraries() = libraries
    override fun getLibrary(
        artifact: ResolvedArtifact,
        additionalArtifacts: AdditionalArtifacts
    ) = libraryCache.getLibrary(artifact, additionalArtifacts).also {
        libraries.putIfAbsent(it.key, it)
    }
}

class LibraryCacheImpl(
    private val stringCache: StringCache,
    private val localJarCache: LocalJarCache
) : LibraryCache {

    // do not query directly. Use [getLibrary]
    private val libraryCache = mutableMapOf<ResolvedArtifact, Library>()

    /** Returns a [Library] instance matching the provided a [ResolvedArtifact]. */
    override fun getLibrary(artifact: ResolvedArtifact, additionalArtifacts: AdditionalArtifacts): Library =
        synchronized(libraryCache) {
            libraryCache.computeIfAbsent(artifact) {
                createLibrary(it, additionalArtifacts)
            }
        }


    fun clear() {
        libraryCache.clear()
        libraryInfoCache.clear()
        libraryInfoForLocalJarsCache.clear()
        projectInfoCache.clear()
    }

    // do not query directly. Use [getProjectInfo]
    private val projectInfoCache = mutableMapOf<ResolvedVariantResult, ProjectInfoImpl>()
    private fun getProjectInfo(variant: ResolvedVariantResult): ProjectInfoImpl =
        projectInfoCache.computeIfAbsent(variant) {
            val component = it.owner as ProjectComponentIdentifier

            val productFlavors = getProductFlavors(it)
            ProjectInfoImpl(
                getBuildType(it),
                productFlavors,
                getAttributeMap(it, productFlavors),
                getCapabilityList(it),
                stringCache.cacheString(component.build.buildPath),
                stringCache.cacheString(component.projectPath),
                it.capabilities.any { capability ->
                    capability.isProjectTestFixturesCapability(component.projectName)
                }
            )
        }

    // do not query directly. Use [getLibraryCache]
    private val libraryInfoCache = mutableMapOf<ResolvedVariantResult, LibraryInfoImpl>()

    // do not query directly. Use [getLibraryCache]
    private val libraryInfoForLocalJarsCache = mutableMapOf<File, LibraryInfoImpl>()
    private fun getLibraryInfo(artifact: ResolvedArtifact): LibraryInfoImpl =
    // we have to handle differently the case of external libraries which can be represented
    // uniquely by their ResolvedVariantResult and local jars which must
    // be represented, in theory, by a mix of path and variants (for the attributes).
    // In practice the attributes aren't needed really since there's no way to have a
    // local jar be variant aware. So we can take a shortcut and only consider the file
        // itself and skip the attributes. (there is already no capabilities for local jars)
        when (val component = artifact.variant.owner) {
            is ModuleComponentIdentifier -> {
                // simply query for the variant.
                libraryInfoCache.computeIfAbsent(artifact.variant) {
                    val productFlavors = getProductFlavors(it)
                    LibraryInfoImpl(
                        getBuildType(it),
                        productFlavors,
                        getAttributeMap(it, productFlavors),
                        getCapabilityList(it),
                        stringCache.cacheString(component.group),
                        stringCache.cacheString(component.module),
                        stringCache.cacheString(component.version),
                        it.capabilities.any { capability ->
                            capability.isLibraryTestFixturesCapability(
                                libraryName = component.module
                            )
                        }
                    )
                }
            }

            is OpaqueComponentArtifactIdentifier -> {
                libraryInfoForLocalJarsCache.computeIfAbsent(artifact.artifactFile!!) {
                    LibraryInfoImpl(
                        buildType = null,
                        productFlavors = mapOf(),
                        attributes = mapOf(),
                        capabilities = listOf(),
                        group = stringCache.cacheString(LOCAL_AAR_GROUPID),
                        name = stringCache.cacheString(it.absolutePath),
                        version = stringCache.cacheString("unspecified"),
                        isTestFixtures = false
                    )
                }
            }

            is ProjectComponentIdentifier -> {
                if (!artifact.isWrappedModule) {
                    throw IllegalArgumentException("${artifact.variant} is not wrapped")
                }
                synchronized(libraryInfoCache) {
                    libraryInfoCache.computeIfAbsent(artifact.variant) {
                        val productFlavors = getProductFlavors(it)
                        LibraryInfoImpl(
                            buildType = getBuildType(it),
                            productFlavors = productFlavors,
                            attributes = getAttributeMap(it, productFlavors),
                            capabilities = getCapabilityList(it),
                            group = WRAPPED_AAR_GROUPID,
                            name = stringCache.cacheString(component.getIdString()),
                            version = stringCache.cacheString("unspecified"),
                            isTestFixtures = false
                        )
                    }
                }
            }

            else -> {
                throw IllegalArgumentException("${artifact.variant.owner.javaClass} is not supported for LibraryInfo")
            }
        }

    /**
     * Handles an artifact.
     *
     * This optionally returns the model item that represents the artifact in case something needs
     * use the return
     */
    private fun createLibrary(
        artifact: ResolvedArtifact,
        additionalArtifacts: AdditionalArtifacts
    ): Library {
        val id = artifact.componentIdentifier

        return if (id !is ProjectComponentIdentifier || artifact.isWrappedModule) {
            val libraryInfo = getLibraryInfo(artifact)
            when (artifact.dependencyType) {
                ResolvedArtifact.DependencyType.ANDROID -> {
                    val folder = artifact.extractedFolder
                        ?: throw RuntimeException("Null extracted folder for artifact: $artifact")

                    val apiJar = FileUtils.join(folder, SdkConstants.FN_API_JAR)
                    val runtimeJar = FileUtils.join(
                        folder,
                        SdkConstants.FD_JARS,
                        SdkConstants.FN_CLASSES_JAR
                    )

                    val runtimeJarFiles =
                        listOf(runtimeJar) + (localJarCache.getLocalJarsForAar(folder) ?: listOf())
                    LibraryImpl.createAndroidLibrary(
                        key = stringCache.cacheString(libraryInfo.computeKey()),
                        libraryInfo = libraryInfo,
                        manifest = File(folder, SdkConstants.FN_ANDROID_MANIFEST_XML),
                        compileJarFiles = if (apiJar.isFile) listOf(apiJar) else runtimeJarFiles,
                        runtimeJarFiles = runtimeJarFiles,
                        resFolder = File(folder, SdkConstants.FD_RES),
                        resStaticLibrary = File(folder, SdkConstants.FN_RESOURCE_STATIC_LIBRARY),
                        assetsFolder = File(folder, SdkConstants.FD_ASSETS),
                        jniFolder = File(folder, SdkConstants.FD_JNI),
                        aidlFolder = File(folder, SdkConstants.FD_AIDL),
                        renderscriptFolder = File(folder, SdkConstants.FD_RENDERSCRIPT),
                        proguardRules = File(folder, SdkConstants.FN_PROGUARD_TXT),
                        externalAnnotations = File(folder, SdkConstants.FN_ANNOTATIONS_ZIP),
                        publicResources = File(folder, SdkConstants.FN_PUBLIC_TXT),
                        symbolFile = File(folder, SdkConstants.FN_RESOURCE_TEXT),

                        lintJar = artifact.publishedLintJar,
                        artifact = artifact.artifactFile!!,
                        srcJar = additionalArtifacts.source,
                        docJar = additionalArtifacts.javadoc,
                        samplesJar = additionalArtifacts.sample,
                    )
                }

                ResolvedArtifact.DependencyType.ANDROID_SANDBOX_SDK -> {
                    val folder = artifact.extractedFolder
                    if (folder != null) {
                        LibraryImpl.createJavaLibrary(
                            stringCache.cacheString(libraryInfo.computeKey()),
                            libraryInfo,
                            folder,
                            additionalArtifacts.source,
                            additionalArtifacts.javadoc,
                            additionalArtifacts.sample,
                        )
                    } else {
                        // If privacy sandbox isn't enabled treat the library as an empty artifact
                        // (The build will fail with an explanation)
                        LibraryImpl.createNoArtifactFileLibrary(
                            stringCache.cacheString(libraryInfo.computeKey()),
                            libraryInfo,
                        )
                    }
                }

                ResolvedArtifact.DependencyType.JAVA -> {
                    LibraryImpl.createJavaLibrary(
                        stringCache.cacheString(libraryInfo.computeKey()),
                        libraryInfo,
                        artifact.artifactFile!!,
                        additionalArtifacts.source,
                        additionalArtifacts.javadoc,
                        additionalArtifacts.sample
                    )
                }

                ResolvedArtifact.DependencyType.RELOCATED_ARTIFACT -> {
                    LibraryImpl.createRelocatedLibrary(
                        stringCache.cacheString(libraryInfo.computeKey()),
                        libraryInfo,
                    )
                }

                ResolvedArtifact.DependencyType.NO_ARTIFACT_FILE -> {
                    LibraryImpl.createNoArtifactFileLibrary(
                        stringCache.cacheString(libraryInfo.computeKey()),
                        libraryInfo,
                    )
                }
            }
        } else if (artifact.dependencyType == ResolvedArtifact.DependencyType.ANDROID_SANDBOX_SDK) {
            // This is a hack to treat internal ASAR dependencies as external as we need to publish
            // the jar as a dependency that Studio needs to resolve SDK classes
            val folder = artifact.extractedFolder
                ?: throw RuntimeException("Null extracted folder for artifact: $artifact")
            val libraryInfo = LibraryInfoImpl(
                buildType = null,
                productFlavors = mapOf(),
                attributes = mapOf(),
                capabilities = listOf(),
                group = stringCache.cacheString(LOCAL_ASAR_GROUPID),
                name = stringCache.cacheString(folder.absolutePath),
                version = stringCache.cacheString("unspecified"),
                isTestFixtures = false
            )
            LibraryImpl.createJavaLibrary(
                stringCache.cacheString(libraryInfo.computeKey()),
                libraryInfo,
                folder,
                additionalArtifacts.source,
                additionalArtifacts.javadoc,
                additionalArtifacts.sample,
            )
        } else if (artifact.dependencyType == ResolvedArtifact.DependencyType.NO_ARTIFACT_FILE) {
            // Handle projects that have no artifacts (e.g. java-platform)
            val projectInfo = getProjectInfo(artifact.variant)
            LibraryImpl.createNoArtifactProjectLibrary(
                stringCache.cacheString(projectInfo.computeKey()),
                projectInfo
            )
        } else {
            // In general, we do not need to provide the artifact for project dependencies
            // because on the IDE side we're just going to do a project to project dependency link.
            // However, there are cases where consumed Java projects have different published
            // artifacts. This could be done properly via attributes and/or capabilities
            // (e.g. the test fixtures in a java-library project), but it could also be done
            // by simply creating a custom artifact tied to a custom configuration. This could be
            // either tied to a source set or just a random jar task.
            // In order to resolve this on the IDE, we need to pass some other info so that the
            // IDE can match this dependency to the proper sourceset of the java project (basically
            // something similar to attributes/capabilities). Unfortunately the dependency
            // model coming from Gradle does not contain anything useful.
            // Because of this we rely on passing the artifact itself, so that we can match it
            // with the model of the java project which itself has the artifact path for all its
            // "custom" variants (that don't use attributes/capabilities).
            val projectInfo = getProjectInfo(artifact.variant)
            val artifactFile = artifact.artifactFile?.takeIf {
                it.extension == "jar"
            }

            LibraryImpl.createProjectLibrary(
                stringCache.cacheString(projectInfo.computeKey()),
                projectInfo,
                artifactFile = artifactFile,
                // For sync model building, this will be null and will be available through project
                // models, but for lint model building, it is still used
                lintJar = artifact.publishedLintJar
            )
        }
    }

    private fun getBuildType(variant: ResolvedVariantResult): String? =
        variant.attributes.keySet()
            .firstOrNull { it.name == BuildTypeAttr.ATTRIBUTE.name }
            ?.let { buildType ->
                variant.attributes.getAttribute(buildType)
                    ?.toString()
                    ?.let { stringCache.cacheString(it) }
            }

    private fun getProductFlavors(variant: ResolvedVariantResult): Map<String, String> =
        variant.attributes.keySet()
            .filter { it.name.startsWith(productFlavorAttrPrefix) }
            .associateBy(
                { stringCache.cacheString(it.name.substring(productFlavorAttrPrefix.length)) },
                { stringCache.cacheString(variant.attributes.getAttribute(it).toString()) })

    private fun getAttributeMap(
        variant: ResolvedVariantResult,
        productFlavors: Map<String, String>
    ): Map<String, String> =
        variant.attributes.keySet()
            .asSequence()
            .filter {
                // Build types and product flavors are handled explicitly,
                // So filter them out from the generic attribute map
                it.name != BuildTypeAttr.ATTRIBUTE.name &&
                        !it.name.startsWith(productFlavorAttrPrefix) &&
                        !productFlavors.containsKey(it.name) // Also exclude the un-prefixed product flavor attributes.
            }.mapNotNull { key ->
                val attr = variant.attributes.getAttribute(key)
                attr?.let { stringCache.cacheString(key.name) to stringCache.cacheString(it.toString()) }
            }
            // this is residual information from the way we combine the dependency graph and
            // the artifacts queried via ArtifactCollection, and later always include the
            // type of the artifact that are queried. But we really don't want/need these, as
            // we are just looking at representing the dependency node itself, not one of its
            // artifacts.
            .filter { it.first != "artifactType" }
            .toMap()

    private fun getCapabilityList(variant: ResolvedVariantResult): List<String> =
        variant.capabilities.map { stringCache.cacheString("${it.group}:${it.name}:${it.version}") }
}

private val productFlavorAttrPrefix: String = ProductFlavorAttr.of("").name
