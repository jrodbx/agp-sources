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

package com.android.build.gradle.internal.manifest

/**
 * Represents the data parser from a manifest file.
 *
 * This is meant by to used with [ManifestDataProvider]
 */
class ManifestData(

    /**
     * Returns the package name from the manifest file.
     *
     * @return the package name or null if not found.
     */
    var packageName: String? = null,

    /**
     * Returns the split name from the manifest file.
     *
     * @return the split name or null if not found.
     */
    var split: String? = null,

    /**
     * Returns the minSdkVersion from the manifest file. The returned value can be an Integer or a
     * String
     *
     * @return the minSdkVersion or null if value is not set.
     */
    var minSdkVersion: AndroidTarget? = null,

    /**
     * Returns the targetSdkVersion from the manifest file.
     * The returned value can be an Integer or a String
     *
     * @return the targetSdkVersion or null if not found
     */
    var targetSdkVersion: AndroidTarget? = null,

    /**
     * Returns the version name from the manifest file.
     *
     * @return the version name or null if not found.
     */
    var versionName: String? = null,

    /**
     * Returns the version code from the manifest file.
     *
     * @return the version code or null if not found
     */
    var versionCode: Int? = null,

    /**
     * Returns the instrumentation runner from the instrumentation tag in the manifest file.
     *
     * @return the instrumentation runner or `null` if there is none specified.
     */
    var instrumentationRunner: String? = null,

    /**
     * Returns the functionalTest from the instrumentation tag in the manifest file.
     *
     * @return the functionalTest or `null` if there is none specified.
     */
    var functionalTest: Boolean? = null,

    /**
     * Returns the handleProfiling from the instrumentation tag in the manifest file.
     *
     * @return the handleProfiling or `null` if there is none specified.
     */
    var handleProfiling: Boolean? = null,

    /**
     * Returns the testLabel from the instrumentation tag in the manifest file.
     *
     * @return the testLabel or `null` if there is none specified.
     */
    var testLabel: String? = null,

    /**
     * Returns value of the `extractNativeLibs` attribute of the `application` tag, if
     * present.
     */
    var extractNativeLibs: Boolean? = null,

    /**
     * Returns value of the `useEmbeddedDex` attribute of the `application` tag, if
     * present.
     */
    var useEmbeddedDex: Boolean? = null
) {

    /**
     * Temporary holder of api/codename to store the output of the minSdkVersion value.
     * This is not final and will be replaced by the final API class.
     * // FIXME b/150290704
     */
    data class AndroidTarget(
        val apiLevel: Int?,
        val codeName: String?
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ManifestData

        if (packageName != other.packageName) return false
        if (split != other.split) return false
        if (minSdkVersion != other.minSdkVersion) return false
        if (targetSdkVersion != other.targetSdkVersion) return false
        if (versionName != other.versionName) return false
        if (versionCode != other.versionCode) return false
        if (instrumentationRunner != other.instrumentationRunner) return false
        if (functionalTest != other.functionalTest) return false
        if (handleProfiling != other.handleProfiling) return false
        if (testLabel != other.testLabel) return false
        if (extractNativeLibs != other.extractNativeLibs) return false
        if (useEmbeddedDex != other.useEmbeddedDex) return false

        return true
    }

    override fun hashCode(): Int {
        var result = packageName?.hashCode() ?: 0
        result = 31 * result + (split?.hashCode() ?: 0)
        result = 31 * result + (minSdkVersion?.hashCode() ?: 0)
        result = 31 * result + (targetSdkVersion?.hashCode() ?: 0)
        result = 31 * result + (versionName?.hashCode() ?: 0)
        result = 31 * result + (versionCode ?: 0)
        result = 31 * result + (instrumentationRunner?.hashCode() ?: 0)
        result = 31 * result + (functionalTest?.hashCode() ?: 0)
        result = 31 * result + (handleProfiling?.hashCode() ?: 0)
        result = 31 * result + (testLabel?.hashCode() ?: 0)
        result = 31 * result + (extractNativeLibs?.hashCode() ?: 0)
        result = 31 * result + (useEmbeddedDex?.hashCode() ?: 0)
        return result
    }
}