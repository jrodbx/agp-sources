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
package com.android.ide.common.gradle.model.ndk.v1

import java.io.File

interface IdeNativeVariantAbi {
  /** Returns a collection of files that affect the build. */
  val buildFiles: Collection<File>

  /** Returns a collection of native artifacts. */
  val artifacts: Collection<IdeNativeArtifact>

  /** Returns a collection of toolchains. */
  val toolChains: Collection<IdeNativeToolchain>

  /** Returns a collection of all compile settings. */
  val settings: Collection<IdeNativeSettings>

  /**
   * Return a map of file extension to each file type.
   *
   *
   * The key is the file extension, the value is either "c" or "c++".
   */
  val fileExtensions: Map<String, String>

  /** Returns the variant name. */
  val variantName: String

  /** Returns the abi. */
  val abi: String
}
