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
            isV1SigningConfigured = true
            field = value
        }
    override var isV2SigningEnabled = true
        set(value) {
            isV2SigningConfigured = true
            field = value
        }

    /** Returns whether v1SigningEnabled is configured by the user through DSL. */
    var isV1SigningConfigured = false
        private set

    /** Returns whether v2SigningEnabled is configured by the user through DSL. */
    var isV2SigningConfigured = false
        private set

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

    /** Note: this function is only used by AGP internally, not by users. */
    protected fun internalSetV1SigningEnabled(enabled: Boolean) {
        val isV1SigningConfigured = this.isV1SigningConfigured
        isV1SigningEnabled = enabled
        this.isV1SigningConfigured = isV1SigningConfigured
    }

    /** Note: this function is only used by AGP internally, not by users. */
    protected fun internalSetV2SigningEnabled(enabled: Boolean) {
        val isV2SigningConfigured = this.isV2SigningConfigured
        isV2SigningEnabled = enabled
        this.isV2SigningConfigured = isV2SigningConfigured
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
        if (isV1SigningConfigured != that.isV1SigningConfigured) return false
        if (isV2SigningConfigured != that.isV2SigningConfigured) return false

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
        result = 31 * result + (if (isV1SigningConfigured) 17 else 0)
        result = 31 * result + (if (isV2SigningConfigured) 17 else 0)
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
            .add("v1SigningConfigured", isV1SigningConfigured)
            .add("v2SigningConfigured", isV2SigningConfigured)
            .toString()
    }
}
