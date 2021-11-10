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
 * Generic version of the gradle-api BuiltArtifact with all gradle specific types removed.
 */
data class GenericBuiltArtifact(
    /**
     * Returns the output type of the referenced APK.
     *
     * @return the Output type for this APK
     */
    val outputType: String,

    /**
     * Returns a possibly empty list of [GenericFilterConfiguration] for this output. If the list
     * is empty this means there is no filter associated to this output.
     *
     * @return list of [GenericFilterConfiguration] for this output.
     */
    val filters: Collection<GenericFilterConfiguration> = listOf(),

    val attributes: Map<String, String> = mapOf(),

    override val versionCode: Int? = null,
    override val versionName: String? = null,
    override val outputFile: String
): CommonBuiltArtifact
