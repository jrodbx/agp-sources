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

package com.android.ide.common.resources

import com.android.utils.FileUtils
import java.io.File

/** A resource compilation service that simply copies files to the output directory. */
object CopyToOutputDirectoryResourceCompilationService : ResourceCompilationService {
    override fun submitCompile(request: CompileResourceRequest) {
        val out = compileOutputFor(request)
        FileUtils.mkdirs(out.parentFile)
        FileUtils.copyFile(request.inputFile, out)
    }

    override fun compileOutputFor(request: CompileResourceRequest): File {
        val parentDir = File(request.outputDirectory, request.inputDirectoryName)
        return File(parentDir, request.inputFile.name)
    }

    override fun close() {
    }
}
