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
package com.android.ide.common.gradle.model.impl

import com.android.ide.common.gradle.model.IdeAndroidArtifact
import com.android.ide.common.gradle.model.IdeBaseArtifact
import com.android.ide.common.gradle.model.IdeJavaArtifact
import com.android.ide.common.gradle.model.IdeProductFlavor
import com.android.ide.common.gradle.model.IdeTestedTargetVariant
import com.android.ide.common.gradle.model.IdeVariant
import com.google.common.collect.ImmutableList
import java.io.Serializable

data class IdeVariantImpl(
  override val name: String,
  override val displayName: String,
  override val mainArtifact: IdeAndroidArtifact,
  override val extraAndroidArtifacts: List<IdeAndroidArtifact>,
  override val extraJavaArtifacts: List<IdeJavaArtifact>,
  override val buildType: String,
  override val productFlavors: List<String>,
  override val mergedFlavor: IdeProductFlavor,
  override val testedTargetVariants: List<IdeTestedTargetVariant>,
  override val instantAppCompatible: Boolean
) : IdeVariant, Serializable {
  override val testArtifacts: List<IdeBaseArtifact>
    get() = ImmutableList.copyOf(
      (extraAndroidArtifacts.asSequence() + extraJavaArtifacts.asSequence()).filter { it.isTestArtifact }.asIterable())

  override val androidTestArtifact: IdeAndroidArtifact? get() = extraAndroidArtifacts.firstOrNull { it.isTestArtifact }

  override val unitTestArtifact: IdeJavaArtifact? get() = extraJavaArtifacts.firstOrNull { it.isTestArtifact }
}
