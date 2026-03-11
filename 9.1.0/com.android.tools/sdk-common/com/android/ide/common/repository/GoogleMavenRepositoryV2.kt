/*
 * Copyright (C) 2025 The Android Open Source Project
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate
import java.util.zip.GZIPInputStream
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser

/**
 * Represents the repository that provides access to metadata about packages (aka groups), artifacts, versions and their dependencies hosted
 * on "maven.google.com".
 */
interface GoogleMavenRepositoryV2 {

  /**
   * @param compileSdk null if non-Android module, otherwise project's compileSdk. This is used to check compatibility against certain
   *   versions of Android dependencies if they specify a minCompileSdk in the .aar's metadata.
   * @return latest [Version] for a given group, artifact, matching the [Predicate], [compileSdk].
   */
  fun findVersion(
    groupId: String,
    artifactId: String,
    filter: Predicate<Version>?,
    allowPreview: Boolean = false,
    compileSdk: Int? = null,
  ): Version?

  /**
   * @param compileSdk null if non-Android module, otherwise project's compileSdk. This is used to check compatibility against certain
   *   versions of Android dependencies if they specify a minCompileSdk in the .aar's metadata.
   * @return latest [Version] for a given group, artifact, matching the [filter], [compileSdk].
   */
  fun findVersion(
    groupId: String,
    artifactId: String,
    filter: ((Version) -> Boolean)? = null,
    allowPreview: Boolean = false,
    compileSdk: Int? = null,
  ): Version?

  /** Returns [Dependency]s for a given group, artifact and [Version]. */
  fun findDependencies(groupId: String, artifactId: String, version: Version, requiredScope: String): List<Dependency>

  /** Returns compile [Dependency]s for a given group, artifact and [Version]. */
  fun findCompileDependencies(groupId: String, artifactId: String, version: Version): List<Dependency>

  companion object {

    /** Creates an instance of [GoogleMavenRepositoryV2]. */
    fun create(host: GoogleMavenRepositoryV2Host): GoogleMavenRepositoryV2 {
      return GoogleMavenRepositoryV2Impl(host)
    }
  }
}

private class GoogleMavenRepositoryV2Impl : GoogleMavenRepositoryV2 {

  private val cachedVersionDependencies = ConcurrentHashMap<Version, List<Dependency>>()
  private val lorryNetworkCache: NetworkCache
  private val gMavenNetworkCache: NetworkCache
  private val localGroups: Set<Package> by lazy {
    lorryNetworkCache.findData("packages-v0.1.json.gz")?.use {
      val root: Root = Gson().fromJson(GZIPInputStream(it).reader(), object : TypeToken<Root>() {})
      root.packages
    } ?: throw IllegalStateException("Failed to load groups from Lorry")
  }

  constructor(host: GoogleMavenRepositoryV2Host) {
    this.lorryNetworkCache =
      object :
        NetworkCache(
          "https://dl.google.com/android/studio/gmaven/index/release/v0.1/",
          host.cacheDir,
          host.networkTimeoutMs,
          host.cacheExpiryHours,
          host.useNetwork,
        ) {
        override fun readUrlData(url: String, timeout: Int, lastModified: Long): ReadUrlDataResult =
          host.readUrlData(url, timeout, lastModified)

        override fun readDefaultData(relative: String): InputStream? = host.readDefaultData(relative)

        override fun error(throwable: Throwable, message: String?) = host.error(throwable, message)
      }
    this.gMavenNetworkCache =
      object : NetworkCache("https://maven.google.com/", host.cacheDir, host.networkTimeoutMs, host.cacheExpiryHours, host.useNetwork) {
        override fun readUrlData(url: String, timeout: Int, lastModified: Long): ReadUrlDataResult =
          host.readUrlData(url, timeout, lastModified)

        override fun readDefaultData(relative: String): InputStream? = host.readDefaultData(relative)

        override fun error(throwable: Throwable, message: String?) = host.error(throwable, message)
      }
  }

  override fun findVersion(
    groupId: String,
    artifactId: String,
    filter: Predicate<Version>?,
    allowPreview: Boolean,
    compileSdk: Int?,
  ): Version? = findVersion(groupId, artifactId, filter?.let { filter -> filter::test }, allowPreview, compileSdk)

  override fun findVersion(
    groupId: String,
    artifactId: String,
    filter: ((Version) -> Boolean)?,
    allowPreview: Boolean,
    compileSdk: Int?,
  ): Version? {
    val group = localGroups.firstOrNull { it.packageId == groupId } ?: return null
    val artifact = group.artifacts.firstOrNull { it.artifactId == artifactId } ?: return null
    return artifact.versions
      .filter { compileSdk == null || (it.properties?.minCompileSdk?.toInt() ?: 0) <= compileSdk }
      .map { Version.parse(it.version) }
      .filter { (filter == null || filter(it)) && (allowPreview || !it.isPreview) }
      .maxOrNull()
  }

  override fun findDependencies(groupId: String, artifactId: String, version: Version, requiredScope: String): List<Dependency> {
    return cachedVersionDependencies.computeIfAbsent(version) {
      val file = "${groupId.replace('.', '/')}/$artifactId/$version/$artifactId-$version.pom"
      val stream = gMavenNetworkCache.findData(file) ?: return@computeIfAbsent emptyList()
      try {
        val boms = mutableListOf<Dependency>()
        val dependencies = mutableListOf<Dependency>()
        val parser = KXmlParser()
        parser.setInput(stream, SdkConstants.UTF_8)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
          if (parser.eventType != XmlPullParser.START_TAG || parser.name != "dependency") {
            continue
          }
          readDependency(parser, requiredScope, boms)?.let { dep -> dependencies.add(dep) }
        }
        dependencies
      } catch (e: Exception) {
        gMavenNetworkCache.error(e, "Problem reading POM file: $file")
        emptyList()
      } finally {
        stream.close()
      }
    }
  }

  override fun findCompileDependencies(groupId: String, artifactId: String, version: Version): List<Dependency> =
    findDependencies(groupId, artifactId, version, "compile")

  private fun readDependency(parser: KXmlParser, requiredScope: String, boms: MutableList<Dependency>): Dependency? {
    var groupId = ""
    var artifactId = ""
    var version = ""
    var scope = ""
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
      when (parser.eventType) {
        XmlPullParser.START_TAG -> {
          when (parser.name) {
            "groupId" -> groupId = parser.nextText()
            "artifactId" -> artifactId = parser.nextText()
            "version" -> version = parser.nextText()
            "scope" -> scope = parser.nextText()
          }
        }

        XmlPullParser.END_TAG -> {
          if (parser.name != "dependency") {
            continue
          }
          return if (scope == requiredScope) {
            check(groupId, "groupId")
            check(artifactId, "artifactId")
            if (version.isEmpty()) {
              val bom = boms.firstOrNull { it.group == groupId && it.name == artifactId }
              version = bom?.version?.lowerBound?.toString() ?: ""
            }
            check(version, "version")
            Dependency(groupId, artifactId, RichVersion.fromPomVersion(version))
          } else if (parser.depth == 4 && groupId.isNotEmpty() && artifactId.isNotEmpty() && version.isNotEmpty()) {
            boms.add(Dependency(groupId, artifactId, RichVersion.fromPomVersion(version)))
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

data class VersionProperties(
  val minCompileSdk: String? = null,
  val aarMetadataVersion: String? = null,
  val aarFormatVersion: String? = null,
  val minAndroidGradlePluginVersion: String? = null,
  val minCompileSdkExtension: String? = null,
)

private data class GMavenVersion(val version: String, val properties: VersionProperties?)

private data class Artifact(val artifactId: String, val versions: Set<GMavenVersion>)

private data class Package(val packageId: String, val artifacts: Set<Artifact>)

/**
 * Acts as the container for the [Package]s, [Artifact]s and [GMavenVersion]s. The overall structure matches the schema of
 * "packages-v0.1.json.gz" so that it can be deserialized by Gson.
 */
private data class Root(val packages: Set<Package>)
