/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.api.artifact.SingleArtifact
import java.nio.charset.StandardCharsets.UTF_8

import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.tools.build.libraries.metadata.AppDependencies
import org.gradle.api.tasks.OutputFile
import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeysetHandle
import java.io.FileOutputStream
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files;
import java.util.zip.DeflaterOutputStream

/**
 * Task that generates SDK dependency block value for APKs.
 *
 * SDK dependency block is a block in APK signature v2 block that stores SDK dependency information
 * of the APK.
 */
@DisableCachingByDefault
abstract class SdkDependencyDataGeneratorTask : NonIncrementalTask() {

  companion object {
    init {
      HybridConfig.register()
    }

    @JvmStatic
    private val publicKey: ByteArray = byteArrayOf(8, -84, -65, -63, -105, 10, 18, -37, 1, 10, -50,
    1, 10, 61, 116, 121, 112, 101, 46, 103, 111, 111, 103, 108, 101, 97, 112, 105, 115, 46, 99, 111,
    109, 47, 103, 111, 111, 103, 108, 101, 46, 99, 114, 121, 112, 116, 111, 46, 116, 105, 110, 107,
    46, 69, 99, 105, 101, 115, 65, 101, 97, 100, 72, 107, 100, 102, 80, 117, 98, 108, 105, 99, 75,
    101, 121, 18, -118, 1, 18, 68, 10, 4, 8, 2, 16, 3, 18, 58, 18, 56, 10, 48, 116, 121, 112, 101,
    46, 103, 111, 111, 103, 108, 101, 97, 112, 105, 115, 46, 99, 111, 109, 47, 103, 111, 111, 103,
    108, 101, 46, 99, 114, 121, 112, 116, 111, 46, 116, 105, 110, 107, 46, 65, 101, 115, 71, 99,
    109, 75, 101, 121, 18, 2, 16, 16, 24, 1, 24, 3, 26, 32, -62, 68, 123, 18, 86, -43, -7, 122, 73,
    -18, 19, -128, -26, -19, 36, 108, -6, 81, 13, -31, 117, 27, -126, -83, 114, -115, 8, 35, 21, -7,
    78, -39, 34, 32, 51, 48, 123, 99, -84, -95, 126, 10, -70, -74, 47, -15, -28, 124, -83, 23, 78,
    -3, 59, -91, 38, -103, -90, 69, 67, -28, -20, -95, -90, -83, -115, -52, 24, 3, 16, 1, 24, -84,
    -65, -63, -105, 10, 32, 4)
  }


  // Optional context. To ensure the correct decryption of a ciphertext the same value must be
  // provided for the decryption operation.
  private val context: String = "SDK_DEPENDENCY_INFO"

  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val dependencies: RegularFileProperty

  @get:OutputFile
  abstract val sdkDependencyData: RegularFileProperty

  @get:OutputFile
  abstract val sdkDependencyDataPublic: RegularFileProperty

  public override fun doTaskAction() {
    FileOutputStream(sdkDependencyData.get().asFile).use {
      it.write(encrypt(compress(Files.readAllBytes(dependencies.get().asFile.toPath()))))
    }

    PrintStream(sdkDependencyDataPublic.get().asFile).use {
      it.print("# List of SDK dependencies of this app, this information is also included in an"
        + " encrypted form in the APK.\n# For more information visit:"
        + " https://d.android.com/r/tools/dependency-metadata\n\n")
      dependencies.get().asFile.inputStream().buffered().use { dependenciesInputStream ->
        it.print(AppDependencies.parseFrom(dependenciesInputStream).toString())
      }
    }
  }

  private fun compress(data: ByteArray): ByteArray {
    val outputStream = ByteArrayOutputStream()
    DeflaterOutputStream(outputStream).use {
      it.write(data)
    }
    return outputStream.toByteArray()
  }

  private fun encrypt(data: ByteArray): ByteArray {
    val hybridEncrypt = KeysetHandle.readNoSecret(
      BinaryKeysetReader.withBytes(publicKey)).getPrimitive(HybridEncrypt::class.java)
    return hybridEncrypt.encrypt(data, context.toByteArray(UTF_8))
  }

  class CreationAction(
    creationConfig: VariantCreationConfig
  ) : VariantTaskCreationAction<SdkDependencyDataGeneratorTask, VariantCreationConfig>(
    creationConfig
  ) {
    override val name: String = computeTaskName("sdk", "DependencyData")
    override val type: Class<SdkDependencyDataGeneratorTask> = SdkDependencyDataGeneratorTask::class.java

    override fun handleProvider(
      taskProvider: TaskProvider<SdkDependencyDataGeneratorTask>
    ) {
      super.handleProvider(taskProvider)
      creationConfig.artifacts.setInitialProvider(
        taskProvider,
        SdkDependencyDataGeneratorTask::sdkDependencyData
      ).withName("sdkDependencyData.pb").on(InternalArtifactType.SDK_DEPENDENCY_DATA)

      creationConfig.artifacts.setInitialProvider(
        taskProvider,
        SdkDependencyDataGeneratorTask::sdkDependencyDataPublic
      ).withName("sdkDependencies.txt").on(InternalArtifactType.SDK_DEPENDENCY_DATA_PUBLIC)
    }

    override fun configure(
      task: SdkDependencyDataGeneratorTask
    ) {
      super.configure(task)
      creationConfig.artifacts.setTaskInputToFinalProduct(
          SingleArtifact.METADATA_LIBRARY_DEPENDENCIES_REPORT, task.dependencies)
    }
  }
}
