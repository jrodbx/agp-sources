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

import org.gradle.api.Incubating
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

/**
 * Provides structured access to various AIDL tools such as
 * the aidl compiler executable and aidl framework.
 *
 * An instance of [Aidl] can be obtained via [AndroidComponentsExtension.sdkComponents]
 *
 * As an example , let's take a [Task] that runs aidl compiler:
 *
 * ```kotlin
 *  abstract class MyTask: DefaultTask() {
 *    @get:Nested
 *    abstract val aidlInput: Property<com.android.build.api.variant.Aidl>
 *
 *    @get:Inject
 *    abstract val execOperations: ExecOperations
 *
 *    @TaskAction
 *    fun execute() {
 *      val aidlExecutable = aidlInput.get().executable.get().asFile
 *      val aidlFramework = aidlInput.get().framework.get().asFile
 *
 *      // execute aidl binary with --help argument
 *      execOperations.exec { spec ->
 *        spec.commandLine(aidlExecutable)
 *        spec.args("--help")
 *      }
 *    }
 *  }
 *
 *  tasks.register<MyTask>("myTaskName") {
 *    // get an instance of Aidl
 *    this.aidlInput.set(androidComponents.sdkComponents.aidl)
 *  }
 *  ```
 */
@Incubating
interface Aidl {

    /**
     * Path to the [AIDL](https://developer.android.com/guide/components/aidl)
     * executable file from the Android SDK
     */
    @get:Internal
    val executable: Provider<RegularFile>

    /**
     * Path to the [AIDL](https://developer.android.com/guide/components/aidl)
     * framework file from the Android SDK
     */
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputFile
    val framework: Provider<RegularFile>

    /**
     * Version of build tools.
     * It is used as an input to allow correct build cache behaviour across different platforms
     */
    @get:Input
    val version: Provider<String>
}

