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

import com.android.build.api.dsl.CompileSdkSpec
import com.android.build.api.dsl.CompileSdkVersion
import com.android.build.api.dsl.HasConfigurableValue
import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilationBuilder
import com.android.build.api.dsl.KotlinMultiplatformAndroidDeviceTest
import com.android.build.api.dsl.KotlinMultiplatformAndroidHostTest
import com.android.build.api.dsl.LibraryAndroidResources
import com.android.build.api.dsl.DependencySelection
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import com.android.build.api.dsl.MinSdkSpec
import com.android.build.api.dsl.MinSdkVersion
import com.android.build.api.dsl.TestCoverage
import com.android.build.api.variant.impl.KmpAndroidCompilationType
import com.android.build.api.variant.impl.MutableAndroidVersion
import com.android.build.gradle.internal.coverage.JacocoOptions
import com.android.build.gradle.internal.dsl.decorator.annotation.WithLazyInitialization
import com.android.build.gradle.internal.services.DslServices
import com.android.builder.core.LibraryRequest
import com.android.builder.core.ToolsRevisionUtils
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

internal abstract class KotlinMultiplatformAndroidLibraryExtensionImpl @Inject constructor(
    private val dslServices: DslServices,
    objectFactory: ObjectFactory,
    private val compilationEnabledCallback: (KotlinMultiplatformAndroidCompilationBuilderImpl) -> Unit,
): KotlinMultiplatformAndroidLibraryExtension, Lockable {

    @WithLazyInitialization
    @Suppress("unused") // the call is injected by DslDecorator
    fun lazyInit() {
        buildToolsVersion = ToolsRevisionUtils.DEFAULT_BUILD_TOOLS_REVISION.toString()
    }

    final override val localDependencySelection: DependencySelection = dslServices.newDecoratedInstance(
        DependencySelectionImpl::class.java, dslServices, objectFactory
    )

    override fun localDependencySelection(action: DependencySelection.() -> Unit) {
        action.invoke(localDependencySelection)
    }

    override val androidResources: LibraryAndroidResources = dslServices.newDecoratedInstance(
        LibraryAndroidResourcesImpl::class.java,
        dslServices,
        false
    )

    override fun androidResources(action: LibraryAndroidResources.() -> Unit) {
        action.invoke(androidResources)
    }

    abstract val libraryRequests: MutableList<LibraryRequest>

    abstract fun setLibraryRequests(libraryRequests: List<LibraryRequest>)

    override fun useLibrary(name: String) {
        useLibrary(name, true)
    }

    override fun useLibrary(name: String, required: Boolean) {
        libraryRequests.add(LibraryRequest(name, required))
    }

    internal abstract var _compileSdk: CompileSdkVersion?

    private val compileSdkDelegate = CompileSdkDelegate(
        getCompileSdk = { _compileSdk },
        setCompileSdk = { _compileSdk = it },
        issueReporter = dslServices.issueReporter,
        dslServices = dslServices
    )

    val compileSdkVersion: String? by lazy {
        compileSdkDelegate.compileSdkVersion
    }

    override var compileSdk: Int?
        get() = compileSdkDelegate.compileSdk
        set(value) {
            compileSdkDelegate.compileSdk = value
        }

    override fun compileSdk(action: CompileSdkSpec.() -> Unit) {
        compileSdkDelegate.compileSdk(action)
    }

    override var compileSdkPreview: String?
        get() = compileSdkDelegate.compileSdkPreview
        set(value) {
            compileSdkDelegate.compileSdkPreview = value
        }

    override var compileSdkExtension: Int?
        get() = compileSdkDelegate.compileSdkExtension
        set(value) {
            compileSdkDelegate.compileSdkExtension = value
        }

    internal abstract var _minSdk: MinSdkVersion?

    private val minSdkDelegate = MinSdkDelegate(
        getMinSdk = { _minSdk },
        setMinSdk = { _minSdk = it },
        dslServices = dslServices
    )

    internal val minSdkVersion: MutableAndroidVersion
        get() {
            return MutableAndroidVersion(_minSdk?.apiLevel, _minSdk?.codeName).sanitize().let {
                MutableAndroidVersion(it.apiLevel, it.codename)
            }
        }

    override var minSdk: Int?
        get() = minSdkDelegate.minSdk
        set(value) {
            minSdkDelegate.minSdk = value
        }

    override var minSdkPreview: String?
        get() = minSdkDelegate.minSdkPreview
        set(value) {
            minSdkDelegate.minSdkPreview = value
        }

    override fun minSdk(action: MinSdkSpec.() -> Unit) {
        minSdkDelegate.minSdk(action)
    }

    override val testCoverage = dslServices.newInstance(JacocoOptions::class.java)

    override fun testCoverage(action: TestCoverage.() -> Unit) {
        action.invoke(testCoverage)
    }

    internal var androidTestOnJvmOptions: KotlinMultiplatformAndroidHostTestImpl? = null
    internal var androidTestOnDeviceOptions: KotlinMultiplatformAndroidDeviceTestImpl? = null
    internal var androidTestOnJvmBuilder: KotlinMultiplatformAndroidCompilationBuilderImpl? = null
    internal var androidTestOnDeviceBuilder: KotlinMultiplatformAndroidCompilationBuilderImpl? = null

    private fun withTestBuilder(
        compilationType: KmpAndroidCompilationType,
        previousConfiguration: KotlinMultiplatformAndroidCompilationBuilder?,
    ): KotlinMultiplatformAndroidCompilationBuilderImpl {
        previousConfiguration?.let {
            val compilationInfo = when (compilationType) {
                KmpAndroidCompilationType.MAIN -> Pair("main", KmpAndroidCompilationType.MAIN)
                KmpAndroidCompilationType.HOST_TEST -> Pair("host", KmpAndroidCompilationType.HOST_TEST)
                KmpAndroidCompilationType.DEVICE_TEST -> Pair("device", KmpAndroidCompilationType.DEVICE_TEST)
            }

            throw IllegalStateException(
                "Android ${compilationInfo.first} tests have already been enabled, and a corresponding compilation " +
                        "(`${compilationInfo.second.defaultCompilationName}`) has already been created. You can create only " +
                        "one component of type android $compilationInfo tests on. Alternatively, you can " +
                        "specify a dependency from the default sourceSet " +
                        "(`${compilationInfo.second.defaultSourceSetName}`) to another sourceSet and it will be " +
                        "included in the compilation."
            )
        }

        return KotlinMultiplatformAndroidCompilationBuilderImpl(compilationType)
    }

    override fun withHostTest(action: KotlinMultiplatformAndroidHostTest.() -> Unit) {
        withHostTestBuilder {  }.configure(action)
    }

    override fun withHostTestBuilder(
        action: KotlinMultiplatformAndroidCompilationBuilder.() -> Unit
    ): HasConfigurableValue<KotlinMultiplatformAndroidHostTest> {
        androidTestOnJvmBuilder = withTestBuilder(
            KmpAndroidCompilationType.HOST_TEST,
            androidTestOnJvmBuilder
        )
        androidTestOnJvmOptions = dslServices.newDecoratedInstance(
            KotlinMultiplatformAndroidHostTestImpl::class.java, dslServices
        )

        androidTestOnJvmBuilder!!.action()
        compilationEnabledCallback(androidTestOnJvmBuilder!!)
        return dslServices.newInstance(
            HasConfigurableValueImpl::class.java,
            androidTestOnJvmOptions!!
        ) as HasConfigurableValue<KotlinMultiplatformAndroidHostTest>
    }

    override fun withDeviceTest(action: KotlinMultiplatformAndroidDeviceTest.() -> Unit) {
        withDeviceTestBuilder {  }.configure(action)
    }

    override fun withDeviceTestBuilder(
        action: KotlinMultiplatformAndroidCompilationBuilder.() -> Unit
    ): HasConfigurableValue<KotlinMultiplatformAndroidDeviceTest> {
        androidTestOnDeviceBuilder = withTestBuilder(
            KmpAndroidCompilationType.DEVICE_TEST,
            androidTestOnDeviceBuilder
        )
        androidTestOnDeviceOptions = dslServices.newDecoratedInstance(
            KotlinMultiplatformAndroidDeviceTestImpl::class.java, dslServices
        )

        androidTestOnDeviceBuilder!!.action()
        compilationEnabledCallback(androidTestOnDeviceBuilder!!)
        return dslServices.newInstance(
            HasConfigurableValueImpl::class.java,
            androidTestOnDeviceOptions!!
        ) as HasConfigurableValue<KotlinMultiplatformAndroidDeviceTest>
    }
}
