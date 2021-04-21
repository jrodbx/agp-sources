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
import com.android.utils.ILogger
import com.google.common.base.Charsets
import com.google.common.io.Files
import java.io.File
import java.io.IOException

/** Invoke the Manifest Merger version 2.  */
fun mergeManifests(
    mainManifest: File,
    manifestOverlays: List<File>,
    dependencies: List<ManifestProvider>,
    navigationJsons: Collection<File>,
    featureName: String?,
    packageOverride: String?,
    versionCode: Int?,
    versionName: String?,
    minSdkVersion: String?,
    targetSdkVersion: String?,
    maxSdkVersion: Int?,
    outMergedManifestLocation: String?,
    outAaptSafeManifestLocation: String?,
    mergeType: ManifestMerger2.MergeType,
    placeHolders: Map<String, Any>,
    optionalFeatures: Collection<ManifestMerger2.Invoker.Feature>,
    dependencyFeatureNames: Collection<String>,
    reportFile: File?,
    logger: ILogger
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

        if (mergeType == ManifestMerger2.MergeType.APPLICATION) {
            manifestMergerInvoker.withFeatures(ManifestMerger2.Invoker.Feature.REMOVE_TOOLS_DECLARATIONS)
        }


        if (outAaptSafeManifestLocation != null) {
            manifestMergerInvoker.withFeatures(ManifestMerger2.Invoker.Feature.MAKE_AAPT_SAFE)
        }

        setInjectableValues(
            manifestMergerInvoker,
            packageOverride, versionCode, versionName,
            minSdkVersion, targetSdkVersion, maxSdkVersion
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
                mergingReport.getMergedDocument(MergingReport.MergedManifestKind.AAPT_SAFE),
                File(
                    outAaptSafeManifestLocation
                )
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
    if (mergedFilePosition.file == SourceFile.UNKNOWN || mergedFilePosition.file.sourceFile?.absolutePath?.contains(
            "merged_manifests"
        ) == false
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
    maxSdkVersion: Int?
) {

    if (packageOverride != null && packageOverride.isNotEmpty()) {
        invoker.setOverride(ManifestSystemProperty.PACKAGE, packageOverride)
    }

    versionCode?.let {
        if (it > 0) {
            invoker.setOverride(ManifestSystemProperty.VERSION_CODE, it.toString())
        }
    }

    if (versionName != null && versionName.isNotEmpty()) {
        invoker.setOverride(ManifestSystemProperty.VERSION_NAME, versionName)
    }
    if (minSdkVersion != null && minSdkVersion.isNotEmpty()) {
        invoker.setOverride(ManifestSystemProperty.MIN_SDK_VERSION, minSdkVersion)
    }
    if (targetSdkVersion != null && targetSdkVersion.isNotEmpty()) {
        invoker.setOverride(ManifestSystemProperty.TARGET_SDK_VERSION, targetSdkVersion)
    }
    if (maxSdkVersion != null) {
        invoker.setOverride(ManifestSystemProperty.MAX_SDK_VERSION, maxSdkVersion.toString())
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

