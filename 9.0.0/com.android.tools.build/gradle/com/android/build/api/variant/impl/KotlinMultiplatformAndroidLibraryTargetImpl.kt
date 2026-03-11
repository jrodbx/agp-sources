/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.api.variant.impl

import com.android.build.api.dsl.AarMetadata
import com.android.build.api.dsl.CompileSdkSpec
import com.android.build.api.dsl.DependencySelection
import com.android.build.api.dsl.HasConfigurableValue
import com.android.build.api.dsl.KmpOptimization
import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilation
import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilationBuilder
import com.android.build.api.dsl.KotlinMultiplatformAndroidDeviceTest
import com.android.build.api.dsl.KotlinMultiplatformAndroidHostTest
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.dsl.LibraryAndroidResources
import com.android.build.api.dsl.Lint
import com.android.build.api.dsl.MinSdkSpec
import com.android.build.api.dsl.Packaging
import com.android.build.api.dsl.TestCoverage
import com.android.build.gradle.internal.dsl.CompileSdkDelegate
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidLibraryExtensionImpl
import com.android.build.gradle.internal.dsl.MinSdkDelegate
import com.android.build.gradle.internal.services.DslServices
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.external.DecoratedExternalKotlinTarget
import javax.inject.Inject

@OptIn(ExternalKotlinTargetApi::class)
internal open class KotlinMultiplatformAndroidLibraryTargetImpl @Inject constructor(
    dslServices: DslServices,
    delegate: Delegate,
    kotlinExtension: KotlinMultiplatformExtension,
    androidExtension: KotlinMultiplatformAndroidLibraryExtensionImpl
) : DecoratedExternalKotlinTarget(delegate),
    KotlinMultiplatformAndroidLibraryTarget,
    KotlinMultiplatformAndroidLibraryExtension by androidExtension {

    internal var enableJavaSources = false
        private set

    override val compilerOptions: KotlinJvmCompilerOptions by lazy(LazyThreadSafetyMode.PUBLICATION) {
        (super.compilerOptions as KotlinJvmCompilerOptions).apply {
            KotlinJvmToolchain.wireJvmTargetToToolchain(this, project)
        }
    }

    override val compilations: NamedDomainObjectContainer<KotlinMultiplatformAndroidCompilation> =
        project.objects.domainObjectContainer(
            KotlinMultiplatformAndroidCompilation::class.java,
            KotlinMultiplatformAndroidCompilationFactory(
                project = project,
                target = this,
                kotlinExtension = kotlinExtension,
                androidExtension = androidExtension
            )
        )

    override fun withJava() {
        enableJavaSources = true
    }

    private val compileSdkDelegate = CompileSdkDelegate(
        getCompileSdk = { androidExtension._compileSdk },
        setCompileSdk = { androidExtension._compileSdk = it },
        issueReporter = dslServices.issueReporter,
        dslServices = dslServices
    )

    open fun compileSdk(action: Action<CompileSdkSpec>) {
        compileSdkDelegate.compileSdk(action)
    }

    //TODO(b/421964815): remove the support for groovy space assignment(e.g `compileSdk 24`).
    @Deprecated(
        "To be removed after Gradle drops space assignment support",
        ReplaceWith("compileSdk { version = release(value) }")
    )
    open fun compileSdk(value: Int) {
        compileSdkDelegate.compileSdk = value
    }

    private val minSdkDelegate = MinSdkDelegate(
        getMinSdk = { androidExtension._minSdk },
        setMinSdk = { androidExtension._minSdk = it },
        dslServices = dslServices
    )

    open fun minSdk(action: Action<MinSdkSpec>) {
        minSdkDelegate.minSdk(action)
    }

    //TODO(b/421964815): remove the support for groovy space assignment(e.g `minSdk 24`).
    @Deprecated(
        "To be removed after Gradle drops space assignment support",
        ReplaceWith("minSdk { version = release(value) }")
    )
    open fun minSdk(value: Int) {
        minSdkDelegate.minSdk = value
    }

    fun localDependencySelection(action: Action<DependencySelection>) {
        action.execute(localDependencySelection)
    }

    fun androidResources(action: Action<LibraryAndroidResources>) {
        action.execute(androidResources)
    }

    fun testCoverage(action: Action<TestCoverage>) {
        action.execute(testCoverage)
    }

    fun optimization(action: Action<KmpOptimization>) {
        action.execute(optimization)
    }

    fun lint(action: Action<Lint>) {
        action.execute(lint)
    }

    fun aarMetadata(action: Action<AarMetadata>) {
        action.execute(aarMetadata)
    }

    fun packaging(action: Action<Packaging>) {
        action.execute(packaging)
    }

    fun withHostTest(action: Action<KotlinMultiplatformAndroidHostTest>) {
        withHostTest {
            action.execute(this)
        }
    }

    fun withHostTestBuilder(action: Action<KotlinMultiplatformAndroidCompilationBuilder>): HasConfigurableValue<KotlinMultiplatformAndroidHostTest> {
        return withHostTestBuilder {
            action.execute(this)
        }
    }

    fun withDeviceTest(action: Action<KotlinMultiplatformAndroidDeviceTest>) {
        withDeviceTest {
            action.execute(this)
        }
    }

    fun withDeviceTestBuilder(action: Action<KotlinMultiplatformAndroidCompilationBuilder>): HasConfigurableValue<KotlinMultiplatformAndroidDeviceTest> {
        return withDeviceTestBuilder {
            action.execute(this)
        }
    }
}
