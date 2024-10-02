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

import com.android.build.gradle.internal.dsl.decorator.annotation.WithLazyInitialization
import com.android.build.gradle.internal.services.DslServices
import com.android.builder.signing.DefaultSigningConfig
import com.google.common.base.MoreObjects
import org.gradle.api.Named
import java.io.File
import java.io.Serializable
import java.security.KeyStore
import javax.inject.Inject

abstract class SigningConfig @Inject @WithLazyInitialization("lazyInit") constructor(name: String, dslServices: DslServices) : DefaultSigningConfig(name),
    Serializable, Named, com.android.build.api.dsl.ApkSigningConfig, InternalSigningConfig {

    fun lazyInit() {
        storeType = KeyStore.getDefaultType()
    }

    override fun initWith(that: com.android.build.api.dsl.SigningConfig) {
        if (that !is SigningConfig) {
            throw RuntimeException("Unexpected implementation type")
        }
        initWith(that as DefaultSigningConfig)
    }

    fun initWith(that: SigningConfig): SigningConfig {
        return initWith(that as DefaultSigningConfig)
    }

    fun initWith(that: DefaultSigningConfig): SigningConfig {
        setStoreFile(that.storeFile)
        setStorePassword(that.storePassword)
        setKeyAlias(that.keyAlias)
        setKeyPassword(that.keyPassword)
        // setting isV1SigningEnabled and isV2SigningEnabled here might incorrectly set
        // enableV1Signing and/or enableV2Signing, but they'll be reset correctly below if so.
        isV1SigningEnabled = that.isV1SigningEnabled
        isV2SigningEnabled = that.isV2SigningEnabled
        enableV1Signing = that.enableV1Signing
        enableV2Signing = that.enableV2Signing
        enableV3Signing = that.enableV3Signing
        enableV4Signing = that.enableV4Signing
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
            .add("enableV1Signing", enableV1Signing)
            .add("enableV2Signing", enableV2Signing)
            .add("enableV3Signing", enableV3Signing)
            .add("enableV4Signing", enableV4Signing)
            .toString()
    }

    // The following setters exist because of a bug where gradle is generating two groovy setters
    // for each field, since each value exists twice in the implemented interfaces
    // TODO - do we need setters for v3 and v4 here as well?

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
