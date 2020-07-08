/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.builder.core

import com.android.builder.model.BuildType
import com.android.builder.model.ProductFlavor
import com.google.common.base.Preconditions.checkState
import com.google.common.base.Strings
import java.io.File
import java.io.Serializable
import java.util.function.IntSupplier
import java.util.function.Supplier

/**
 * Provides attributes for the variant.
 *
 * The attributes are from data merged from the manifest and product flavor.
 *
 * @param mergedFlavor the merged product flavor
 * @param buildType the type used for the build
 * @param isTestVariant whether the current variant is for a test component.
 * @param manifestSupplier the supplier of manifest attributes.
 * @param manifestFile the file for the manifest.
 */
class VariantAttributesProvider(
        private val mergedFlavor: ProductFlavor,
        private val buildType: BuildType,
        private val isTestVariant: Boolean,
        private val manifestSupplier: ManifestAttributeSupplier,
        private val manifestFile: File,
        private val fullName: String) {

    /**
     * Returns the application id override value coming from the Product Flavor and/or the Build
     * Type. If the package/id is not overridden then this returns null.
     *
     * @return the id override or null
     */
    val idOverride: String?
        get() {
            var idName = mergedFlavor.applicationId

            val idSuffix = AbstractProductFlavor.mergeApplicationIdSuffix(
                    buildType.applicationIdSuffix,
                    mergedFlavor.applicationIdSuffix)

            if (!idSuffix.isEmpty()) {
                idName = idName ?: packageName
                idName = if (idSuffix[0] == '.') idName + idSuffix else idName + '.' + idSuffix
            }

            return idName
        }

    /**
     * Returns the package name from the manifest file.
     *
     * @return the package name or throws an exception if not found in the manifest.
     */
    val packageName: String
        get() {
            checkState(!isTestVariant)
            return manifestSupplier.`package` ?: throw RuntimeException(
                    "Cannot read packageName from ${manifestFile.absolutePath}")
        }

    /**
     * Returns the split name from the manifest file.
     *
     * @return the split name or null if not found.
     */
    val split: String?
        get() = manifestSupplier.split

    /**
     * Returns the version name for this variant. This could be specified by the product flavors,
     * or, if not, it could be coming from the manifest. A suffix may be specified by the build
     * type.
     *
     * @param ignoreManifest whether or not the manifest is ignored when getting the version name
     * @return the version name or null if none defined
     */
    fun getVersionName(ignoreManifest: Boolean = false): String? {
        var versionName = mergedFlavor.versionName

        if (versionName == null && !isTestVariant && !ignoreManifest) {
            versionName = manifestSupplier.versionName
        }

        val versionSuffix = AbstractProductFlavor.mergeVersionNameSuffix(
            buildType.versionNameSuffix, mergedFlavor.versionNameSuffix)

        if (versionSuffix != null && versionSuffix.isNotEmpty()) {
            versionName = Strings.nullToEmpty(versionName) + versionSuffix
        }

        return versionName
    }

    /**
     * Returns the version code for this variant. This could be specified by the product flavors,
     * or, if not, it could be coming from the manifest.
     *
     * @param ignoreManifest whether or not the manifest is ignored when getting the version code
     * @return the version code or -1 if there was none defined.
     */
    fun getVersionCode(ignoreManifest: Boolean = false): Int {
        var versionCode = mergedFlavor.versionCode ?: -1

        if (versionCode == -1 && !isTestVariant && !ignoreManifest) {
            versionCode = manifestSupplier.versionCode
        }

        return versionCode
    }

    /**
     * Returns the instrumentation runner, found in the build file or manifest file
     *
     * @return the instrumentation runner or `null` if there is none specified.
     */
    val instrumentationRunner: String?
        get() = mergedFlavor.testInstrumentationRunner ?: manifestSupplier.instrumentationRunner

    /**
     * Returns the targetPackage from the instrumentation tag in the manifest file.
     *
     * @return the targetPackage or `null` if there is none specified.
     */
    val targetPackage: String?
        get() = manifestSupplier.targetPackage

    /**
     * Returns the functionalTest, found in the build file or manifest file.
     *
     * @return the functionalTest or `null` if there is none specified.
     */
    val functionalTest: Boolean?
        get() = mergedFlavor.testFunctionalTest ?: manifestSupplier.functionalTest

    /**
     * Returns the handleProfiling, found in the build file or manifest file.
     *
     * @return the handleProfiling or `null` if there is none specified.
     */
    val handleProfiling: Boolean?
        get() = mergedFlavor.testHandleProfiling ?: manifestSupplier.handleProfiling

    /**
     * Returns the testLabel from the instrumentation tag in the manifest file.
     *
     * @return the testLabel or `null` if there is none specified.
     */
    val testLabel: String?
        get() = manifestSupplier.testLabel

    /**
     * Returns value of the `extractNativeLibs` attribute of the `application` tag, if
     * present in the manifest file.
     */
    val extractNativeLibs: Boolean?
        get() = manifestSupplier.extractNativeLibs

    /**
     * Returns the application ID even if this is a test variant.
     *
     * @return the application ID
     */
    fun getApplicationId(testedPackage: String): String {
        var id: String?

        if (isTestVariant) {
            id = mergedFlavor.testApplicationId
            if (id == null) {
                id = "$testedPackage.test"
            } else {
                if (id == testedPackage) {
                    throw RuntimeException(
                            "Application and test application id cannot be the same: both are $id for $fullName")
                }
            }

        } else {
            // first get package override.
            // if it's null, this means we just need the default package
            // from the manifest since both flavor and build type do nothing.
            id = idOverride ?: packageName
        }

        return id
    }

    /**
     * Returns the original application ID before any overrides from flavors. If the variant is a
     * test variant, then the application ID is the one coming from the configuration of the tested
     * variant, and this call is similar to [.getApplicationId]
     *
     * @return the original application ID
     */
    fun getOriginalApplicationId(testedPackage: String): String {
        return if (isTestVariant) {
            getApplicationId(testedPackage)
        } else packageName

    }

    /**
     * Returns the test app application ID, which should only be called from a test variant.
     *
     * @return the test application ID
     */
    fun getTestApplicationId(testedPackage: String): String {
        checkState(isTestVariant)

        return if (!Strings.isNullOrEmpty(mergedFlavor.testApplicationId)) {
            // if it's specified through build file read from there
            mergedFlavor.testApplicationId!!
        } else {
            // otherwise getApplicationId() contains rules for getting the
            // applicationId for the test app from the tested application
            getApplicationId(testedPackage)
        }
    }

    val manifestVersionNameSupplier: Supplier<String?>
        get() {
            val file = if (isTestVariant) null else manifestFile
            val versionSuffix = AbstractProductFlavor.mergeVersionNameSuffix(
                buildType.versionNameSuffix, mergedFlavor.versionNameSuffix
            )
            return ManifestVersionNameSupplier(
                file,
                manifestSupplier.isManifestFileRequired,
                versionSuffix
            )
        }

    val manifestVersionCodeSupplier: IntSupplier
        get() {
            val file = if (isTestVariant) null else manifestFile
            return ManifestVersionCodeSupplier(
                file,
                manifestSupplier.isManifestFileRequired)
        }

    private class ManifestVersionNameSupplier(
            private val manifestFile: File? = null,
            private val isManifestFileRequired: Boolean,
            private val versionSuffix: String? = null
    ) : Supplier<String?>, Serializable {
        private var isCached: Boolean = false
        private var versionName: String? = null

        override fun get(): String? {
            if (isCached) {
                return versionName
            }
            if (manifestFile != null) {
                versionName = DefaultManifestParser(
                    manifestFile,
                    { true },
                    isManifestFileRequired,
                    null
                ).versionName
            }

            if (versionSuffix != null && versionSuffix.isNotEmpty()) {
                versionName = Strings.nullToEmpty(versionName) + versionSuffix
            }

            isCached = true
            return versionName
        }
    }

    private class ManifestVersionCodeSupplier(
                private val manifestFile: File? = null,
                private val isManifestFileRequired: Boolean
    ) : IntSupplier, Serializable {
        private var isCached: Boolean = false
        private var versionCode: Int = -1

        override fun getAsInt(): Int {
            if (isCached) {
                return versionCode
            }
            if (versionCode == -1 && manifestFile != null) {
                versionCode = DefaultManifestParser(
                    manifestFile,
                    { true },
                    isManifestFileRequired,
                    null
                ).versionCode
            }
            isCached = true
            return versionCode
        }
    }

}
