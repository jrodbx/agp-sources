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

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.res.rewriteLinkException
import com.android.build.gradle.internal.services.Aapt2DaemonServiceKey
import com.android.build.gradle.internal.services.useAaptDaemon
import com.android.build.gradle.options.SyncOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.builder.internal.aapt.v2.Aapt2Exception
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property

abstract class Aapt2LinkRunnable : ProfileAwareWorkAction<Aapt2LinkRunnable.Params>() {

    override fun run() {
        runAapt2Link(
                parameters.aapt2ServiceKey.get(),
                parameters.request.get(),
                parameters.errorFormatMode.get()
        )
    }

    abstract class Params : ProfileAwareWorkAction.Parameters() {
        abstract val aapt2ServiceKey: Property<Aapt2DaemonServiceKey>
        abstract val request: Property<AaptPackageConfig>
        abstract val errorFormatMode: Property<SyncOptions.ErrorFormatMode>
    }
}

fun runAapt2Link(
        aapt2ServiceKey: Aapt2DaemonServiceKey,
        request: AaptPackageConfig,
        errorFormatMode: SyncOptions.ErrorFormatMode
) {
    val logger = Logging.getLogger(Aapt2LinkRunnable::class.java)
    useAaptDaemon(aapt2ServiceKey) { daemon ->
        try {
            daemon.link(request, LoggerWrapper(logger))
        } catch (exception: Aapt2Exception) {
            throw rewriteLinkException(
                    exception,
                    errorFormatMode,
                    null,
                    null,
                    emptyMap(),
                    logger
            )
        }
    }
}
