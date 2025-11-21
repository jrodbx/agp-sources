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

import com.android.build.api.dsl.DependencyVariantSelection
import com.android.build.api.dsl.HasConfigurableValue
import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilationBuilder
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidDeviceTest
import com.android.build.api.dsl.KotlinMultiplatformAndroidHostTest
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
import com.android.builder.core.apiVersionFromString
import com.android.builder.signing.DefaultSigningConfig
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

internal abstract class KotlinMultiplatformAndroidLibraryExtensionImpl @Inject @WithLazyInitialization("lazyInit") constructor(
    private val dslServices: DslServices,
    objectFactory: ObjectFactory,
    private val compilationEnabledCallback: (KotlinMultiplatformAndroidCompilationBuilder) -> Unit,
): KotlinMultiplatformAndroidLibraryExtension, Lockable {

    fun lazyInit() {
        buildToolsVersion = ToolsRevisionUtils.DEFAULT_BUILD_TOOLS_REVISION.toString()
        DefaultSigningConfig.DebugSigningConfig(
            getBuildService(
                dslServices.buildServiceRegistry,
                AndroidLocationsBuildService::class.java
            ).get().getDefaultDebugKeystoreLocation()
        ).copyToSigningConfig(signingConfig)
    }

    override val dependencyVariantSelection: DependencyVariantSelection =
        dslServices.newDecoratedInstance(DependencyVariantSelectionImpl::class.java, dslServices, objectFactory)

    override fun dependencyVariantSelection(action: DependencyVariantSelection.() -> Unit) {
        action.invoke(dependencyVariantSelection)
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
            val apiVersion = apiVersionFromString(value)
            val min =
                mutableMinSdk ?: MutableAndroidVersion(null, null).also {
                    mutableMinSdk = it
                }
            min.codename = apiVersion?.codename
            min.api = apiVersion?.apiLevel
        }

    override val testCoverage = dslServices.newInstance(JacocoOptions::class.java)

    internal var androidTestOnJvmOptions: KotlinMultiplatformAndroidHostTestImpl? = null
    internal var androidTestOnDeviceOptions: KotlinMultiplatformAndroidDeviceTestImpl? = null
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

    override fun withHostTest(action: KotlinMultiplatformAndroidHostTest.() -> Unit) {
        withHostTestBuilder {  }.configure(action)
    }

    @Deprecated("Use withHostTest. This api will be removed in AGP 9.0",
        ReplaceWith("withHostTest(action)")
    )
    override fun withAndroidTestOnJvm(action: KotlinMultiplatformAndroidHostTest.() -> Unit) {
        return withHostTest(action)
    }

    override fun withHostTestBuilder(
        action: KotlinMultiplatformAndroidCompilationBuilder.() -> Unit
    ): HasConfigurableValue<KotlinMultiplatformAndroidHostTest> {
        androidTestOnJvmBuilder = withTestBuilder(
            KmpAndroidCompilationType.TEST_ON_JVM,
            androidTestOnJvmBuilder
        )
        androidTestOnJvmOptions = dslServices.newDecoratedInstance(
            KotlinMultiplatformAndroidHostTestImpl::class.java, dslServices
        )

        androidTestOnJvmBuilder!!.action()
        compilationEnabledCallback(androidTestOnJvmBuilder!!)
        return HasConfigurableValueImpl(androidTestOnJvmOptions!!)
    }

    @Deprecated("Use withHostTestBuilder. This api will be removed in AGP 9.0",
        ReplaceWith("withHostTestBuilder(action)")
    )
    override fun withAndroidTestOnJvmBuilder(
        action: KotlinMultiplatformAndroidCompilationBuilder.() -> Unit
    ): HasConfigurableValue<KotlinMultiplatformAndroidHostTest> {
        return withHostTestBuilder(action)
    }

    override fun withDeviceTest(action: KotlinMultiplatformAndroidDeviceTest.() -> Unit) {
        withDeviceTestBuilder {  }.configure(action)
    }

    @Deprecated("Use withDeviceTest. This api will be removed in AGP 9.0",
        ReplaceWith("withDeviceTest(action)")
    )
    override fun withAndroidTestOnDevice(action: KotlinMultiplatformAndroidDeviceTest.() -> Unit) {
        return withDeviceTest(action)
    }

    override fun withDeviceTestBuilder(
        action: KotlinMultiplatformAndroidCompilationBuilder.() -> Unit
    ): HasConfigurableValue<KotlinMultiplatformAndroidDeviceTest> {
        androidTestOnDeviceBuilder = withTestBuilder(
            KmpAndroidCompilationType.TEST_ON_DEVICE,
            androidTestOnDeviceBuilder
        )
        androidTestOnDeviceOptions = dslServices.newDecoratedInstance(
            KotlinMultiplatformAndroidDeviceTestImpl::class.java, dslServices
        )

        androidTestOnDeviceBuilder!!.action()
        compilationEnabledCallback(androidTestOnDeviceBuilder!!)
        return HasConfigurableValueImpl(androidTestOnDeviceOptions!!)
    }

    @Deprecated("Use withDeviceTestBuilder. This api will be removed in AGP 9.0",
        ReplaceWith("withDeviceTestBuilder(action)")
    )
    override fun withAndroidTestOnDeviceBuilder(action: KotlinMultiplatformAndroidCompilationBuilder.() -> Unit): HasConfigurableValue<KotlinMultiplatformAndroidDeviceTest> {
        return withDeviceTestBuilder(action)
    }
}
