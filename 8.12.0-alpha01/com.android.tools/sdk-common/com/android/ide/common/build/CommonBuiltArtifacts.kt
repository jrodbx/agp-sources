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

package com.android.ide.common.build

/**
 * Common interface between studio specific and agp specific in memory representations of the
 * output.json file.
 *
 * agp representation is located in gradle-api package so end users can load/write those files
 * when consuming/producing artifacts.
 *
 * studio representation is located here in sdk-common and cannot import gradle-api interfaces.
 */
interface CommonBuiltArtifacts {
    /**
     * Indicates the version of the metadata file.
     *
     * @return the metadata file.
     */
    val version: Int

    /**
     * Returns the application ID for this [CommonBuiltArtifacts] instance.
     *
     * @return the application ID.
     */
    val applicationId: String

    /**
     * Identifies the variant name for this [CommonBuiltArtifacts] instance.
     */
    val variantName: String

    /**
     * Returns baseline profile details for this [CommonBuiltArtifacts] instance.
     * If it is null, this means that the baseline profiles are not available. Some examples are:
     * - An older version of Android Gradle plugin is being used, which does not have this property
     * - Baseline profiles do not apply to this [CommonBuiltArtifacts] instance
     */
    val baselineProfiles: List<BaselineProfileDetails>?

    /**
     * The minimum API level that the output `.dex` files support (or null if it is unknown/not
     * applicable to the current artifact type).
     *
     * Note that this value may be different from the minSdkVersion specified in the DSL/manifest.
     * For example, if the IDE is deploying to a device (i.e., the API level of the device is known)
     * and if a few more conditions are met, AGP may use a higher minSdkVersion for dexing to
     * improve build performance.
     */
    val minSdkVersionForDexing: Int?
}
