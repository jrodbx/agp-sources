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

import com.android.builder.model.Variant
import java.io.Serializable

interface IdeVariant : Variant, Serializable {
  override fun getMainArtifact(): IdeAndroidArtifact
  val androidTestArtifact: IdeAndroidArtifact?
  val unitTestArtifact: IdeJavaArtifact?
  override fun getExtraAndroidArtifacts(): Collection<IdeAndroidArtifact>
  override fun getExtraJavaArtifacts(): Collection<IdeJavaArtifact>
  val testArtifacts: Collection<IdeBaseArtifact>
}