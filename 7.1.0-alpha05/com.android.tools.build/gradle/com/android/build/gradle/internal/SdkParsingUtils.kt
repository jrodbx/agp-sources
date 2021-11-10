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

import com.android.Version
import com.android.builder.core.ToolsRevisionUtils
import com.android.builder.errors.IssueReporter
import com.android.io.CancellableFileIo
import com.android.repository.Revision
import com.android.repository.api.ConsoleProgressIndicator
import com.android.repository.api.LocalPackage
import com.android.repository.api.Repository
import com.android.repository.impl.meta.SchemaModuleUtil
import com.android.sdklib.AndroidTargetHash.getPlatformHashString
import com.android.sdklib.AndroidVersion
import com.android.sdklib.BuildToolInfo
import com.android.sdklib.OptionalLibrary
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.meta.DetailsTypes
import com.android.sdklib.repository.targets.PlatformTarget
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Splitter
import com.google.common.collect.ImmutableList
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Try to load the BuildTool from {@code ${sdkDirectory}/build-tools/${revision}}.
 */
fun buildBuildTools(sdkDirectory: File, revision: Revision): BuildToolInfo? {
    val buildToolsPath = DetailsTypes.getBuildToolsPath(revision).replace(';', '/')
    val buildToolsXml = sdkDirectory.resolve(buildToolsPath).resolve("package.xml")
    val buildToolsPackage = parsePackage(buildToolsXml) ?: return null
    // BuildToolInfo is cheap to build and verify, so we can keep using it.
    val buildToolInfo = BuildToolInfo.fromLocalPackage(buildToolsPackage)
    if (!buildToolInfo.isValid(null)) {
        // The build tools we loaded is missing some expected components.
        return null
    }
    if (!buildToolInfo.revision.equals(revision)) {
        // The path we guessed contained a build-tool but it had a different than the requested one.
        return null
    }
    return buildToolInfo
}

/**
 * Load and parse a {@link LocalPackage} ('package.xml') file from the disk.
 */
fun parsePackage(packageXml: File): LocalPackage? {
    if (!packageXml.exists()) {
        return null
    }

    val progress = ConsoleProgressIndicator()
    lateinit var repo: Repository
    try {
        val parsedObject = SchemaModuleUtil.unmarshal(
            FileInputStream(packageXml),
            AndroidSdkHandler.getAllModules(),
            false,
            progress) ?: return null
        repo = parsedObject as Repository
    } catch (e: IOException) {
        // This shouldn't ever happen
        progress.logError("Error parsing $packageXml.", e)
        return null
    }

    repo.localPackage?.setInstalledPath(packageXml.toPath().parent)
    return repo.localPackage
}

// Additional libraries always comes from addon targets.
/**
 * Return the additional libraries collection fron {@code localPackage}.
 *
 * <p>These are only present if the LocalPackage represents a AddOn platform. Otherwise returns an
 * empty list.
 */
fun parseAdditionalLibraries(localPackage: LocalPackage): List<OptionalLibrary> {
    if (localPackage.typeDetails !is DetailsTypes.AddonDetailsType) {
        return ImmutableList.of()
    }

    val details = localPackage.typeDetails as DetailsTypes.AddonDetailsType

    // Looks strange, but getLibrary inside getLibraries returns a list of libraries...
    return details.libraries?.let { libraries ->
        libraries.library
            .filter { it.localJarPath != null }
            .onEach { it.setPackagePath(localPackage.location) }
    } ?: emptyList()
}

// Optional libraries always comes from base/platform targets.
/**
 * Load the optional libraries for {@code localPackage}.
 *
 * <p>If {@code localPackage} is a Platform, then the list of optional packages is located in the
 * {@code optional/optional.json} file under it, like $SDK/platforms/android-28/optional/...
 */
fun parseOptionalLibraries(localPackage: LocalPackage): List<OptionalLibrary> {
    val optionalJson = localPackage.location.resolve("optional/optional.json")
    if (CancellableFileIo.isRegularFile(optionalJson)) {
        return PlatformTarget.getLibsFromJson(optionalJson)
    }
    return emptyList()
}

fun warnIfCompileSdkTooNew(version: AndroidVersion, issueReporter: IssueReporter, suppressWarningIfTooNewForVersions: String?) {
    warnIfCompileSdkTooNew(
        version = version,
        issueReporter = issueReporter,
        maxVersion = ToolsRevisionUtils.MAX_RECOMMENDED_COMPILE_SDK_VERSION,
        androidGradlePluginVersion = Version.ANDROID_GRADLE_PLUGIN_VERSION,
        suppressWarningIfTooNewForVersions = suppressWarningIfTooNewForVersions,
    )
}

@VisibleForTesting
internal fun warnIfCompileSdkTooNew(
    version: AndroidVersion,
    issueReporter: IssueReporter,
    maxVersion: AndroidVersion,
    androidGradlePluginVersion: String,
    suppressWarningIfTooNewForVersions: String? = null,
    ) {
    if (version <= maxVersion) return
    val suppressName = version.apiString
    val suppressSet = suppressWarningIfTooNewForVersions?.splitToSequence(",")?.filter(String::isNotEmpty)?.toSet() ?: setOf()
    if (suppressSet.contains(suppressName)) return
    issueReporter.reportWarning(
        IssueReporter.Type.COMPILE_SDK_VERSION_TOO_HIGH,
        "We recommend using a newer Android Gradle plugin to use ${version.asDsl()}\n" +
                "\n" +
                "This Android Gradle plugin ($androidGradlePluginVersion) " +
                "was tested up to ${AndroidVersion(maxVersion.apiLevel).asDsl()}" +
                (if (maxVersion.isPreview) " (and ${maxVersion.asDsl()})" else "") + "\n" +
                "\n" +
                "This warning can be suppressed by " +
                (if (suppressWarningIfTooNewForVersions.isNullOrEmpty()) {
                    "adding\n    ${com.android.build.gradle.options.StringOption.SUPPRESS_UNSUPPORTED_COMPILE_SDK.propertyName}=$suppressName"
                } else {
                    "updating\n    ${com.android.build.gradle.options.StringOption.SUPPRESS_UNSUPPORTED_COMPILE_SDK.propertyName}=${suppressSet.joinToString(",")},$suppressName"
                }) +
                "\n" +
                "to this project's gradle.properties\n" +
                "\n" +
                "The build will continue, but you are strongly encouraged to update your project to\n" +
                "use a newer Android Gradle Plugin that has been tested with ${version.asDsl()}",
        getPlatformHashString(maxVersion)
    )
}

private fun AndroidVersion.asDsl(): String {
    return if (isPreview) """compileSdkPreview = "$codename"""" else "compileSdk = $apiLevel"
}
