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

import com.android.ide.common.gradle.model.IdeAaptOptions
import com.android.ide.common.gradle.model.IdeAndroidGradlePluginProjectFlags
import com.android.ide.common.gradle.model.IdeAndroidProject
import com.android.ide.common.gradle.model.IdeAndroidProjectType
import com.android.ide.common.gradle.model.IdeBuildTypeContainer
import com.android.ide.common.gradle.model.IdeDependenciesInfo
import com.android.ide.common.gradle.model.IdeJavaCompileOptions
import com.android.ide.common.gradle.model.IdeLintOptions
import com.android.ide.common.gradle.model.IdeProductFlavorContainer
import com.android.ide.common.gradle.model.IdeSigningConfig
import com.android.ide.common.gradle.model.IdeVariantBuildInformation
import com.android.ide.common.gradle.model.IdeViewBindingOptions
import java.io.File
import java.io.Serializable

data class IdeAndroidProjectImpl(
  override val modelVersion: String,
  override val name: String,
  override val projectType: IdeAndroidProjectType,
  override val defaultConfig: IdeProductFlavorContainer,
  override val buildTypes: Collection<IdeBuildTypeContainer>,
  override val productFlavors: Collection<IdeProductFlavorContainer>,
  override val variantNames: Collection<String>,
  override val flavorDimensions: Collection<String>,
  override val compileTarget: String,
  override val bootClasspath: Collection<String>,
  override val signingConfigs: Collection<IdeSigningConfig>,
  override val aaptOptions: IdeAaptOptions,
  override val lintOptions: IdeLintOptions,
  override val javaCompileOptions: IdeJavaCompileOptions,
  override val buildFolder: File,
  override val resourcePrefix: String?,
  override val buildToolsVersion: String?,
  override val ndkVersion: String?,
  override val isBaseSplit: Boolean,
  override val dynamicFeatures: Collection<String>,
  override val viewBindingOptions: IdeViewBindingOptions?,
  override val dependenciesInfo: IdeDependenciesInfo?,
  override val groupId: String?,
  override val agpFlags: IdeAndroidGradlePluginProjectFlags,
  override val variantsBuildInformation: Collection<IdeVariantBuildInformation>,
  override val lintRuleJars: List<File>?
) : IdeAndroidProject, Serializable
