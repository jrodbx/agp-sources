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
@Deprecated("Use LazyManifestProvider instead")
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
}
