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
) : NetworkCache(GMAVEN_BASE_URL, cacheDir, networkTimeoutMs, cacheExpiryHours, useNetwork) {

    private var packageMap: MutableMap<String, PackageInfo>? = null

    fun hasGroupId(groupId: String): Boolean {
        return getPackageMap()[groupId] != null
    }

    fun findVersion(
        dependency: Dependency,
        predicate: Predicate<Version>?,
        allowPreview: Boolean = false
    ): Version? {
        val filter = getDependencyFilter(dependency, predicate)
        val group = dependency.group ?: return null
        if (group == "androidx.work") {
            handleWorkManager(dependency, allowPreview, filter)?.let { return it }
        }
        return findVersion(group, dependency.name, filter, allowPreview)
    }

    private fun getDependencyFilter(
        dependency: Dependency,
        predicate: Predicate<Version>?
    ): (Version) -> Boolean {
        fun predicate(version: Version) = predicate?.test(version) ?: true
        val filter = when {
            dependency.hasExplicitDistinctUpperBound -> {
                { v: Version -> predicate(v) && dependency.version?.contains(v) ?: true }
            }

            else -> {
                { v: Version -> predicate(v) && dependency.version?.accepts(v) ?: true }
            }
        }
        return getSnapshotVersionFilter(filter)
    }

    private fun handleWorkManager(dependency: Dependency, allowPreview: Boolean, filter: (Version) -> Boolean): Version? {
        // NB not VersionRange.parse("[2.7,2.7.0)")
        val range = Range.closedOpen(Version.parse("2.7"), Version.parse("2.7.0"))
        val excludedRange = VersionRange(range)
        // TODO: actually I'd like something like
        //    dependency.version?.intersection(VersionRange([2.7,2.7.0)).isNotEmpty?
        //  but that's currently a bit tricky to express.  We know that versions have the form
        //  2.7.0-alpha01 so we can afford to be a bit loose here.
        if (dependency.version?.accepts(Version.prefixInfimum("2.7.0")) == true) {
            val artifactInfo = findArtifact("androidx.work", dependency.name) ?: return null
            val snapshotFilter = getSnapshotVersionFilter(null)
            artifactInfo.getVersions()
                .filter { v -> !excludedRange.contains(v) && filter(v) }
                .filter { allowPreview || !it.isPreview }
                .filter { snapshotFilter(it) }
                .maxOrNull()
                ?.let { return it }
        }

        return null
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

    fun getVersions(
        dependency: Dependency,
         predicate: Predicate<Version>?): Sequence<Version> {
        val groupId = dependency.group ?: return emptySequence()
        val artifactInfo = findArtifact(groupId, dependency.name) ?: return emptySequence()
        val filter = getDependencyFilter(dependency, predicate)
        return artifactInfo.getVersions().filter { filter(it) }
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
        return findDependencies(groupId, artifactId, version, "compile")
    }

    /**
     * Like [findCompileDependencies], but you can specify a specific POM scope
     * to match, such as "runtime"
     */
    fun findDependencies(
        groupId: String,
        artifactId: String,
        version: Version,
        requiredScope: String
    ): List<Dependency> {
        val packageInfo = getPackageMap()[groupId] ?: return emptyList()
        val artifactInfo = packageInfo.findArtifact(artifactId)
        return artifactInfo?.findDependencies(version, packageInfo, requiredScope) ?: emptyList()
    }

    private fun findArtifact(groupId: String, artifactId: String): ArtifactInfo? {
        val packageInfo = getPackageMap()[groupId] ?: return null
        return packageInfo.findArtifact(artifactId)
    }

    protected open fun getPackageMap(): Map<String, PackageInfo> {
        if (packageMap == null) {
            val map = Maps.newHashMapWithExpectedSize<String, PackageInfo>(28)
            findData("master-index.xml")?.use {
                readMasterIndex(it, map) { tag -> PackageInfo(tag) }
            }
            packageMap = map
        }

        return packageMap!!
    }

    protected fun <T : PackageInfo> readMasterIndex(
        stream: InputStream,
        map: MutableMap<String, T>,
        factory: (String) -> T
    ) = try {
        stream.use {
            val parser = KXmlParser()
            parser.setInput(it, SdkConstants.UTF_8)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                val eventType = parser.eventType
                if (eventType == XmlPullParser.END_TAG && parser.depth > 1) {
                    val tag = parser.name
                    val packageInfo = factory(tag)
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

    override fun readDefaultData(relative: String): InputStream? {
        return GoogleMavenRepository::class.java.getResourceAsStream("/versions-offline/$relative")
    }

    protected data class ArtifactInfo(val id: String, val versions: String) {

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

        fun findDependencies(
            version: Version,
            packageInfo: PackageInfo,
            requiredScope: String = "compile"
        ): List<Dependency> {
            return dependencyInfo[version] ?: loadDependencies(version, packageInfo, requiredScope)
        }

        private fun loadDependencies(
            version: Version,
            packageInfo: PackageInfo,
            requiredScope: String,
        ): List<Dependency> {
            if (findVersion({ it == version }, true) == null) {
                // Do not attempt to load a pom file that is known not to exist
                return emptyList()
            }
            val dependencies = packageInfo.loadDependencies(id, version, requiredScope)
            dependencyInfo[version] = dependencies
            return dependencies
        }
    }

    protected open inner class PackageInfo(private val pkg: String) {

        private val artifacts: Map<String, ArtifactInfo> by lazy {
            val map = mutableMapOf<String, ArtifactInfo>()
            val stream = findData("${pkg.replace('.', '/')}/group-index.xml")
            stream?.use { readGroupData(stream, map) }
            return@lazy map
        }

        protected fun readGroupData(stream: InputStream, map: MutableMap<String, ArtifactInfo>) =
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

        open fun artifacts(): Set<String> = artifacts.values.map { it.id }.toSet()

        open fun findArtifact(id: String): ArtifactInfo? = artifacts[id]

        fun loadDependencies(
            id: String,
            version: Version,
            requiredScope: String
        ): List<Dependency> {
            val file = "${pkg.replace('.', '/')}/$id/$version/$id-$version.pom"
            val stream = findData(file)
            return stream?.use { readDependenciesFromPomFile(stream, file, requiredScope) }
                ?: emptyList()
        }

        private fun readDependenciesFromPomFile(
            stream: InputStream,
            file: String,
            requiredScope: String
        ): List<Dependency> {

            return try {
                val boms = mutableListOf<Dependency>()
                val dependencies = mutableListOf<Dependency>()
                val parser = KXmlParser()
                parser.setInput(stream, SdkConstants.UTF_8)
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    val eventType = parser.eventType
                    if (eventType == XmlPullParser.START_TAG) {
                        val name = parser.name
                        if (name == "dependency") {
                            val dependency = readDependency(parser, requiredScope, boms)
                            if (dependency != null) {
                                dependencies.add(dependency)
                            }
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

        private fun readDependency(
            parser: KXmlParser,
            requiredScope: String,
            boms: MutableList<Dependency>
        ): Dependency? {
            var groupId = ""
            var artifactId = ""
            var version = ""
            var scope = ""
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG ->
                        if (parser.depth <= 5) { // avoid <exclusions> group and artifact ids
                            when (parser.name) {
                                "groupId" -> groupId = parser.nextText()
                                "artifactId" -> artifactId = parser.nextText()
                                "version" -> version = parser.nextText()
                                "scope" -> scope = parser.nextText()
                            }
                        }

                    XmlPullParser.END_TAG ->
                        if (parser.name == "dependency") {
                            return if (scope == requiredScope) {
                                check(groupId, "groupId")
                                check(artifactId, "artifactId")
                                if (version.isEmpty()) {
                                    val bom =
                                        boms.firstOrNull {
                                            it.group == groupId && it.name == artifactId
                                        }
                                    version = bom?.version?.lowerBound?.toString() ?: ""
                                }
                                check(version, "version")
                                Dependency(groupId, artifactId, RichVersion.parse(version))
                            } else if (parser.depth == 4 && groupId.isNotEmpty() &&
                                artifactId.isNotEmpty() && version.isNotEmpty()
                            ) {
                                boms.add(
                                    Dependency(
                                        groupId,
                                        artifactId,
                                        RichVersion.parse(version)
                                    )
                                )
                                null
                            } else {
                                null
                            }
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

    companion object {

        /** Key used in cache directories to locate the maven.google.com network cache */
        const val MAVEN_GOOGLE_CACHE_DIR_KEY = "maven.google"
    }
}

