/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.build.gradle.internal.r8.TargetedR8Rules
import com.android.build.gradle.internal.r8.TargetedR8RulesReadWriter
import com.android.build.gradle.internal.r8.TargetedR8RulesReadWriter.createJarContents
import com.android.utils.FileUtils
import javax.inject.Inject
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input

@CacheableTransform
abstract class ExtractProGuardRulesTransform @Inject constructor() : TransformAction<ExtractProGuardRulesTransform.Parameters> {

  interface Parameters : GenericTransformParameters {
    @get:Input val filterOutGlobalRules: Property<Boolean>
  }

  @get:Classpath @get:InputArtifact abstract val inputArtifact: Provider<FileSystemLocation>

  override fun transform(transformOutputs: TransformOutputs) {
    val targetedR8Rules =
      TargetedR8RulesReadWriter.readFromJar(
        inputArtifact.get().asFile,
        isClassesJarInAar = false,
        shouldRemoveBannedGlobals = parameters.filterOutGlobalRules.get(),
      )
    writeTargetedR8Rules(targetedR8Rules, transformOutputs)
  }

  companion object {

    fun writeTargetedR8Rules(targetedR8Rules: TargetedR8Rules, transformOutputs: TransformOutputs, isClassesJarInAar: Boolean = false) {
      // Create a subdirectory called "lib" as FilterShrinkerRulesTransform expects this structure
      val outputDirectory = transformOutputs.dir("shrink-rules").resolve("lib")
      FileUtils.mkdirs(outputDirectory)

      targetedR8Rules.createJarContents(isClassesJarInAar = isClassesJarInAar).forEach { (relativePath, contents) ->
        outputDirectory.resolve(relativePath).run {
          FileUtils.mkdirs(parentFile)
          writeBytes(contents)
        }
      }
    }
  }
}
