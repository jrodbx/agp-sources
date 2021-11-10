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

import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.api.AndroidSourceSet
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import com.android.build.gradle.internal.CompileOptions as CompileOptionsImpl
import com.android.build.gradle.internal.coverage.JacocoOptions as JacocoOptionsImpl
import com.android.build.gradle.internal.dsl.AaptOptions as AaptOptionsImpl
import com.android.build.gradle.internal.dsl.AdbOptions as AdbOptionsImpl
import com.android.build.gradle.internal.dsl.DataBindingOptions as DataBindingOptionsImpl
import com.android.build.gradle.internal.dsl.ExternalNativeBuild as ExternalNativeBuildImpl
import com.android.build.gradle.internal.dsl.LintOptions as LintOptionsImpl
import com.android.build.gradle.internal.dsl.PackagingOptions as PackagingOptionsImpl
import com.android.build.gradle.internal.dsl.Splits as SplitsImpl
import com.android.build.gradle.internal.dsl.TestOptions as TestOptionsImpl

/**
 * Internal extension of the DSL interface that overrides the properties to use the implementation
 * types, in order to enable the use of kotlin delegation from the original DSL classes
 * to the new implementations.
 */
interface InternalCommonExtension<
        BuildFeaturesT : com.android.build.api.dsl.BuildFeatures,
        BuildTypeT : com.android.build.api.dsl.BuildType,
        DefaultConfigT : com.android.build.api.dsl.DefaultConfig,
        ProductFlavorT : com.android.build.api.dsl.ProductFlavor> :
    CommonExtension<
        BuildFeaturesT,
        BuildTypeT,
        DefaultConfigT,
        ProductFlavorT>, Lockable {

    override val aaptOptions: AaptOptionsImpl

    override val adbOptions: AdbOptionsImpl
    override val compileOptions: CompileOptionsImpl

    override val dataBinding: DataBindingOptionsImpl
    override val jacoco: JacocoOptionsImpl
    override val lintOptions: LintOptionsImpl
    override val packagingOptions: PackagingOptionsImpl
    override val externalNativeBuild: ExternalNativeBuildImpl
    override val testOptions: TestOptionsImpl
    override val splits: SplitsImpl
    override val signingConfigs: NamedDomainObjectContainer<SigningConfig>

    var compileSdkVersion: String?

    fun buildTypes(action: Action<in NamedDomainObjectContainer<BuildType>>)
    fun productFlavors(action: Action<NamedDomainObjectContainer<ProductFlavor>>)
    fun defaultConfig(action: Action<DefaultConfig>)
    fun signingConfigs(action: Action<NamedDomainObjectContainer<SigningConfig>>)
}
