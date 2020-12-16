/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.lint

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import javax.inject.Inject

@Suppress("UnstableApiUsage")
abstract class AndroidLintWorkAction : WorkAction<AndroidLintWorkAction.LintWorkActionParameters> {

    abstract class LintWorkActionParameters : WorkParameters {
        abstract val arguments: ListProperty<String>
        abstract val classpath: ConfigurableFileCollection
        abstract val android: Property<Boolean>
        abstract val fatalOnly: Property<Boolean>
        abstract val lintFixBuildService: Property<LintFixBuildService>
    }

    @get:Inject
    abstract val process: ExecOperations

    override fun execute() {
        // The lint fix build service, when present, prevents multiple lint fix actions from running
        // concurrently potentially on the same sources.
        parameters.lintFixBuildService.orNull
        val logger = Logging.getLogger(this.javaClass)
        val arguments = parameters.arguments.get()
        logger.debug("Running lint " + arguments.joinToString(" "))
        val execResult = process.javaexec {
            it.classpath = parameters.classpath
            it.args = arguments
            it.main = "com.android.tools.lint.Main"
            it.isIgnoreExitValue = true
        }

        logger.debug("Lint returned ${execResult.exitValue}")
        if (execResult.exitValue == ERRNO_SUCCESS) {
            return
        }

        val message: String = when {
            !parameters.android.get() -> {
                """
                    Lint found errors in the project; aborting build.

                    Fix the issues identified by lint, or add the following to your build script to proceed with errors:
                    ...
                    lintOptions {
                        abortOnError false
                    }
                    ...
                    """.trimIndent()
            }
            parameters.fatalOnly.get() -> {
                """
                    Lint found fatal errors while assembling a release target.

                    To proceed, either fix the issues identified by lint, or modify your build script as follows:
                    ...
                    android {
                        lintOptions {
                            checkReleaseBuilds false
                            // Or, if you prefer, you can continue to check for errors in release builds,
                            // but continue the build even when errors are found:
                            abortOnError false
                        }
                    }
                    ...
                    """.trimIndent()
            }
            else -> {
                """
                    Lint found errors in the project; aborting build.

                    Fix the issues identified by lint, or add the following to your build script to proceed with errors:
                    ...
                    android {
                        lintOptions {
                            abortOnError false
                        }
                    }
                    ...
                    """.trimIndent()
            }
        }

        throw when (execResult.exitValue) {
            ERRNO_ERRORS -> RuntimeException(message)
            ERRNO_USAGE -> IllegalStateException("Internal Error: Unexpected lint usage")
            ERRNO_EXISTS -> throw RuntimeException("Unable to write lint output")
            ERRNO_HELP -> throw IllegalStateException("Internal error: Unexpected lint help call")
            ERRNO_INVALID_ARGS -> throw IllegalStateException("Internal error: Unexpected lint invalid arguments")
            ERRNO_CREATED_BASELINE -> throw RuntimeException("Aborting build since new baseline file was created")
            ERRNO_APPLIED_SUGGESTIONS -> throw RuntimeException("Aborting build since sources were modified to apply quickfixes after compilation")
            else -> throw IllegalStateException("Internal error: unexpected lint return value ${execResult.exitValue}")
        }
    }

    companion object {

        private const val ERRNO_SUCCESS = 0
        private const val ERRNO_ERRORS = 1
        private const val ERRNO_USAGE = 2
        private const val ERRNO_EXISTS = 3
        private const val ERRNO_HELP = 4
        private const val ERRNO_INVALID_ARGS = 5
        private const val ERRNO_CREATED_BASELINE = 6
        private const val ERRNO_APPLIED_SUGGESTIONS = 7
    }
}
