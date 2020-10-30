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

package com.android.build.gradle.internal.res.namespaced

import com.android.builder.internal.aapt.v2.Aapt2RenamingConventions
import java.io.File
import java.io.Serializable
import java.nio.file.Files
import javax.inject.Inject

class Aapt2CompileDeleteRunnable @Inject constructor(
        private val params: Params) : Runnable {

    override fun run() {
        val outDir = params.outputDirectory.toPath()
        params.deletedInputs.forEach {
            val compiledName = Aapt2RenamingConventions.compilationRename(it)
            Files.delete(outDir.resolve(compiledName))
            Files.delete(params.partialRDirectory.toPath().resolve("$compiledName-R.txt"))
        }
    }

    class Params(
            val outputDirectory: File,
            val deletedInputs: Iterable<File>,
            val partialRDirectory: File) : Serializable
}
