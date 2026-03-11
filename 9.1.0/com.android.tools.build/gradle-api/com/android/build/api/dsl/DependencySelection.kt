/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.api.dsl

import org.gradle.api.Action
import org.gradle.api.provider.ListProperty

/** Specifies options for doing Gradle variant selection for external Android dependencies based on build types and product flavors. */
interface DependencySelection {
  /**
   * Specifies a list of build types that the plugin should try to use when a direct variant match with a dependency is not possible.
   *
   * If the list is left empty, the default variant for the dependencies being consumed will be of build type "release"
   *
   * If you want to preserve the default value use `selectBuildTypeFrom.add()` and if you would like to set your own list of build types use
   * `selectBuildTypeFrom.set()`
   */
  val selectBuildTypeFrom: ListProperty<String>

  /**
   * Configures a single product flavor dimension that the plugin should try to use when a direct variant match with a dependency is not
   * possible.
   */
  fun productFlavorDimension(dimension: String, action: Action<ProductFlavorDimensionSpec>)
}

interface ProductFlavorDimensionSpec {
  val selectFrom: ListProperty<String>
}
