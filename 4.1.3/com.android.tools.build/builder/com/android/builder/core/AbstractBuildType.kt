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
package com.android.builder.core

import com.android.builder.internal.BaseConfigImpl
import com.android.builder.model.BuildType
import com.android.builder.model.SigningConfig
import com.google.common.base.MoreObjects

/**
 * Builder-level implementation of BuildType.
 */
@Deprecated("This is deprecated, use DSL objects directly.")
abstract class AbstractBuildType : BaseConfigImpl(), BuildType {

    /**
     * Copies all properties from the given build type.
     *
     * It can be used like this:
     *
     * ```
     * android.buildTypes {
     *     customBuildType {
     *         initWith debug
     *         // customize...
     *     }
     * }
     * ```
     */
    open fun initWith(that: BuildType): AbstractBuildType {
        _initWith(that)
        isDebuggable = that.isDebuggable
        isTestCoverageEnabled = that.isTestCoverageEnabled
        isJniDebuggable = that.isJniDebuggable
        isRenderscriptDebuggable = that.isRenderscriptDebuggable
        renderscriptOptimLevel = that.renderscriptOptimLevel
        versionNameSuffix = that.versionNameSuffix
        isMinifyEnabled = that.isMinifyEnabled
        isZipAlignEnabled = (that.isZipAlignEnabled)
        setSigningConfig(that.signingConfig)
        isEmbedMicroApp = that.isEmbedMicroApp
        isPseudoLocalesEnabled = that.isPseudoLocalesEnabled
        return this
    }

    abstract override fun getName(): String

    abstract override var isDebuggable: Boolean

    fun setDebuggable(debuggable: Boolean): BuildType {
        isDebuggable = debuggable
        return this
    }

    open fun debuggable(debuggable: Boolean) {
        isDebuggable = debuggable
    }

    abstract override var isTestCoverageEnabled: Boolean

    abstract override var isPseudoLocalesEnabled: Boolean

    abstract override var isJniDebuggable: Boolean

    fun setJniDebuggable(jniDebugBuild: Boolean): BuildType {
        isJniDebuggable = jniDebugBuild
        return this
    }

    open fun jniDebuggable(jniDebuggable: Boolean) {
        isJniDebuggable = jniDebuggable
    }

    abstract override var isRenderscriptDebuggable: Boolean

    fun setRenderscriptDebuggable(renderscriptDebugBuild: Boolean): BuildType {
        isRenderscriptDebuggable = renderscriptDebugBuild
        return this
    }

    open fun renderscriptDebuggable(renderscriptDebugBuild: Boolean) {
        isRenderscriptDebuggable = renderscriptDebugBuild
    }

    abstract override var renderscriptOptimLevel: Int

    abstract override var isMinifyEnabled: Boolean

    fun setMinifyEnabled(enabled: Boolean): BuildType {
        isMinifyEnabled = enabled
        return this
    }

    open fun minifyEnabled(enabled: Boolean) {
        isMinifyEnabled = enabled
    }

    abstract override var isZipAlignEnabled: Boolean

    fun setZipAlignEnabled(zipAlign: Boolean): BuildType {
        isZipAlignEnabled = zipAlign
        return this
    }

    open fun zipAlignEnabled(zipAlign: Boolean) {
        isZipAlignEnabled = zipAlign
    }

    abstract override val signingConfig: SigningConfig?

    abstract fun setSigningConfig(signingConfig: SigningConfig?): BuildType

    abstract override var isEmbedMicroApp: Boolean

    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
            .add("name", name)
            .add("debuggable", isDebuggable)
            .add("testCoverageEnabled", isTestCoverageEnabled)
            .add("jniDebuggable", isJniDebuggable)
            .add("pseudoLocalesEnabled", isPseudoLocalesEnabled)
            .add("renderscriptDebuggable", isRenderscriptDebuggable)
            .add("renderscriptOptimLevel", renderscriptOptimLevel)
            .add("minifyEnabled", isMinifyEnabled)
            .add("zipAlignEnabled", isZipAlignEnabled)
            .add("signingConfig", signingConfig)
            .add("embedMicroApp", isEmbedMicroApp)
            .add("mBuildConfigFields", buildConfigFields)
            .add("mResValues", resValues)
            .add("mProguardFiles", proguardFiles)
            .add("mConsumerProguardFiles", consumerProguardFiles)
            .add("mManifestPlaceholders", manifestPlaceholders)
            .toString()
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
