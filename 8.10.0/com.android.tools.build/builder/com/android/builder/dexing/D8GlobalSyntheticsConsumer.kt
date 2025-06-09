/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.builder.dexing

import com.android.tools.r8.ByteDataView
import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.GlobalSyntheticsConsumer
import com.android.tools.r8.references.ClassReference
import com.android.utils.FileUtils
import java.io.FileOutputStream
import java.nio.file.Path

/**
 * [D8GlobalSyntheticsConsumer] is used by D8 to write global synthetics to our custom location.
 */
class D8GlobalSyntheticsConsumer(val globalSyntheticsOutput: Path) : GlobalSyntheticsConsumer {

    override fun accept(
        data: ByteDataView?,
        context: ClassReference?,
        handler: DiagnosticsHandler?
    ) {
        if (data == null) return

        // context exist if and only if d8 is running with dexPerClassFile mode
        val outputFile = if (context != null) {
            // when context exist, each global file name is computed based on the class that
            // global synthetics are generated from
            globalSyntheticsOutput.resolve(
                // context.binaryName + .class is guaranteed to be the same as classFileRelativePath
                DexFilePerClassFile
                    .getGlobalSyntheticOutputRelativePath(context.binaryName + ".class")
            )
        } else {
            // when context doesn't exist, globals are output to a single file under
            // globalSyntheticsOutput directory
            globalSyntheticsOutput.resolve(bundledGlobalSyntheticsFileName + globalSyntheticsFileExtension)
        }.toFile()

        FileUtils.mkdirs(outputFile.parentFile)
        // accept function is only called at most once for given context, so we can rewrite
        // modified files
        FileUtils.deleteIfExists(outputFile)

        FileOutputStream(outputFile).buffered().use {
            it.write(
                data.buffer,
                data.offset,
                data.length
            )
        }
    }
}

private const val bundledGlobalSyntheticsFileName = "global"
const val globalSyntheticsFileExtension = ".globals"
