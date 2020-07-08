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

import com.android.SdkConstants
import com.android.build.api.artifact.Artifact
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.options.StringOption
import com.android.builder.core.BuilderConstants
import com.android.builder.core.VariantType
import com.android.builder.model.AndroidProject
import com.android.utils.FileUtils
import com.android.utils.toStrings
import org.gradle.api.Project
import org.gradle.api.file.Directory
import java.io.File
import java.util.Locale

class VariantPathHelper(
    private val project: Project,
    private val variantDslInfo: VariantDslInfo,
    private val dslServices: DslServices
) {

    // this is very inefficient as we re-instantiate intermediats/outputDir every time
    // FIXME replace all this with Project.layout.getBuildDirectory() which returns a dynamic DirectoryProperty

    val intermediatesDir: File
        get() = File(project.buildDir, AndroidProject.FD_INTERMEDIATES)
    val outputDir: File
        get() = File(project.buildDir, AndroidProject.FD_OUTPUTS)
    val generatedDir: File
        get() = File(project.buildDir, AndroidProject.FD_GENERATED)
    val reportsDir: File
        get() = File(project.buildDir, BuilderConstants.FD_REPORTS)

    val generatedResOutputDir: File
        get() = getGeneratedResourcesDir("resValues")

    val generatedPngsOutputDir: File
        get() = getGeneratedResourcesDir("pngs")

    val renderscriptResOutputDir: File
        get() = getGeneratedResourcesDir("rs")

    val microApkResDirectory: File
        get() = FileUtils.join(generatedDir, "res", "microapk", variantDslInfo.dirName)

    val microApkManifestFile: File
        get() = FileUtils.join(
            generatedDir,
            "manifests",
            "microapk",
            variantDslInfo.dirName,
            SdkConstants.FN_ANDROID_MANIFEST_XML
        )

    val defaultMergeResourcesOutputDir: File
        get() = FileUtils.join(
            intermediatesDir,
            SdkConstants.FD_RES,
            SdkConstants.FD_MERGED,
            variantDslInfo.dirName
        )

    val compiledResourcesOutputDir: File
        get() = FileUtils.join(
            intermediatesDir,
            SdkConstants.FD_RES,
            SdkConstants.FD_COMPILED,
            variantDslInfo.dirName
        )

    val buildConfigSourceOutputDir: File
        get() = FileUtils.join(generatedDir, "source", "buildConfig", variantDslInfo.dirName)

    val renderscriptObjOutputDir: File
        get() = FileUtils.join(
            intermediatesDir,
            toStrings(
                "rs",
                variantDslInfo.directorySegments,
                "obj"
            )
        )

    val coverageReportDir: File
        get() = FileUtils.join(reportsDir, "coverage", variantDslInfo.dirName)

    /**
     * Obtains the location where APKs should be placed.
     *
     * @return the location for APKs
     */
    val apkLocation: File
        get() {
            val override = dslServices.projectOptions.get(StringOption.IDE_APK_LOCATION)
            val baseDirectory =
                if (override != null) dslServices.file(override) else getDefaultApkLocation()
            return File(baseDirectory, variantDslInfo.dirName)
        }

    /**
     * Obtains the default location for APKs.
     *
     * @return the default location for APKs
     */
    private fun getDefaultApkLocation(): File {
        return FileUtils.join(outputDir, "apk")
    }

    val aarLocation: File
        get() = FileUtils.join(outputDir, BuilderConstants.EXT_LIB_ARCHIVE)

    val manifestOutputDirectory: File
        get() {
            val variantType: VariantType = variantDslInfo.variantType
            if (variantType.isTestComponent) {
                if (variantType.isApk) { // ANDROID_TEST
                    return FileUtils.join(
                        intermediatesDir,
                        "manifest",
                        variantDslInfo.dirName
                    )
                }
            } else {
                return FileUtils.join(
                    intermediatesDir, "manifests", "full", variantDslInfo.dirName
                )
            }
            throw RuntimeException("getManifestOutputDirectory called for an unexpected variant.")
        }

    /**
     * Returns a place to store incremental build data. The {@code name} argument has to be unique
     * per task, ideally generated with [TaskInformation.name].
     */
    fun getIncrementalDir(name: String): File {
        return FileUtils.join(intermediatesDir, "incremental", name)
    }

    fun getIntermediateDir(taskOutput: Artifact<Directory>): File {
        return intermediate(taskOutput.name().toLowerCase(Locale.US))
    }

    private fun getGeneratedResourcesDir(name: String): File {
        return FileUtils.join(
            generatedDir,
            listOf("res", name) + variantDslInfo.directorySegments
        )
    }

    /**
     * An intermediate directory for this variant.
     *
     *
     * Of the form build/intermediates/dirName/variant/
     */
    private fun intermediate(directoryName: String): File {
        return FileUtils.join(intermediatesDir, directoryName, variantDslInfo.dirName)
    }
}