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

package com.android.builder.internal.aapt.v2

import com.android.builder.internal.aapt.AaptConvertConfig
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.ide.common.resources.CompileResourceRequest
import com.android.utils.ILogger

/**
 * The operations AAPT2 can perform.
 *
 * Methods throw [Aapt2Exception] for invalid input (e.g. syntax error in a source file)
 * and [Aapt2InternalException] if there is an internal issue running AAPT itself.
 */
interface Aapt2 {
    /** Perform the requested compilation. Throws [Aapt2Exception] on failure */
    fun compile(request: CompileResourceRequest, logger: ILogger)

    /** Perform the requested linking. Throws [Aapt2Exception] on failure. */
    fun link(request: AaptPackageConfig, logger: ILogger)

    /**
     * Perform the requested conversion between proto/binary resources. Throws [Aapt2Exception] on
     * failure.
     */
    fun convert(request: AaptConvertConfig, logger: ILogger)
}