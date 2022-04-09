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
import com.android.build.api.dsl.ProductFlavor
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantDslInfoBuilder
import com.android.build.gradle.internal.core.VariantDslInfoImpl
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.StringOption
import com.android.builder.core.BuilderConstants
import com.android.builder.core.ComponentType
import com.android.utils.combineAsCamelCase
import com.android.utils.toStrings
import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import java.io.File

class VariantPathHelper(
    val buildDirectory: DirectoryProperty,
    private val variantDslInfo: VariantDslInfo,
    private val dslServices: DslServices
) {

    /**
     * Returns a unique directory name (can include multiple folders) for the variant, based on
     * build type, flavor and test.
     *
     *
     * This always uses forward slashes ('/') as separator on all platform.
     *
     * @return the directory name for the variant
     */
    val dirName: String by lazy {
        Joiner.on('/').join(directorySegments)
    }


    /**
     * Returns a unique directory name (can include multiple folders) for the variant, based on
     * build type, flavor and test.
     *
     * @return the directory name for the variant
     */
    val directorySegments: Collection<String?> by lazy {
        val builder =
            ImmutableList.builder<String>()
        if (variantDslInfo.componentType.isNestedComponent) {
            builder.add(variantDslInfo.componentType.prefix)
        }
        if (variantDslInfo.productFlavorList.isNotEmpty()) {
            builder.add(
                combineAsCamelCase(
                    variantDslInfo.productFlavorList, ProductFlavor::getName
                )
            )
        }
        builder.add((variantDslInfo as VariantDslInfoImpl).buildTypeObj.name)
        builder.build()
    }

    /**
     * Returns the expected output file name for the variant.
     *
     * @param archivesBaseName the project's archiveBaseName
     * @param baseName the variant baseName
     */
    fun getOutputFileName(archivesBaseName: String, baseName: String): String {
        // we only know if it is signed during configuration, if it's the base module.
        // Otherwise, don't differentiate between signed and unsigned.
        val suffix =
            if (variantDslInfo.isSigningReady || !variantDslInfo.componentType.isBaseModule)
                SdkConstants.DOT_ANDROID_PACKAGE
            else "-unsigned.apk"
        return "$archivesBaseName-$baseName$suffix"
    }

    /**
     * Returns a full name that includes the given splits name.
     *
     * @param splitName the split name
     * @return a unique name made up of the variant and split names.
     */
    fun computeFullNameWithSplits(splitName: String): String {
        return VariantDslInfoBuilder.computeFullNameWithSplits(
            variantDslInfo.componentIdentity,
            variantDslInfo.componentType,
            splitName
        )
    }

    /**
     * Returns the full, unique name of the variant, including BuildType, flavors and test, dash
     * separated. (similar to full name but with dashes)
     *
     * @return the name of the variant
     */
    val baseName: String by lazy {
        VariantDslInfoBuilder.computeBaseName(
            variantDslInfo as VariantDslInfoImpl,
            variantDslInfo.componentType
        )
    }

    /**
     * Returns a base name that includes the given splits name.
     *
     * @param splitName the split name
     * @return a unique name made up of the variant and split names.
     */
    fun computeBaseNameWithSplits(splitName: String): String {
        val sb = StringBuilder()
        if (variantDslInfo.productFlavorList.isNotEmpty()) {
            for (pf in variantDslInfo.productFlavorList) {
                sb.append(pf.name).append('-')
            }
        }
        sb.append(splitName).append('-')
        sb.append((variantDslInfo as VariantDslInfoImpl).buildTypeObj.name)
        if (variantDslInfo.componentType.isNestedComponent) {
            sb.append('-').append(variantDslInfo.componentType.prefix)
        }
        return sb.toString()
    }

    fun intermediatesDir(vararg subDirs: String): Provider<Directory> =
        getBuildSubDir(SdkConstants.FD_INTERMEDIATES, subDirs)

    fun outputDir(vararg subDirs: String): Provider<Directory> =
        getBuildSubDir(SdkConstants.FD_OUTPUTS, subDirs)

    fun generatedDir(vararg subDirs: String): Provider<Directory> =
        getBuildSubDir(SdkConstants.FD_GENERATED, subDirs)

    fun reportsDir(vararg subDirs: String): Provider<Directory> =
        getBuildSubDir(BuilderConstants.FD_REPORTS, subDirs)

    val buildConfigSourceOutputDir: Provider<Directory>
            by lazy { generatedDir("source", "buildConfig", dirName) }

    val renderscriptObjOutputDir: Provider<Directory>
            by lazy {
                getBuildSubDir(
                    SdkConstants.FD_INTERMEDIATES,
                    toStrings("rs", directorySegments, "obj").toTypedArray()
                )
            }

    val coverageReportDir: Provider<Directory>
            by lazy { reportsDir("coverage", dirName) }

    /**
     * Obtains the location where APKs should be placed.
     *
     * @return the location for APKs
     */
    val apkLocation: File
            by lazy {
                val override = dslServices.projectOptions.get(StringOption.IDE_APK_LOCATION)
                // it does not really matter if the build was invoked from the IDE or not, it only
                // matters if it is an 'optimized' build and in that case, we consider it a
                // custom build.
                val customBuild =
                        dslServices.projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY) != null ||
                        dslServices.projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI) != null ||
                        dslServices.projectOptions.get(IntegerOption.IDE_TARGET_DEVICE_API) != null
                val baseDirectory =when {
                    override != null -> dslServices.file(override)
                    customBuild ->  deploymentApkLocation.get().asFile
                    else -> defaultApkLocation.get().asFile
                }
                File(baseDirectory, dirName)
            }

    /**
     * Obtains the default location for APKs.
     */
    private val defaultApkLocation: Provider<Directory>
            by lazy { outputDir("apk") }

    /**
     * Obtains the location for APKs that target a specific device.
     *
     * APKs built for a specific device are put in intermediates/ in order to
     * distinguish them from other APKs
     *
     * @return the location for targeted APKs
     */
    private val deploymentApkLocation: Provider<Directory> by lazy {
        intermediatesDir("apk")
    }

    val aarLocation: Provider<Directory>
            by lazy { outputDir(BuilderConstants.EXT_LIB_ARCHIVE) }

    val manifestOutputDirectory: Provider<Directory>
        get() {
            val componentType: ComponentType = variantDslInfo.componentType
            if (componentType.isTestComponent) {
                if (componentType.isApk) { // ANDROID_TEST
                    return intermediatesDir("manifest", dirName)
                }
            } else {
                return intermediatesDir("manifests", "full", dirName)
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

    fun getGeneratedResourcesDir(name: String): Provider<Directory> {
        val dirs: List<String> =
            listOf("res", name) + directorySegments.filterNotNull()
        return generatedDir(*dirs.toTypedArray())
    }

    private fun getBuildSubDir(childDir: String, subDirs: Array<out String>): Provider<Directory> {
        // Prevent accidental usage with files.
        if (subDirs.any() && subDirs.last().contains('.')) {
            throw IllegalStateException("Directory should not contain '.'.")
        }
        return buildDirectory.dir("$childDir${subDirs.joinToString(separator = "/", prefix = "/")}")
    }
}
