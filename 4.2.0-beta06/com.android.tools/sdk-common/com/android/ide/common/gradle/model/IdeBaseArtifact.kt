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

import java.io.File
import java.io.Serializable

interface IdeBaseArtifact : Serializable {
  /** Name of the artifact. This should match [ArtifactMetaData.getName].  */
  val name: String

  /** @return the name of the task used to compile the code.
   */
  val compileTaskName: String

  /**
   * Returns the name of the task used to generate the artifact output(s).
   *
   * @return the name of the task.
   */
  val assembleTaskName: String

  /**
   * Returns the absolute path for the listing file that will get updated after each build. The
   * model file will contain deployment related information like applicationId, list of APKs.
   *
   * @return the path to a json file.
   */
  val assembleTaskOutputListingFile: String

  /**
   * Returns the folder containing the class files. This is the output of the java compilation.
   *
   * @return a folder.
   */
  val classesFolder: File

  /**
   * Folders or jars containing additional classes (e.g., R.jar or those registered by third-party
   * plugins like Kotlin).
   */
  val additionalClassesFolders: Collection<File>

  /**
   * Returns the folder containing resource files that classes form this artifact expect to find
   * on the classpath.
   */
  val javaResourcesFolder: File?

  /**
   * A SourceProvider specific to the variant. This can be null if there is no flavors as the
   * "variant" is equal to the build type.
   *
   * @return the variant specific source provider
   */
  val variantSourceProvider: IdeSourceProvider?

  /**
   * A SourceProvider specific to the flavor combination.
   *
   *
   * For instance if there are 2 dimensions, then this would be Flavor1Flavor2, and would be
   * common to all variant using these two flavors and any of the build type.
   *
   *
   * This can be null if there is less than 2 flavors.
   *
   * @return the multi flavor specific source provider
   */
  val multiFlavorSourceProvider: IdeSourceProvider?

  /**
   * Returns names of tasks that need to be run when setting up the IDE project. After these tasks
   * have run, all the generated source files etc. that the IDE needs to know about should be in
   * place.
   */
  val ideSetupTaskNames: Collection<String>

  /**
   * Returns all the source folders that are generated. This is typically folders for the R, the
   * aidl classes, and the renderscript classes.
   *
   * @return a list of folders.
   * @since 1.2
   */
  val generatedSourceFolders: Collection<File>
  val isTestArtifact: Boolean
  val level2Dependencies: IdeDependencies

  // See: http://b/71706169
  fun addGeneratedSourceFolder(generatedSourceFolder: File)
}