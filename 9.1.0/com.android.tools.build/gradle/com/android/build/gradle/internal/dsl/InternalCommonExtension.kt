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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.CompileSdkSpec
import org.gradle.api.Action

/**
 * Internal extension of the DSL interface that overrides the properties to use the implementation types, in order to enable the use of
 * kotlin delegation from the original DSL classes to the new implementations.
 */
interface InternalCommonExtension : CommonExtension, Lockable {

  var compileSdkVersion: String?

  // See GroovyExtensionsTest
  fun setFlavorDimensions(flavorDimensions: List<String>)

  fun compileSdk(action: Action<CompileSdkSpec>)
}
