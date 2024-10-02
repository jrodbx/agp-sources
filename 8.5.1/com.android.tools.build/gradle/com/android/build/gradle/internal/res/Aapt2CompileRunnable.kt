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
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.services.getLeasingAapt2
import com.android.builder.internal.aapt.v2.Aapt2Exception
import com.android.ide.common.resources.CompileResourceRequest
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class Aapt2CompileRunnable : ProfileAwareWorkAction<Aapt2CompileRunnable.Params>() {

    override fun run() {
        runAapt2Compile(
            parameters.aapt2Input.get(),
            parameters.requests.get(),
            parameters.enableBlame.getOrElse(false)
        )
    }

    abstract class Params : ProfileAwareWorkAction.Parameters() {
        abstract val aapt2Input: Property<Aapt2Input>
        abstract val requests: ListProperty<CompileResourceRequest>
        abstract val enableBlame: Property<Boolean>
    }
}

fun runAapt2Compile(
    aapt2Input: Aapt2Input,
    requests: List<CompileResourceRequest>,
    enableBlame: Boolean
) {
    val logger = Logging.getLogger(Aapt2CompileRunnable::class.java)
    val loggerWrapper = LoggerWrapper(logger)
    val daemon = aapt2Input.getLeasingAapt2()
    val errorFormatMode = aapt2Input.buildService.get().parameters.errorFormatMode.get()
    requests.forEach { request ->
        try {
            daemon.compile(request, loggerWrapper)
        } catch (exception: Aapt2Exception) {
            throw rewriteCompileException(
                exception,
                request,
                errorFormatMode,
                enableBlame,
                logger
            )
        }
    }
}
