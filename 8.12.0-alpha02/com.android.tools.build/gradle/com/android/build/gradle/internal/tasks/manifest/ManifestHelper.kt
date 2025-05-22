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

package com.android.build.gradle.internal.tasks.manifest

import com.android.SdkConstants.DOT_XML
import com.android.ide.common.blame.SourceFile
import com.android.ide.common.blame.SourceFilePosition
import com.android.ide.common.blame.SourcePosition
import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.ManifestProvider
import com.android.manifmerger.ManifestSystemProperty
import com.android.manifmerger.MergingReport
import com.android.manifmerger.MergingReport.MergedManifestKind.AAPT_SAFE
import com.android.manifmerger.MergingReport.MergedManifestKind.MERGED
import com.android.utils.ILogger
import com.google.common.base.Charsets
import com.google.common.io.Files
import java.io.File
import java.io.IOException

/**
 * Invoke the Manifest Merger version 2.
 *
 * @param packageOverride the value used for the merged manifest's package attribute, which is
 *                        the applicationId for apps and the namespace for libraries.
 * @param namespace the namespace, used to create or shorten fully qualified class names
 * @param extractNativeLibs the value to assign to the injected android:extractNativeLibs attribute.
 *                          The attribute will not be injected if null. Even if not null, the
 *                          attribute will not be modified if it's already present in the main
 *                          manifest or an overlay manifest.
 */
fun mergeManifests(
    mainManifest: File,
    manifestOverlays: List<File>,
    dependencies: List<ManifestProvider>,
    navigationJsons: Collection<File>,
    featureName: String?,
    packageOverride: String?,
    namespace: String,
    profileable: Boolean,
    versionCode: Int?,
    versionName: String?,
    minSdkVersion: String?,
    targetSdkVersion: String?,
    maxSdkVersion: Int?,
    testOnly: Boolean,
    extractNativeLibs: Boolean?,
    outMergedManifestLocation: String?,
    outAaptSafeManifestLocation: String?,
    mergeType: ManifestMerger2.MergeType,
    placeHolders: Map<String, Any>,
    optionalFeatures: Collection<ManifestMerger2.Invoker.Feature>,
    dependencyFeatureNames: Collection<String>,
    generatedLocaleConfigAttribute: String?,
    reportFile: File?,
    logger: ILogger,
    checkIfPackageInMainManifest: Boolean = true,
    compileSdk: Int? = null
): MergingReport {

    try {

        val manifestMergerInvoker = ManifestMerger2.newMerger(mainManifest, logger, mergeType)
            .setPlaceHolderValues(placeHolders)
            .addFlavorAndBuildTypeManifests(*manifestOverlays.toTypedArray())
            .addManifestProviders(dependencies)
            .addNavigationJsons(navigationJsons)
            .withFeatures(*optionalFeatures.toTypedArray())
            .setMergeReportFile(reportFile)
            .setFeatureName(featureName)
            .addDependencyFeatureNames(dependencyFeatureNames)
            .setNamespace(namespace)

        if (checkIfPackageInMainManifest) {
            manifestMergerInvoker.withFeatures(
                ManifestMerger2.Invoker.Feature.CHECK_IF_PACKAGE_IN_MAIN_MANIFEST
            )
        }

        val isAppMerge = mergeType == ManifestMerger2.MergeType.APPLICATION
        val injectProfileable = isAppMerge && profileable

        if (isAppMerge) {
            manifestMergerInvoker.withFeatures(ManifestMerger2.Invoker.Feature.REMOVE_TOOLS_DECLARATIONS)
        }

        if (outAaptSafeManifestLocation != null) {
            manifestMergerInvoker.withFeatures(ManifestMerger2.Invoker.Feature.MAKE_AAPT_SAFE)
        }

        manifestMergerInvoker.setGeneratedLocaleConfigAttribute(generatedLocaleConfigAttribute)

        setInjectableValues(
            manifestMergerInvoker,
            packageOverride,
            versionCode,
            versionName,
            minSdkVersion,
            targetSdkVersion,
            maxSdkVersion,
            injectProfileable,
            testOnly,
            compileSdk,
            extractNativeLibs
        )

        val mergingReport = manifestMergerInvoker.merge()
        logger.verbose("Merging result: %1\$s", mergingReport.result)
        if (mergingReport.result == MergingReport.Result.ERROR) {
            mergingReport.log(logger)
            throw RuntimeException(mergingReport.reportString)
        }
        if (mergingReport.result == MergingReport.Result.WARNING) {
            mergingReport.log(logger)
        }

        val annotatedDocument =
            mergingReport.getMergedDocument(MergingReport.MergedManifestKind.BLAME)
        if (annotatedDocument != null) {
            logger.verbose(annotatedDocument)
        }
        logger.verbose("Merged manifest saved to $outMergedManifestLocation")
        if (outMergedManifestLocation != null) {
            save(mergingReport.getMergedDocument(MergingReport.MergedManifestKind.MERGED),
                File(outMergedManifestLocation))
        }

        if (outAaptSafeManifestLocation != null) {
            save(
                mergingReport.getMergedDocument(
                    if (mergingReport.isAaptSafeManifestUnchanged) MERGED else AAPT_SAFE
                ),
                File(outAaptSafeManifestLocation)
            )
        }
        return mergingReport
    } catch (e: ManifestMerger2.MergeFailureException) {
        // TODO: unacceptable.
        throw RuntimeException(e)
    }

}

/**
 * Finds the original source of the file position pointing to a merged manifest file.
 *
 * The manifest merge blame file is formatted as follow
 * <lineNumber>--><filePath>:<startLine>:<startColumn>-<endLine>:<endColumn>
 */
fun findOriginalManifestFilePosition(
    manifestMergeBlameContents: List<String>,
    mergedFilePosition: SourceFilePosition
): SourceFilePosition {
    if (mergedFilePosition.file == SourceFile.UNKNOWN ||
        mergedFilePosition.file.sourceFile?.absolutePath?.let {
            it.contains("merged_manifests") || it.contains("packaged_manifests")
        } == false
    ) {
        return mergedFilePosition
    }
    try {
        val linePrefix = (mergedFilePosition.position.startLine + 1).toString() + "-->"
        manifestMergeBlameContents.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith(linePrefix)) {
                var position = trimmed.substring(linePrefix.length)
                if (position.startsWith("[")) {
                    val closingIndex = position.indexOf("] ")
                    if (closingIndex >= 0) {
                        position = position.substring(closingIndex + 2)
                    }
                }
                val index = position.indexOf(DOT_XML)
                if (index != -1) {
                    val file = position.substring(0, index + DOT_XML.length)
                    return if (file != position) {
                        val sourcePosition = position.substring(index + DOT_XML.length + 1)
                        SourceFilePosition(File(file), SourcePosition.fromString(sourcePosition))
                    } else {
                        SourceFilePosition(File(file), SourcePosition.UNKNOWN)
                    }
                }
            }
        }
    } catch (e: Exception) {
        return mergedFilePosition
    }
    return mergedFilePosition
}

/**
 * Sets the [ManifestSystemProperty] that can be injected
 * in the manifest file.
 */
private fun setInjectableValues(
    invoker: ManifestMerger2.Invoker,
    packageOverride: String?,
    versionCode: Int?,
    versionName: String?,
    minSdkVersion: String?,
    targetSdkVersion: String?,
    maxSdkVersion: Int?,
    profileable: Boolean,
    testOnly: Boolean,
    compileSdk: Int?,
    extractNativeLibs: Boolean?
) {

    if (packageOverride != null && packageOverride.isNotEmpty()) {
        invoker.setOverride(ManifestSystemProperty.Document.PACKAGE, packageOverride)
    }

    versionCode?.let {
        if (it > 0) {
            invoker.setOverride(ManifestSystemProperty.Manifest.VERSION_CODE, it.toString())
        }
    }

    if (versionName != null && versionName.isNotEmpty()) {
        invoker.setOverride(ManifestSystemProperty.Manifest.VERSION_NAME, versionName)
    }
    if (minSdkVersion != null && minSdkVersion.isNotEmpty()) {
        invoker.setOverride(ManifestSystemProperty.UsesSdk.MIN_SDK_VERSION, minSdkVersion)
    }
    if (targetSdkVersion != null && targetSdkVersion.isNotEmpty()) {
        invoker.setOverride(ManifestSystemProperty.UsesSdk.TARGET_SDK_VERSION, targetSdkVersion)
    }
    if (maxSdkVersion != null) {
        invoker.setOverride(ManifestSystemProperty.UsesSdk.MAX_SDK_VERSION, maxSdkVersion.toString())
    }
    if (profileable) {
        invoker.setOverride(ManifestSystemProperty.Profileable.SHELL, "true")
        // API 29 doesn't support 'android:enabled' for the profileable tag.
        if (compileSdk != null && compileSdk >= 30) {
            invoker.setOverride(ManifestSystemProperty.Profileable.ENABLED, "true")
        }
    }
    if (testOnly) {
        invoker.setOverride(ManifestSystemProperty.Application.TEST_ONLY, "true")
    }
    if (extractNativeLibs != null) {
        // android:extractNativeLibs unrecognized if using compile SDK < 23.
        if (compileSdk == null || compileSdk >= 23) {
            invoker.setOverride(
                ManifestSystemProperty.Application.EXTRACT_NATIVE_LIBS,
                extractNativeLibs.toString()
            )
        }
    }
}

/**
 * Saves the [com.android.manifmerger.XmlDocument] to a file in UTF-8 encoding.
 * @param xmlDocument xml document to save.
 * @param out file to save to.
 */
private fun save(xmlDocument: String?, out: File) {
    try {
        Files.createParentDirs(out)
        Files.asCharSink(out, Charsets.UTF_8).write(xmlDocument!!)
    } catch (e: IOException) {
        throw RuntimeException(e)
    }

}

