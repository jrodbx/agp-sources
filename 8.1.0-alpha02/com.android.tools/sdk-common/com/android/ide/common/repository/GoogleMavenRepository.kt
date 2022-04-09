/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.ide.common.repository

import com.android.SdkConstants
import com.android.ide.common.gradle.Version
import com.google.common.collect.Maps
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.util.HashMap
import java.util.concurrent.TimeUnit
import java.util.function.Predicate

const val GMAVEN_TEST_BASE_URL_ENV_VAR = "GMAVEN_TEST_BASE_URL"
const val DEFAULT_GMAVEN_URL = "https://maven.google.com/"
@JvmField
val GMAVEN_BASE_URL = System.getenv(GMAVEN_TEST_BASE_URL_ENV_VAR) ?: DEFAULT_GMAVEN_URL

/**
 * Provides information about the artifacts and versions available on maven.google.com
 */
abstract class GoogleMavenRepository @JvmOverloads constructor(
    /** Location to search for cached repository content files */
    cacheDir: Path? = null,

    /**
     * Number of milliseconds to wait until timing out attempting to access the remote
     * repository
     */
    networkTimeoutMs: Int = 3000,

    /** Maximum allowed age of cached data; default is 7 days */
    cacheExpiryHours: Int = TimeUnit.DAYS.toHours(7).toInt(),

    /** If false, this repository won't make network requests */
    useNetwork: Boolean = true
) : NetworkCache(
    GMAVEN_BASE_URL, MAVEN_GOOGLE_CACHE_DIR_KEY, cacheDir, networkTimeoutMs,
    cacheExpiryHours, useNetwork
) {

    companion object {
        /** Key used in cache directories to locate the maven.google.com network cache */
        const val MAVEN_GOOGLE_CACHE_DIR_KEY = "maven.google"
    }

    private var packageMap: MutableMap<String, PackageInfo>? = null

    fun findVersion(dependency: GradleCoordinate, filter: Predicate<Version>? = null):
        Version? = findVersion(dependency, filter, dependency.isPreview)

    fun findVersion(
        dependency: GradleCoordinate,
        predicate: Predicate<Version>?,
        allowPreview: Boolean = false
    ): Version? {
        val groupId = dependency.groupId
        val artifactId = dependency.artifactId
        val version = dependency.lowerBoundVersion
        val filter = when {
            dependency.acceptsGreaterRevisions() -> {
                val prefix = dependency.revision.trimEnd('+')
                if (predicate != null) {
                    { v: Version -> predicate.test(v) && v.toString().startsWith(prefix) }
                } else {
                    { v: Version -> v.toString().startsWith(prefix) }
                }
            }
            predicate != null -> {
                { v: Version -> predicate.test(v) }
            }
            else -> {
                null
            }
        }

        // Temporary special casing for AndroidX: don't offer upgrades from 2.6 to 2.7 previews
        if (groupId == "androidx.work") {
            if (version < Version.prefixInfimum("2.7")) {
                val artifactInfo = findArtifact(groupId, artifactId) ?: return null
                val snapshotFilter = getSnapshotVersionFilter(null)
                artifactInfo.getVersions()
                    .filter { v ->
                        ((v < Version.prefixInfimum("2") || v >= Version.prefixInfimum("3")) ||
                                (v < Version.prefixInfimum("2.7") ||
                                        (v > Version.prefixInfimum("2.8") || !v.isPreview))) &&
                                (filter == null || filter(v))
                    }
                    .filter { allowPreview || !it.isPreview }
                    .filter { snapshotFilter(it) }
                    .maxOrNull()
                    ?.let { return it }
            }
        }

        val compositeFilter = getSnapshotVersionFilter(filter)
        return findVersion(groupId, artifactId, compositeFilter, allowPreview)
    }

    // In addition to the optional filter, add in filtering to disable suggestions to
    // snapshot versions.
    private fun getSnapshotVersionFilter(filter: ((Version) -> Boolean)?): (Version) -> Boolean =
        { candidate -> !candidate.isSnapshot && (filter == null || filter(candidate)) }

    fun findVersion(
        groupId: String,
        artifactId: String,
        filter: Predicate<Version>?,
        allowPreview: Boolean = false
    ): Version? =
        findVersion(groupId, artifactId, { filter?.test(it) != false }, allowPreview)

    fun findVersion(
        groupId: String,
        artifactId: String,
        filter: ((Version) -> Boolean)? = null,
        allowPreview: Boolean = false
    ): Version? {
        val artifactInfo = findArtifact(groupId, artifactId) ?: return null
        return artifactInfo.findVersion(filter, allowPreview)
    }

    fun getGroups(): Set<String> = getPackageMap().keys.toSet()

    fun getArtifacts(groupId: String): Set<String> = getPackageMap()[groupId]?.artifacts().orEmpty()

    fun getVersions(groupId: String, artifactId: String): Set<Version> {
        val artifactInfo = findArtifact(groupId, artifactId) ?: return emptySet()
        return artifactInfo.getVersions().toSet()
    }

    fun getAgpVersions(): Set<AgpVersion> {
        val artifactInfo = findArtifact("com.android.tools.build", "gradle") ?: return emptySet()
        return artifactInfo.getAgpVersions().toSet()
    }

    fun findCompileDependencies(
        groupId: String,
        artifactId: String,
        version: Version
    ): List<GradleCoordinate> {
        val packageInfo = getPackageMap()[groupId] ?: return emptyList()
        val artifactInfo = packageInfo.findArtifact(artifactId)
        return artifactInfo?.findCompileDependencies(version, packageInfo) ?: emptyList()
    }

    private fun findArtifact(groupId: String, artifactId: String): ArtifactInfo? {
        val packageInfo = getPackageMap()[groupId] ?: return null
        return packageInfo.findArtifact(artifactId)
    }

    private fun getPackageMap(): MutableMap<String, PackageInfo> {
        if (packageMap == null) {
            val map = Maps.newHashMapWithExpectedSize<String, PackageInfo>(28)
            findData("master-index.xml")?.use { readMasterIndex(it, map) }
            packageMap = map
        }

        return packageMap!!
    }

    private data class ArtifactInfo(val id: String, val versions: String) {

        private val dependencyInfo by lazy { HashMap<Version, List<GradleCoordinate>>() }

        fun getVersions(): Sequence<Version> =
            versions.splitToSequence(",")
                .map { Version.parse(it) }

        fun getAgpVersions(): Sequence<AgpVersion> =
            versions.splitToSequence(",")
                .map { AgpVersion.tryParse(it) }
                .filterNotNull()

        fun findVersion(filter: ((Version) -> Boolean)?, allowPreview: Boolean = false): Version? =
            getVersions()
                .filter { filter == null || filter(it) }
                .filter { allowPreview || !it.isPreview }
                .maxOrNull()

        fun findCompileDependencies(
            version: Version,
            packageInfo: PackageInfo
        ): List<GradleCoordinate> {
            return dependencyInfo[version] ?: loadCompileDependencies(version, packageInfo)
        }

        private fun loadCompileDependencies(
            version: Version,
            packageInfo: PackageInfo
        ): List<GradleCoordinate> {
            if (findVersion({ it == version }, true) == null) {
                // Do not attempt to load a pom file that is known not to exist
                return emptyList()
            }
            val dependencies = packageInfo.loadCompileDependencies(id, version)
            dependencyInfo[version] = dependencies
            return dependencies
        }
    }

    override fun readDefaultData(relative: String): InputStream? {
        return GoogleMavenRepository::class.java.getResourceAsStream("/versions-offline/$relative")
    }

    private fun readMasterIndex(stream: InputStream, map: MutableMap<String, PackageInfo>) =
        try {
            stream.use {
                val parser = KXmlParser()
                parser.setInput(it, SdkConstants.UTF_8)
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    val eventType = parser.eventType
                    if (eventType == XmlPullParser.END_TAG && parser.depth > 1) {
                        val tag = parser.name
                        val packageInfo = PackageInfo(tag)
                        map[tag] = packageInfo
                    } else if (eventType != XmlPullParser.START_TAG) {
                        continue
                    }
                }
            }
        } catch (e: XmlPullParserException) {
            // Malformed XML. Most likely the file we received was not the XML file
            // but some sort of network portal redirect HTML page. Gracefully degrade.
        } catch (e: IOException) {
            error(e, null)
        }

    private inner class PackageInfo(val pkg: String) {
        private val artifacts: Map<String, ArtifactInfo> by lazy {
            val map = HashMap<String, ArtifactInfo>()
            initializeIndex(map)
            map
        }

        fun artifacts(): Set<String> = artifacts.values.map { it.id }.toSet()

        fun findArtifact(id: String): ArtifactInfo? = artifacts[id]

        fun loadCompileDependencies(id: String, version: Version): List<GradleCoordinate> {
            val file = "${pkg.replace('.', '/')}/$id/$version/$id-$version.pom"
            val stream = findData(file)
            return stream?.use { readCompileDependenciesFromPomFile(stream, file) } ?: emptyList()
        }

        private fun initializeIndex(map: MutableMap<String, ArtifactInfo>) {
            val stream = findData("${pkg.replace('.', '/')}/group-index.xml")
            stream?.use { readGroupData(stream, map) }
        }

        private fun readGroupData(stream: InputStream, map: MutableMap<String, ArtifactInfo>) =
            try {
                val parser = KXmlParser()
                parser.setInput(stream, SdkConstants.UTF_8)
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    val eventType = parser.eventType
                    if (eventType == XmlPullParser.START_TAG) {
                        val artifactId = parser.name
                        val versions = parser.getAttributeValue(null, "versions")
                        if (versions != null) {
                            val artifactInfo = ArtifactInfo(artifactId, versions)
                            map[artifactId] = artifactInfo
                        }
                    }
                }
            } catch (e: XmlPullParserException) {
                // Malformed XML. Most likely the file we received was not the XML file
                // but some sort of network portal redirect HTML page. Gracefully degrade.
            } catch (e: Exception) {
                error(e, null)
            }

        private fun readCompileDependenciesFromPomFile(
            stream: InputStream,
            file: String
        ): List<GradleCoordinate> {

            return try {
                val dependencies = mutableListOf<GradleCoordinate>()
                val parser = KXmlParser()
                parser.setInput(stream, SdkConstants.UTF_8)
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    val eventType = parser.eventType
                    if (eventType == XmlPullParser.START_TAG && parser.name == "dependency") {
                        val dependency = readCompileDependency(parser)
                        if (dependency != null) {
                            dependencies.add(dependency)
                        }
                    }
                }
                dependencies
            } catch (e: XmlPullParserException) {
                // Malformed XML. Most likely the file we received was not the XML file
                // but some sort of network portal redirect HTML page. Gracefully degrade.
                emptyList()
            } catch (e: Exception) {
                error(e, "Problem reading POM file: $file")
                emptyList()
            }
        }

        private fun readCompileDependency(parser: KXmlParser): GradleCoordinate? {
            var groupId = ""
            var artifactId = ""
            var version = ""
            var scope = ""
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG ->
                        when (parser.name) {
                            "groupId" -> groupId = parser.nextText()
                            "artifactId" -> artifactId = parser.nextText()
                            "version" -> version = parser.nextText()
                            "scope" -> scope = parser.nextText()
                        }
                    XmlPullParser.END_TAG ->
                        if (parser.name == "dependency") {
                            check(groupId, "groupId")
                            check(artifactId, "artifactId")
                            check(version, "version")
                            return if (scope == "compile")
                                GradleCoordinate(groupId, artifactId, version)
                            else
                                null
                        }
                }
            }
            throw RuntimeException("Unexpected end of file")
        }

        private fun check(item: String, name: String) {
            if (item.isEmpty()) {
                throw RuntimeException("Missing $name field")
            }
        }
    }
}
