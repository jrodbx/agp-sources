/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.ide.common.gradle.model

interface IdeVariant {
  val name: String
  val displayName: String
  val mainArtifact: IdeAndroidArtifact
  val androidTestArtifact: IdeAndroidArtifact?
  val unitTestArtifact: IdeJavaArtifact?
  val buildType: String
  val productFlavors: List<String>

  /**
   * The result of the merge of all the flavors and of the main default config. If no flavors
   * are defined then this is the same as the default config.
   */
  val mergedFlavor: IdeProductFlavor
  val testedTargetVariants: List<IdeTestedTargetVariant>
  val instantAppCompatible: Boolean
}