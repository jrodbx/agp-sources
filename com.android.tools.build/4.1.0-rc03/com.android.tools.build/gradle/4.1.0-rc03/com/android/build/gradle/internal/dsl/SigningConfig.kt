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

package com.android.build.gradle.internal.dsl

import com.android.builder.signing.DefaultSigningConfig
import com.google.common.base.MoreObjects
import org.gradle.api.Named
import java.io.File
import java.io.Serializable
import javax.inject.Inject

open class SigningConfig @Inject constructor(name: String) : DefaultSigningConfig(name),
    Serializable, Named, com.android.build.api.dsl.SigningConfig {

    fun initWith(that: com.android.builder.model.SigningConfig): SigningConfig? {
        setStoreFile(that.storeFile)
        setStorePassword(that.storePassword)
        setKeyAlias(that.keyAlias)
        setKeyPassword(that.keyPassword)
        internalSetV1SigningEnabled(that.isV1SigningEnabled)
        internalSetV2SigningEnabled(that.isV2SigningEnabled)
        setStoreType(that.storeType)
        return this
    }

    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
            .add("name", name)
            .add("storeFile", storeFile?.absolutePath ?: "null")
            .add("storePassword", storePassword)
            .add("keyAlias", keyAlias)
            .add("keyPassword", keyPassword)
            .add("storeType", storeType)
            .add("v1SigningEnabled", isV1SigningEnabled)
            .add("v2SigningEnabled", isV2SigningEnabled)
            .add("v1SigningConfigured", isV1SigningConfigured)
            .add("v2SigningConfigured", isV2SigningConfigured)
            .toString()
    }

    // The following setters exist because of a bug where gradle is generating two groovy setters
    // for each field, since each value exists twice in the implemented interfaces

    open fun storeFile(storeFile: File?) {
        this.storeFile = storeFile
    }

    open fun storePassword(storePassword: String?) {
        this.storePassword = storePassword
    }

    open fun keyAlias(keyAlias: String?) {
        this.keyAlias = keyAlias
    }

    open fun keyPassword(keyPassword: String?) {
        this.keyPassword = keyPassword
    }

    open fun storeType(storeType: String?) {
        this.storeType = storeType
    }
}