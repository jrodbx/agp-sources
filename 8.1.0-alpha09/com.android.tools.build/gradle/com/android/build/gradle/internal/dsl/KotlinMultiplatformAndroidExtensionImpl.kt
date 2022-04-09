/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.impl.MutableAndroidVersion
import com.android.build.gradle.internal.dsl.decorator.annotation.WithLazyInitialization
import com.android.build.gradle.internal.packaging.getDefaultDebugKeystoreLocation
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.getBuildService
import com.android.builder.core.LibraryRequest
import com.android.builder.core.ToolsRevisionUtils
import com.android.builder.signing.DefaultSigningConfig
import javax.inject.Inject

abstract class KotlinMultiplatformAndroidExtensionImpl @Inject @WithLazyInitialization("lazyInit") constructor(
    private val dslServices: DslServices
): KotlinMultiplatformAndroidExtension {

    fun lazyInit() {
        buildToolsVersion = ToolsRevisionUtils.DEFAULT_BUILD_TOOLS_REVISION.toString()
        DefaultSigningConfig.DebugSigningConfig(
            getBuildService(
                dslServices.buildServiceRegistry,
                AndroidLocationsBuildService::class.java
            ).get().getDefaultDebugKeystoreLocation()
        ).copyToSigningConfig(signingConfig)
    }

    override val testOptions: TestOptions =
        dslServices.newInstance(TestOptions::class.java, dslServices)

    abstract val libraryRequests: MutableList<LibraryRequest>

    override fun useLibrary(name: String) {
        useLibrary(name, true)
    }

    override fun useLibrary(name: String, required: Boolean) {
        libraryRequests.add(LibraryRequest(name, required))
    }

    override val installation = dslServices.newDecoratedInstance(
        AdbOptions::class.java,
        dslServices
    )

    var signingConfig = dslServices.newDecoratedInstance(
        SigningConfig::class.java, "kotlinAndroidInstrumentation", dslServices
    )

    override fun testSigningConfig(action: ApkSigningConfig.() -> Unit) {
        action.invoke(signingConfig)
    }

    internal val minSdkVersion: MutableAndroidVersion
        get() = mutableMinSdk?.sanitize()?.let { MutableAndroidVersion(it.apiLevel, it.codename) }
            ?: MutableAndroidVersion(1)

    private var mutableMinSdk: MutableAndroidVersion? = null

    override var minSdk: Int?
        get() = mutableMinSdk?.api
        set(value) {
            val min =
                mutableMinSdk ?: MutableAndroidVersion(null, null).also {
                    mutableMinSdk = it
                }
            min.codename = null
            min.api = value
        }

    override var minSdkPreview: String?
        get() = mutableMinSdk?.codename
        set(value) {
            val min =
                mutableMinSdk ?: MutableAndroidVersion(null, null).also {
                    mutableMinSdk = it
                }
            min.codename = value
            min.api = null
        }

    internal val testTargetSdkVersion: AndroidVersion?
        get() = mutableTargetSdk?.sanitize()

    private var mutableTargetSdk: MutableAndroidVersion? = null

    override var testTargetSdk: Int?
        get() = mutableTargetSdk?.api
        set(value) {
            val target =
                mutableTargetSdk ?: MutableAndroidVersion(null, null).also {
                    mutableTargetSdk = it
                }
            target.codename = null
            target.api = value
        }

    override var testTargetSdkPreview: String?
        get() = mutableTargetSdk?.codename
        set(value) {
            val target =
                mutableTargetSdk ?: MutableAndroidVersion(null, null).also {
                    mutableTargetSdk = it
                }
            target.codename = value
            target.api = null
        }
}
