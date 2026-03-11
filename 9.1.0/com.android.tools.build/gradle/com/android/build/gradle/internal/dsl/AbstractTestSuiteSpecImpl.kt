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

package com.android.build.gradle.internal.dsl

import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty

abstract class AbstractTestSuiteSpecImpl(val name: String, val projectDirectory: Directory, val buildDirectory: DirectoryProperty) {
  protected val userAddedSourcesSets = mutableListOf<Directory>()

  /**
   * Adds a source set to the test suite assembly pipeline. The source folder is a root directory that should contain 'java', 'kotlin' and
   * 'res' subfolders depending on the type of source spec.
   *
   * We will eventually make this function public,
   *
   * @param sourceSet source path relative to the project directory.
   */
  fun addStaticSourceSet(sourceSet: String) {
    userAddedSourcesSets.add(projectDirectory.dir(sourceSet))
  }
}
