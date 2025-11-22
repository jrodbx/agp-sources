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

import com.android.build.api.dsl.ComposeOptions
import com.android.build.api.dsl.LibraryAndroidResources
import com.android.build.api.dsl.LibraryBuildFeatures
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.LibraryInstallation
import com.android.build.api.dsl.LibraryPublishing
import com.android.build.api.dsl.Lint
import com.android.build.api.dsl.Packaging
import com.android.build.api.dsl.PrivacySandbox
import com.android.build.api.dsl.TestCoverage
import com.android.build.gradle.internal.CompileOptions as CompileOptionsImpl
import com.android.build.gradle.internal.dsl.AaptOptions as AaptOptionsImpl
import com.android.build.gradle.internal.dsl.AdbOptions as AdbOptionsImpl
import com.android.build.gradle.internal.dsl.DataBindingOptions as DataBindingOptionsImpl
import com.android.build.gradle.internal.dsl.ExternalNativeBuild as ExternalNativeBuildImpl
import com.android.build.gradle.internal.dsl.LintOptions as LintOptionsImpl
import com.android.build.gradle.internal.coverage.JacocoOptions as JacocoOptionsImpl
import com.android.build.gradle.internal.dsl.PackagingOptions as PackagingImpl
import com.android.build.gradle.internal.dsl.TestOptions as TestOptionsImpl
import com.android.build.gradle.internal.dsl.Splits as SplitsImpl
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer

/** See [InternalCommonExtension] */
interface InternalLibraryExtension: LibraryExtension, InternalTestedExtension{

    override var aidlPackagedList: MutableCollection<String>
    fun aaptOptions(action: Action<AaptOptionsImpl>)
    fun adbOptions(action: Action<AdbOptionsImpl>)
    fun androidResources(action: Action<LibraryAndroidResources>)
    fun buildTypes(action: Action<in NamedDomainObjectContainer<BuildType>>)
    fun compileOptions(action: Action<CompileOptionsImpl>)
    fun composeOptions(action: Action<ComposeOptions>)
    fun dataBinding(action: Action<DataBindingOptionsImpl>)
    fun viewBinding(action: Action<ViewBindingOptionsImpl>)
    fun jacoco(action: Action<JacocoOptionsImpl>)
    fun testOptions(action: Action<TestOptionsImpl>)
    fun testCoverage(action: Action<TestCoverage>)
    fun splits(action: Action<SplitsImpl>)
    fun sourceSets(action: Action<NamedDomainObjectContainer<com.android.build.gradle.api.AndroidSourceSet>>)
    fun lint(action: Action<Lint>)
    fun lintOptions(action: Action<LintOptionsImpl>)
    fun productFlavors(action: Action<NamedDomainObjectContainer<ProductFlavor>>)
    fun defaultConfig(action: Action<DefaultConfig>)
    fun signingConfigs(action: Action<NamedDomainObjectContainer<SigningConfig>>)
    fun externalNativeBuild(action: Action<ExternalNativeBuildImpl>)
    fun buildFeatures(action: Action<LibraryBuildFeatures>)
    fun installation(action: Action<LibraryInstallation>)
    fun packaging(action: Action<Packaging>)
    fun packagingOptions(action: Action<PackagingImpl>)
    fun publishing(action: Action<LibraryPublishing>)

    fun privacySandbox(action: Action<PrivacySandbox>)
}
