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
 * Generic version of the gradle-api BuiltArtifacts with all the gradle types removed.
 */
data class GenericBuiltArtifacts(

    override val version: Int,

    /**
     * Identifies the [GenericBuiltArtifacts] for this [Collection] of [GenericBuiltArtifacts],
     * all [GenericBuiltArtifact] are the same type of artifact.
     *
     * @return the [GenericArtifactType] for all the [GenericBuiltArtifact] instances.
     */
    val artifactType: GenericArtifactType,

    /**
     * Returns the application ID for these [GenericBuiltArtifact] instances.
     *
     * @return the application ID.
     */
    override val applicationId: String,

    /**
     * Identifies the variant name for these [GenericBuiltArtifact]
     */
    override val variantName: String,

    /**
     * Returns the [Collection] of [GenericBuiltArtifact].
     */
    val elements: Collection<GenericBuiltArtifact>,

    /**
     *  Type of file stored in [elements], can be "File" or "Directory", or null if there are
     *  no elements.
     */
    val elementType: String?
): CommonBuiltArtifacts
