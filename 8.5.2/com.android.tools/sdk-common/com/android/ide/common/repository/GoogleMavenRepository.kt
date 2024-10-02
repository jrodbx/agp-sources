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
import com.android.ide.common.gradle.Dependency
import com.android.ide.common.gradle.RichVersion
import com.android.ide.common.gradle.Version
import com.android.ide.common.gradle.VersionRange
import com.google.common.collect.Maps
import com.google.common.collect.Range
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

    /** Maximum allowed age of cached data; default is 1 day */
    cacheExpiryHours: Int = TimeUnit.DAYS.toHours(1).toInt(),

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

    fun hasGroupId(groupId: String): Boolean {
        return getPackageMap()[groupId] != null
    }

    fun findVersion(dependency: Dependency, filter: Predicate<Version>? = null): Version? =
        findVersion(dependency, filter, dependency.explicitlyIncludesPreview)

    fun findVersion(
        dependency: Dependency,
        predicate: Predicate<Version>?,
        allowPreview: Boolean = false
    ): Version? {
        fun predicate(version: Version) = predicate?.test(version) ?: true
        val group = dependency.group ?: return null
        val filter = when {
            dependency.hasExplicitDistinctUpperBound -> {
                { v: Version -> predicate(v) && dependency.version?.contains(v) ?: true }
            }
            else -> {
                { v: Version -> predicate(v) && dependency.version?.accepts(v) ?: true }
            }
        }

        if (group == "androidx.work") {
            // NB not VersionRange.parse("[2.7,2.7.0)")
            val range = Range.closedOpen(Version.parse("2.7"), Version.parse("2.7.0"))
            val excludedRange = VersionRange(range)
            // TODO: actually I'd like something like
            //    dependency.version?.intersection(VersionRange([2.7,2.7.0)).isNotEmpty?
            //  but that's currently a bit tricky to express.  We know that versions have the form
            //  2.7.0-alpha01 so we can afford to be a bit loose here.
            if (dependency.version?.accepts(Version.prefixInfimum("2.7.0")) == true) {
                val artifactInfo = findArtifact(group, dependency.name) ?: return null
                val snapshotFilter = getSnapshotVersionFilter(null)
                artifactInfo.getVersions()
                    .filter { v -> !excludedRange.contains(v) && filter(v) }
                    .filter { allowPreview || !it.isPreview }
                    .filter { snapshotFilter(it) }
                    .maxOrNull()
                    ?.let { return it }
            }
        }

        val compositeFilter = getSnapshotVersionFilter(filter)
        return findVersion(group, dependency.name, compositeFilter, allowPreview)
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
    ): List<Dependency> {
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

        private val dependencyInfo by lazy { HashMap<Version, List<Dependency>>() }

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
        ): List<Dependency> {
            return dependencyInfo[version] ?: loadCompileDependencies(version, packageInfo)
        }

        private fun loadCompileDependencies(
            version: Version,
            packageInfo: PackageInfo
        ): List<Dependency> {
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

        fun loadCompileDependencies(id: String, version: Version): List<Dependency> {
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
        ): List<Dependency> {

            return try {
                val dependencies = mutableListOf<Dependency>()
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

        private fun readCompileDependency(parser: KXmlParser): Dependency? {
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
                                Dependency(groupId, artifactId, RichVersion.parse(version))
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

