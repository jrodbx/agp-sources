/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.build.gradle.external.gnumake

/**
 * Classification of a given command-line. Includes tool-specific interpretation of input and output
 * files.
 */
internal data class BuildStepInfo @JvmOverloads constructor(
    val command: CommandLine,
    val inputs: List<String>,
    val outputs: List<String>,
    // true only if this command can supply terminal input files.
    // For example, .c files specified by gcc with -c flag.
    val inputsAreSourceFiles: Boolean = false
) {

    val onlyInput: String get() = inputs.single()

    init {
        if (inputsAreSourceFiles) {
            if (inputs.size != 1) {
                throw RuntimeException(
                    """
                    GNUMAKE: Expected exactly one source file in compile step: $this
                    but received: 
                    ${inputs.joinToString("\n")}
                    in command:
                    $command
                    """.trimIndent()
                )
            }
        }
    }
}