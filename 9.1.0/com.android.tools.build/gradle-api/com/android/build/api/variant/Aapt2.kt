/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.api.variant

import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal

/**
 * Provides structured access to AAPT2 tool.
 *
 * An instance of [Aapt2] can be obtained via [AndroidComponentsExtension.sdkComponents]
 *
 * For example, this is the structure of a [org.gradle.api.Task] that runs aapt2:
 * ```kotlin
 *  abstract class MyTaskUsingAapt2: DefaultTask() {
 *    @get:Nested
 *    abstract val aapt2Input: Property<com.android.build.api.variant.Aapt2>
 *
 *    @get:Inject
 *    abstract val execOperations: ExecOperations
 *
 *    @TaskAction
 *    fun execute() {
 *      val aapt2Executable = aapt2Input.get().executable.get().asFile
 *
 *      // execute aapt2 help
 *      execOperations.exec { spec ->
 *        spec.commandLine(aapt2Executable)
 *        spec.args("-h")
 *        spec.isIgnoreExitValue = true // -h returns exit value 1
 *      }
 *    }
 *  }
 *
 *  tasks.register<MyTaskUsingAapt2>("myTaskUsingAapt2") {
 *    // get an instance of Aapt2
 *    this.aapt2Input.set(androidComponents.sdkComponents.aapt2)
 *  }
 *  ```
 */
interface Aapt2 {

  /** Path to the [AAPT2](https://developer.android.com/tools/aapt2) executable file from the Android SDK */
  @get:Internal val executable: Provider<RegularFile>

  /** Version of build tools. It is used as an input to allow correct build cache behaviour across different platforms */
  @get:Input val version: Provider<String>
}
