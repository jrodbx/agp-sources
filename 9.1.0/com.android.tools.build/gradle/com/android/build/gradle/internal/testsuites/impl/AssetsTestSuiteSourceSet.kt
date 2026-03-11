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

package com.android.build.gradle.internal.testsuites.impl

import com.android.build.api.dsl.AgpTestSuiteDependencies
import com.android.build.api.variant.TestSuiteSourceSet
import com.android.build.api.variant.TestSuiteSourceType
import com.android.build.api.variant.impl.FileBasedDirectoryEntryImpl
import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.gradle.internal.services.VariantServices
import java.io.File

internal class AssetsTestSuiteSourceSet(
  private val sourceSetName: String,
  variantServices: VariantServices,
  override val dependencies: AgpTestSuiteDependencies?,
) : TestSuiteSourceSet.Assets {

  override fun getName(): String = sourceSetName

  private val assetsSourcesFolder =
    FlatSourceDirectoriesImpl(sourceSetName, variantServices, null).also {
      it.addSource(
        FileBasedDirectoryEntryImpl(
          name = sourceSetName,
          directory = File(variantServices.projectInfo.projectDirectory.asFile, "src/$sourceSetName"),
          filter = null,
          isUserAdded = false,
          shouldBeAddedToIdeModel = true,
        )
      )
    }

  override fun get(): FlatSourceDirectoriesImpl = assetsSourcesFolder

  override val type: TestSuiteSourceType
    get() = TestSuiteSourceType.ASSETS
}
