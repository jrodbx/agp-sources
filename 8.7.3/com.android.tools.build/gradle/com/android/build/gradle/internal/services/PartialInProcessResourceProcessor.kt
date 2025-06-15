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

package com.android.build.gradle.internal.services

import com.android.aaptcompiler.ResourceCompilerOptions
import com.android.aaptcompiler.canCompileResourceInJvm
import com.android.aaptcompiler.compileResource
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.res.blameLoggerFor
import com.android.builder.internal.aapt.AaptConvertConfig
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.builder.internal.aapt.v2.Aapt2
import com.android.ide.common.resources.CompileResourceRequest
import com.android.utils.ILogger
import javax.annotation.concurrent.ThreadSafe

/** Wraps an [Aapt2] to push some compile requests to the in-process resource compiler */
@ThreadSafe
class PartialInProcessResourceProcessor (val delegate: Aapt2):
    Aapt2 {
    override fun compile(request: CompileResourceRequest, logger: ILogger) {
        if (canCompileResourceInJvm(request.inputFile, request.isPngCrunching)) {
            val options = ResourceCompilerOptions(
                    pseudolocalize = request.isPseudoLocalize,
                    legacyMode = true,
                    sourcePath = request.sourcePath,
                    partialRFile = request.partialRFile,
            )

            val blameLogger = blameLoggerFor(request, LoggerWrapper.getLogger(this::class.java))
            compileResource(request.inputFile, request.outputDirectory, options, blameLogger)
        } else {
            delegate.compile(request, logger)
        }
    }

    override fun link(request: AaptPackageConfig, logger: ILogger) = delegate.link(request, logger)

    override fun convert(request: AaptConvertConfig, logger: ILogger) = delegate.convert(request,logger)
}
