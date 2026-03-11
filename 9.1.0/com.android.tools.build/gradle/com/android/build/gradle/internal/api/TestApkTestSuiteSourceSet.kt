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

package com.android.build.gradle.internal.api

import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.build.api.dsl.AgpTestSuiteDependencies
import com.android.build.api.variant.TestSuiteSourceSet
import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.gradle.internal.services.VariantServices
import java.io.File
import org.gradle.api.file.Directory

class TestApkTestSuiteSourceSet(
  sourceSetName: String,
  variantServices: VariantServices,
  userAddedSourceSets: Collection<Directory>,
  javaEnabled: Boolean,
  kotlinEnabled: Boolean,
  override val dependencies: AgpTestSuiteDependencies?,
) :
  AbstractTestSuiteSourceSet(sourceSetName, variantServices, userAddedSourceSets, javaEnabled, kotlinEnabled), TestSuiteSourceSet.TestApk {

  override val manifestFile = File(variantServices.projectInfo.projectDirectory.asFile, "src/$sourceSetName/$FN_ANDROID_MANIFEST_XML")

  override val java: FlatSourceDirectoriesImpl? = if (javaEnabled) createJavaSources(variantServices) else null

  override val kotlin: FlatSourceDirectoriesImpl? = if (kotlinEnabled) createKotlinSources(variantServices) else null

  override val resources: FlatSourceDirectoriesImpl = resourcesSourcesFolder
}
