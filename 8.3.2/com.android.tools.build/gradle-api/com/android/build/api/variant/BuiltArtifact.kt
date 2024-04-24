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

package com.android.build.api.variant

/**
 * Represents a built artifact that is present in the file system.
 */
interface BuiltArtifact: VariantOutputConfiguration {

    /**
     * Returns a read-only version code.
     *
     * @return version code or null if the version code is unknown (not set in manifest nor DSL)
     */
    val versionCode: Int?

    /**
     * Returns a read-only version name.
     *
     * @return version name or null if the version name is unknown (not set in manifest nor DSL)
     */
    val versionName: String?

    /**
     * Absolute path to the built file
     *
     * @return the output file path.
     */
    val outputFile: String
}
