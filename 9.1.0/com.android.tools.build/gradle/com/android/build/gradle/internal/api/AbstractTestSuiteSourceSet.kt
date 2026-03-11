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

import com.android.build.api.variant.impl.FileBasedDirectoryEntryImpl
import com.android.build.api.variant.impl.FlatSourceDirectoriesForJavaImpl
import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.gradle.internal.services.VariantServices
import java.io.File
import kotlin.collections.forEach
import org.gradle.api.file.Directory

abstract class AbstractTestSuiteSourceSet(
  protected val sourceSetName: String,
  variantServices: VariantServices,
  val userAddedSourceSets: Collection<Directory>,
  val javaEnabled: Boolean,
  val kotlinEnabled: Boolean,
) {
  fun getName(): String = sourceSetName

  val defaultTopLevelFolder = File(variantServices.projectInfo.projectDirectory.asFile, "src/$sourceSetName")

  protected fun createJavaSources(variantServices: VariantServices) =
    FlatSourceDirectoriesForJavaImpl(sourceSetName, variantServices, null).also {
      it.addSource(
        FileBasedDirectoryEntryImpl(
          name = sourceSetName,
          directory = File(defaultTopLevelFolder, "java"),
          filter = null,
          isUserAdded = false,
          shouldBeAddedToIdeModel = true,
        )
      )
      userAddedSourceSets.forEach { userAddedFolder ->
        it.addSource(
          FileBasedDirectoryEntryImpl(
            name = sourceSetName,
            directory = File(userAddedFolder.asFile, "java"),
            filter = null,
            isUserAdded = true,
            shouldBeAddedToIdeModel = true,
          )
        )
      }
    }

  protected fun createKotlinSources(variantServices: VariantServices) =
    FlatSourceDirectoriesImpl(sourceSetName, variantServices, null).also {
      it.addSource(
        FileBasedDirectoryEntryImpl(
          name = sourceSetName,
          directory = File(defaultTopLevelFolder, "kotlin"),
          filter = null,
          isUserAdded = false,
          shouldBeAddedToIdeModel = true,
        )
      )
      userAddedSourceSets.forEach { userAddedSourceSet ->
        it.addSource(
          FileBasedDirectoryEntryImpl(
            name = sourceSetName,
            directory = File(userAddedSourceSet.asFile, "kotlin"),
            filter = null,
            isUserAdded = true,
            shouldBeAddedToIdeModel = true,
          )
        )
      }
    }

  protected val resourcesSourcesFolder =
    FlatSourceDirectoriesImpl(sourceSetName, variantServices, null).also {
      it.addSource(
        FileBasedDirectoryEntryImpl(
          name = sourceSetName,
          directory = File(defaultTopLevelFolder, "resources"),
          filter = null,
          isUserAdded = false,
          shouldBeAddedToIdeModel = true,
        )
      )
    }

  init {
    userAddedSourceSets.forEach { userAddedSourceSet ->
      resourcesSourcesFolder.addSource(
        FileBasedDirectoryEntryImpl(
          name = sourceSetName,
          directory = File(userAddedSourceSet.asFile, "resources"),
          filter = null,
          isUserAdded = true,
          shouldBeAddedToIdeModel = true,
        )
      )
    }
  }
}
