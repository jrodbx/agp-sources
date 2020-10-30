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

package com.android.build.gradle.internal.res

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.services.Aapt2DaemonServiceKey
import com.android.build.gradle.internal.services.useAaptDaemon
import com.android.build.gradle.options.SyncOptions
import com.android.builder.internal.aapt.v2.Aapt2Exception
import com.android.ide.common.resources.CompileResourceRequest
import org.gradle.api.logging.Logging
import java.io.Serializable
import javax.inject.Inject

class Aapt2CompileRunnable @Inject constructor(
    private val params: Params
) : Runnable {

    override fun run() {
        val logger = Logging.getLogger(this::class.java)
        useAaptDaemon(params.aapt2ServiceKey) { daemon ->
            params.requests.forEach { request ->
                try {
                    daemon.compile(request, LoggerWrapper(logger))
                } catch (exception: Aapt2Exception) {
                    throw rewriteCompileException(
                        exception,
                        request,
                        params.errorFormatMode,
                        params.enableBlame,
                        logger
                    )
                }
            }
        }
    }

    class Params(
        val aapt2ServiceKey: Aapt2DaemonServiceKey,
        val requests: List<CompileResourceRequest>,
        val errorFormatMode: SyncOptions.ErrorFormatMode,
        val enableBlame: Boolean = false
    ) : Serializable
}
