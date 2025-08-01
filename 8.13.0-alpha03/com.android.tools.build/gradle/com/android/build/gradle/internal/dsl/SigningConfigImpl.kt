/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.SigningConfig
import com.android.build.gradle.internal.dsl.decorator.annotation.WithLazyInitialization
import java.io.File
import java.security.KeyStore

abstract class SigningConfigImpl: SigningConfig {

    protected abstract var _storeFile: String?

    @WithLazyInitialization
    @Suppress("unused") // the call is injected by DslDecorator
    protected fun lazyInit() {
        storeType = KeyStore.getDefaultType()
    }

    override var storeFile: File?
        get() = _storeFile?.let { File(it) }
        set(value) { _storeFile = value?.absolutePath }

}

internal fun SigningConfig.isPresent(): Boolean {
    return this.storeFile != null ||
            this.storePassword != null ||
            this.keyAlias != null ||
            this.keyPassword != null
}

