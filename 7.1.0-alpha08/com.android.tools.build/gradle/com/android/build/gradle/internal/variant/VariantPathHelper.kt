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

package com.android.build.gradle.internal.variant

import com.android.build.api.artifact.Artifact
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.options.StringOption
import com.android.builder.core.BuilderConstants
import com.android.builder.core.VariantType
import com.android.builder.model.AndroidProject
import com.android.utils.toStrings
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import java.io.File
import java.util.Locale

class VariantPathHelper(
    val buildDirectory: DirectoryProperty,
    private val variantDslInfo: VariantDslInfo<*>,
    private val dslServices: DslServices
) {

    fun intermediatesDir(vararg subDirs: String): Provider<Directory> =
        getBuildSubDir(AndroidProject.FD_INTERMEDIATES, subDirs)

    fun outputDir(vararg subDirs: String): Provider<Directory> =
        getBuildSubDir(AndroidProject.FD_OUTPUTS, subDirs)

    fun generatedDir(vararg subDirs: String): Provider<Directory> =
        getBuildSubDir(AndroidProject.FD_GENERATED, subDirs)

    fun reportsDir(vararg subDirs: String): Provider<Directory> =
        getBuildSubDir(BuilderConstants.FD_REPORTS, subDirs)

    val generatedResOutputDir: Provider<Directory>
            by lazy { getGeneratedResourcesDir("resValues") }

    val generatedPngsOutputDir: Provider<Directory>
            by lazy { getGeneratedResourcesDir("pngs") }

    val renderscriptResOutputDir: Provider<Directory>
            by lazy { getGeneratedResourcesDir("rs") }

    val buildConfigSourceOutputDir: Provider<Directory>
            by lazy { generatedDir("source", "buildConfig", variantDslInfo.dirName) }

    val renderscriptObjOutputDir: Provider<Directory>
            by lazy {
                getBuildSubDir(
                    AndroidProject.FD_INTERMEDIATES,
                    toStrings("rs", variantDslInfo.directorySegments, "obj").toTypedArray()
                )
            }

    val coverageReportDir: Provider<Directory>
            by lazy { reportsDir("coverage", variantDslInfo.dirName) }

    /**
     * Obtains the location where APKs should be placed.
     *
     * @return the location for APKs
     */
    val apkLocation: File
            by lazy {
                val override = dslServices.projectOptions.get(StringOption.IDE_APK_LOCATION)
                val baseDirectory =
                    if (override != null) {
                        dslServices.file(override)
                    } else {
                        defaultApkLocation.get().asFile
                    }
                File(baseDirectory, variantDslInfo.dirName)
            }

    /**
     * Obtains the default location for APKs.
     */
    private val defaultApkLocation: Provider<Directory>
            by lazy { outputDir("apk") }

    val aarLocation: Provider<Directory>
            by lazy { outputDir(BuilderConstants.EXT_LIB_ARCHIVE) }

    val manifestOutputDirectory: Provider<Directory>
        get() {
            val variantType: VariantType = variantDslInfo.variantType
            if (variantType.isTestComponent) {
                if (variantType.isApk) { // ANDROID_TEST
                    return intermediatesDir("manifest", variantDslInfo.dirName)
                }
            } else {
                return intermediatesDir("manifests", "full", variantDslInfo.dirName)
            }
            throw RuntimeException("getManifestOutputDirectory called for an unexpected variant.")
        }

    /**
     * Returns a place to store incremental build data. The {@code name} argument has to be unique
     * per task, ideally generated with [TaskInformation.name].
     */
    fun getIncrementalDir(name: String): File {
        return intermediatesDir("incremental", name).get().asFile
    }

    fun getIntermediateDir(taskOutput: Artifact<Directory>): File {
        return intermediate(taskOutput.name().toLowerCase(Locale.US))
    }

    private fun getGeneratedResourcesDir(name: String): Provider<Directory> {
        val dirs: List<String> =
            listOf("res", name) + variantDslInfo.directorySegments.filterNotNull()
        return generatedDir(*dirs.toTypedArray())
    }

    private fun getBuildSubDir(childDir: String, subDirs: Array<out String>): Provider<Directory> {
        // Prevent accidental usage with files.
        if (subDirs.any() && subDirs.last().contains('.')) {
            throw IllegalStateException("Directory should not contain '.'.")
        }
        return buildDirectory.dir("$childDir${subDirs.joinToString(separator = "/", prefix = "/")}")
    }

    /**
     * An intermediate directory for this variant.
     *
     *
     * Of the form build/intermediates/dirName/variant/
     */
    private fun intermediate(directoryName: String): File {
        return intermediatesDir(directoryName, variantDslInfo.dirName).get().asFile
    }
}
