/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.builder.signing

import com.android.builder.core.BuilderConstants
import com.android.builder.model.SigningConfig
import com.google.common.base.MoreObjects
import java.io.File
import java.security.KeyStore

/**
 * SigningConfig encapsulates the information necessary to access certificates in a keystore file
 * that can be used to sign APKs.
 */
open class DefaultSigningConfig(private val mName: String) : SigningConfig {
    companion object {
        const val DEFAULT_PASSWORD = "android"
        const val DEFAULT_ALIAS = "AndroidDebugKey"

        /**
         * Creates a [DefaultSigningConfig] that uses the default debug alias and passwords.
         */
        @JvmStatic
        fun debugSigningConfig(storeFile: File): DefaultSigningConfig {
            val result = DefaultSigningConfig(BuilderConstants.DEBUG)
            result.storeFile = storeFile
            result.storePassword = DEFAULT_PASSWORD
            result.keyAlias = DEFAULT_ALIAS
            result.keyPassword = DEFAULT_PASSWORD
            return result
        }
    }

    override var storeFile: File? = null
    override var storePassword: String? = null
    override var keyAlias: String? = null
    override var keyPassword: String? = null
    override var storeType: String? = KeyStore.getDefaultType()

    override var isV1SigningEnabled = true
        set(value) {
            enableV1Signing = value
            field = value
        }
    override var isV2SigningEnabled = true
        set(value) {
            enableV2Signing = value
            field = value
        }

    var enableV1Signing: Boolean? = null
    var enableV2Signing: Boolean? = null
    var enableV3Signing: Boolean? = null
    var enableV4Signing: Boolean? = null

    override val isSigningReady: Boolean
        get() = storeFile != null &&
                storePassword != null &&
                keyAlias != null &&
                keyPassword != null

    override fun getName() = mName

    fun setStoreFile(storeFile: File?): DefaultSigningConfig {
        this.storeFile = storeFile
        return this
    }

    fun setStorePassword(storePassword: String?): DefaultSigningConfig {
        this.storePassword = storePassword
        return this
    }

    fun setKeyAlias(keyAlias: String?): DefaultSigningConfig {
        this.keyAlias = keyAlias
        return this
    }

    fun setKeyPassword(keyPassword: String?): DefaultSigningConfig {
        this.keyPassword = keyPassword
        return this
    }

    fun setStoreType(storeType: String?): DefaultSigningConfig {
        this.storeType = storeType
        return this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class.java != other::class.java) return false

        val that = other as DefaultSigningConfig

        if (keyAlias != that.keyAlias) {
            return false
        }

        if (keyPassword != that.keyPassword) {
            return false
        }

        if (storeFile != that.storeFile) {
            return false
        }

        if (storePassword != that.storePassword) {
            return false
        }

        if (storeType != that.storeType) {
            return false
        }
        if (isV1SigningEnabled != that.isV1SigningEnabled) return false
        if (isV2SigningEnabled != that.isV2SigningEnabled) return false
        if (enableV1Signing != that.enableV1Signing) return false
        if (enableV2Signing != that.enableV2Signing) return false
        if (enableV3Signing != that.enableV3Signing) return false
        if (enableV4Signing != that.enableV4Signing) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (storeFile?.hashCode() ?: 0)
        result = 31 * result + (storePassword?.hashCode() ?: 0)
        result = 31 * result + (keyAlias?.hashCode() ?: 0)
        result = 31 * result + (keyPassword?.hashCode() ?: 0)
        result = 31 * result + (storeType?.hashCode() ?: 0)
        result = 31 * result + (if (isV1SigningEnabled) 17 else 0)
        result = 31 * result + (if (isV2SigningEnabled) 17 else 0)
        result = 31 * result + (enableV1Signing?.hashCode() ?: 0)
        result = 31 * result + (enableV2Signing?.hashCode() ?: 0)
        result = 31 * result + (enableV3Signing?.hashCode() ?: 0)
        result = 31 * result + (enableV4Signing?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
            .add("storeFile", storeFile?.absolutePath)
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
}
