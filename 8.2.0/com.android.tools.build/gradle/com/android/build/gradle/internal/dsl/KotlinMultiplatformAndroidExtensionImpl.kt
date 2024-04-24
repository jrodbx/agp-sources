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

import com.android.build.api.dsl.HasConfigurableValue
import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilationBuilder
import com.android.build.api.dsl.KotlinMultiplatformAndroidExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidTestOnDevice
import com.android.build.api.dsl.KotlinMultiplatformAndroidTestOnJvm
import com.android.build.api.variant.impl.KmpAndroidCompilationType
import com.android.build.api.variant.impl.MutableAndroidVersion
import com.android.build.gradle.internal.coverage.JacocoOptions
import com.android.build.gradle.internal.dsl.decorator.annotation.WithLazyInitialization
import com.android.build.gradle.internal.packaging.getDefaultDebugKeystoreLocation
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.getBuildService
import com.android.builder.core.BuilderConstants
import com.android.builder.core.LibraryRequest
import com.android.builder.core.ToolsRevisionUtils
import com.android.builder.signing.DefaultSigningConfig
import javax.inject.Inject

internal abstract class KotlinMultiplatformAndroidExtensionImpl @Inject @WithLazyInitialization("lazyInit") constructor(
    private val dslServices: DslServices,
    private val compilationEnabledCallback: (KotlinMultiplatformAndroidCompilationBuilder) -> Unit,
): KotlinMultiplatformAndroidExtension, Lockable {

    fun lazyInit() {
        buildToolsVersion = ToolsRevisionUtils.DEFAULT_BUILD_TOOLS_REVISION.toString()
        DefaultSigningConfig.DebugSigningConfig(
            getBuildService(
                dslServices.buildServiceRegistry,
                AndroidLocationsBuildService::class.java
            ).get().getDefaultDebugKeystoreLocation()
        ).copyToSigningConfig(signingConfig)
    }

    abstract val libraryRequests: MutableList<LibraryRequest>

    override fun useLibrary(name: String) {
        useLibrary(name, true)
    }

    override fun useLibrary(name: String, required: Boolean) {
        libraryRequests.add(LibraryRequest(name, required))
    }

    var signingConfig = dslServices.newDecoratedInstance(
        SigningConfig::class.java, BuilderConstants.DEBUG, dslServices
    )

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

    override val testCoverage = dslServices.newInstance(JacocoOptions::class.java)

    internal var androidTestOnJvmOptions: KotlinMultiplatformAndroidTestOnJvmImpl? = null
    internal var androidTestOnDeviceOptions: KotlinMultiplatformAndroidTestOnDeviceImpl? = null
    internal var androidTestOnJvmBuilder: KotlinMultiplatformAndroidCompilationBuilderImpl? = null
    internal var androidTestOnDeviceBuilder: KotlinMultiplatformAndroidCompilationBuilderImpl? = null

    private fun withTestBuilder(
        compilationType: KmpAndroidCompilationType,
        previousConfiguration: KotlinMultiplatformAndroidCompilationBuilder?,
    ): KotlinMultiplatformAndroidCompilationBuilderImpl {
        previousConfiguration?.let {
            val type = when (compilationType) {
                KmpAndroidCompilationType.MAIN -> "main"
                KmpAndroidCompilationType.TEST_ON_JVM -> "jvm"
                KmpAndroidCompilationType.TEST_ON_DEVICE -> "device"
            }

            throw IllegalStateException(
                "Android tests on $type has already been enabled, and a corresponding compilation " +
                        "(`${it.compilationName}`) has already been created. You can create only " +
                        "one component of type android tests on $type. Alternatively, you can " +
                        "specify a dependency from the default sourceSet " +
                        "(`${it.defaultSourceSetName}`) to another sourceSet and it will be " +
                        "included in the compilation."
            )
        }

        return KotlinMultiplatformAndroidCompilationBuilderImpl(compilationType)
    }

    override fun withAndroidTestOnJvm(action: KotlinMultiplatformAndroidTestOnJvm.() -> Unit) {
        withAndroidTestOnJvmBuilder {  }.configure(action)
    }

    override fun withAndroidTestOnJvmBuilder(
        action: KotlinMultiplatformAndroidCompilationBuilder.() -> Unit
    ): HasConfigurableValue<KotlinMultiplatformAndroidTestOnJvm> {
        androidTestOnJvmBuilder = withTestBuilder(
            KmpAndroidCompilationType.TEST_ON_JVM,
            androidTestOnJvmBuilder
        )
        androidTestOnJvmOptions = dslServices.newDecoratedInstance(
            KotlinMultiplatformAndroidTestOnJvmImpl::class.java, dslServices
        )

        androidTestOnJvmBuilder!!.action()
        compilationEnabledCallback(androidTestOnJvmBuilder!!)
        return HasConfigurableValueImpl(androidTestOnJvmOptions!!)
    }

    override fun withAndroidTestOnDevice(action: KotlinMultiplatformAndroidTestOnDevice.() -> Unit) {
        withAndroidTestOnDeviceBuilder {  }.configure(action)
    }

    override fun withAndroidTestOnDeviceBuilder(
        action: KotlinMultiplatformAndroidCompilationBuilder.() -> Unit
    ): HasConfigurableValue<KotlinMultiplatformAndroidTestOnDevice> {
        androidTestOnDeviceBuilder = withTestBuilder(
            KmpAndroidCompilationType.TEST_ON_DEVICE,
            androidTestOnDeviceBuilder
        )
        androidTestOnDeviceOptions = dslServices.newDecoratedInstance(
            KotlinMultiplatformAndroidTestOnDeviceImpl::class.java, dslServices
        )

        androidTestOnDeviceBuilder!!.action()
        compilationEnabledCallback(androidTestOnDeviceBuilder!!)
        return HasConfigurableValueImpl(androidTestOnDeviceOptions!!)
    }
}
