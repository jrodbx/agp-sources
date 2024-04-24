/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.builder.model.v2.models

import com.android.builder.model.v2.AndroidModel
import com.android.builder.model.v2.ide.ArtifactDependenciesAdjacencyList
import com.android.builder.model.v2.ide.Library

interface VariantDependenciesAdjacencyList: AndroidModel {
    /**
     * Returns the name of the variant. It is made up of the build type and flavors (if applicable)
     *
     * @return the name of the variant.
     */
    val name: String

    val mainArtifact: ArtifactDependenciesAdjacencyList

    val androidTestArtifact: ArtifactDependenciesAdjacencyList?
    val unitTestArtifact: ArtifactDependenciesAdjacencyList?
    val testFixturesArtifact: ArtifactDependenciesAdjacencyList?

    /**
     * The list of external libraries used by all the variants in the module.
     *
     * The key for the map entries is the keys used in [Edge.from] or [Edge.to]
     * and [Library.key]
     */
    val libraries: Map<String, Library>
}
