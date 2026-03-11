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

package com.android.build.gradle.internal.dependency

import com.android.SdkConstants.FD_RES_NAVIGATION
import com.android.SdkConstants.RES_FOLDER
import com.android.utils.FileUtils
import java.io.File
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.work.DisableCachingByDefault

/** Extracting navigation.xml to process (substitute placeholder) it separately from all other resources */
@DisableCachingByDefault
abstract class ExtractNavigationXmlTransform : TransformAction<GenericTransformParameters> {

  @get:Classpath @get:InputArtifact abstract val inputArtifact: Provider<FileSystemLocation>

  override fun transform(transformOutputs: TransformOutputs) {
    val inputFile = inputArtifact.get().asFile
    if (inputFile.isDirectory) transformExplodedAar(inputFile, transformOutputs)
    else
      throw IllegalStateException(
        "Input file for navigation xml transform must be " + "a directory arr extracted to but got ${inputFile.path}"
      )
  }

  private fun transformExplodedAar(explodedAarDirectory: File, transformOutputs: TransformOutputs) {
    val resDir = explodedAarDirectory.resolve(RES_FOLDER)
    val navigationDir = resDir.resolve(FD_RES_NAVIGATION)
    val outputDir = File(transformOutputs.dir(explodedAarDirectory.name), FD_RES_NAVIGATION)
    FileUtils.mkdirs(outputDir)
    navigationDir.listFiles()?.forEach { it.copyTo(File(outputDir, it.name), overwrite = true) }
  }
}
