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

package com.android.builder.core

import com.google.common.base.Charsets
import com.google.common.collect.ImmutableList
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Paths

/** Class to produce program arguments when launching Desugar process, based on the input params. */
class DesugarProcessArgs(
        private val inputsToOutputs: Map<String, String>,
        private val classpath: List<String>,
        private val bootClasspath: List<String>,
        private val tmpDir: String,
        private val verbose: Boolean,
        private val minSdkVersion: Int,
        private val enableBugFixForJacoco: Boolean = true) : Serializable {

    companion object {
        const val MIN_SUPPORTED_API_TRY_WITH_RESOURCES = 19
        /**
         * Windows has limit of 2^15 = 32768 chars for the command line length.
         */
        const val MAX_CMD_LENGTH_FOR_WINDOWS = 32768
    }

    fun getArgs(isWindows: Boolean): List<String> {
        val args = mutableListOf<String>()

        if (verbose) {
            args.add("--verbose")
        }
        inputsToOutputs.forEach { input, out ->
            args.add("--input")
            args.add(input)
            args.add("--output")
            args.add(out)
        }
        classpath.forEach { c ->
            args.add("--classpath_entry")
            args.add(c)
        }
        bootClasspath.forEach { b ->
            args.add("--bootclasspath_entry")
            args.add(b)
        }

        args.add("--min_sdk_version")
        args.add(Integer.toString(minSdkVersion))
        if (minSdkVersion < MIN_SUPPORTED_API_TRY_WITH_RESOURCES) {
            args.add("--desugar_try_with_resources_if_needed")
        } else {
            args.add("--nodesugar_try_with_resources_if_needed")
        }
        args.add("--desugar_try_with_resources_omit_runtime_classes")
        if (enableBugFixForJacoco) {
            // fix for b/62623509
            args.add("--legacy_jacoco_fix")
        }
        args.add("--copy_bridges_from_classpath")

        return if (!isWindows || args.map { it.length }.sum() <= MAX_CMD_LENGTH_FOR_WINDOWS) {
            args
        } else {
            val pathTmpDir = Paths.get(tmpDir)
            if (!Files.exists(pathTmpDir)) {
                Files.createDirectories(pathTmpDir)
            }
            val argsFile = Files.createTempFile(pathTmpDir, "desugar_args", "")
            Files.write(argsFile, args, Charsets.UTF_8)

            ImmutableList.of("@" + argsFile.toString())
        }
    }
}
