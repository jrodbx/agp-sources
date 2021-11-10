/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl.decorator

import com.android.build.api.dsl.AbiSplit
import com.android.build.api.dsl.AndroidResources
import com.android.build.api.dsl.ApplicationPublishing
import com.android.build.api.dsl.AssetPackBundleExtension
import com.android.build.api.dsl.Bundle
import com.android.build.api.dsl.BundleAbi
import com.android.build.api.dsl.BundleDensity
import com.android.build.api.dsl.BundleDeviceTier
import com.android.build.api.dsl.BundleLanguage
import com.android.build.api.dsl.BundleTexture
import com.android.build.api.dsl.BundleCodeTransparency
import com.android.build.api.dsl.LibraryPublishing
import com.android.build.api.dsl.CompileOptions
import com.android.build.api.dsl.DensitySplit
import com.android.build.api.dsl.DexPackagingOptions
import com.android.build.api.dsl.JniLibsPackagingOptions
import com.android.build.api.dsl.Lint
import com.android.build.api.dsl.PackagingOptions
import com.android.build.api.dsl.ResourcesPackagingOptions
import com.android.build.api.dsl.SigningConfig
import com.android.build.api.dsl.Split
import com.android.build.api.dsl.Splits
import com.android.build.gradle.internal.dsl.AaptOptions
import com.android.build.gradle.internal.dsl.AbiSplitOptions
import com.android.build.gradle.internal.dsl.ApplicationPublishingImpl
import com.android.build.gradle.internal.dsl.AssetPackBundleExtensionImpl
import com.android.build.gradle.internal.dsl.BundleOptions
import com.android.build.gradle.internal.dsl.BundleOptionsAbi
import com.android.build.gradle.internal.dsl.BundleOptionsCodeTransparency
import com.android.build.gradle.internal.dsl.BundleOptionsDensity
import com.android.build.gradle.internal.dsl.BundleOptionsDeviceTier
import com.android.build.gradle.internal.dsl.BundleOptionsLanguage
import com.android.build.gradle.internal.dsl.BundleOptionsTexture
import com.android.build.gradle.internal.dsl.DensitySplitOptions
import com.android.build.gradle.internal.dsl.DexPackagingOptionsImpl
import com.android.build.gradle.internal.dsl.LintImpl
import com.android.build.gradle.internal.dsl.JniLibsPackagingOptionsImpl
import com.android.build.gradle.internal.dsl.LibraryPublishingImpl
import com.android.build.gradle.internal.dsl.ResourcesPackagingOptionsImpl
import com.android.build.gradle.internal.dsl.SplitOptions
import org.gradle.api.JavaVersion

/** The list of all the supported property types for the production AGP */
val AGP_SUPPORTED_PROPERTY_TYPES: List<SupportedPropertyType> = listOf(
    SupportedPropertyType.Var.String,
    SupportedPropertyType.Var.Boolean,
    SupportedPropertyType.Var.NullableBoolean,
    SupportedPropertyType.Var.Int,
    SupportedPropertyType.Var.NullableInt,
    SupportedPropertyType.Var.Enum(JavaVersion::class.java),

    SupportedPropertyType.Collection.List,
    SupportedPropertyType.Collection.Set,

    SupportedPropertyType.Block(AbiSplit::class.java, AbiSplitOptions::class.java),
    SupportedPropertyType.Block(AndroidResources::class.java, AaptOptions::class.java),
    SupportedPropertyType.Block(ApplicationPublishing::class.java, ApplicationPublishingImpl::class.java),
    SupportedPropertyType.Block(AssetPackBundleExtension::class.java, AssetPackBundleExtensionImpl::class.java),
    SupportedPropertyType.Block(Bundle::class.java, BundleOptions::class.java),
    SupportedPropertyType.Block(BundleAbi::class.java, BundleOptionsAbi::class.java),
    SupportedPropertyType.Block(BundleDensity::class.java, BundleOptionsDensity::class.java),
    SupportedPropertyType.Block(BundleDeviceTier::class.java, BundleOptionsDeviceTier::class.java),
    SupportedPropertyType.Block(BundleLanguage::class.java, BundleOptionsLanguage::class.java),
    SupportedPropertyType.Block(BundleTexture::class.java, BundleOptionsTexture::class.java),
    SupportedPropertyType.Block(BundleCodeTransparency::class.java, BundleOptionsCodeTransparency::class.java),
    SupportedPropertyType.Block(CompileOptions::class.java, com.android.build.gradle.internal.CompileOptions::class.java),
    SupportedPropertyType.Block(DensitySplit::class.java, DensitySplitOptions::class.java),
    SupportedPropertyType.Block(DexPackagingOptions::class.java, DexPackagingOptionsImpl::class.java),
    SupportedPropertyType.Block(JniLibsPackagingOptions::class.java, JniLibsPackagingOptionsImpl::class.java),
    SupportedPropertyType.Block(LibraryPublishing::class.java, LibraryPublishingImpl::class.java),
    SupportedPropertyType.Block(Lint::class.java, LintImpl::class.java),
    SupportedPropertyType.Block(PackagingOptions::class.java, com.android.build.gradle.internal.dsl.PackagingOptions::class.java),
    SupportedPropertyType.Block(ResourcesPackagingOptions::class.java, ResourcesPackagingOptionsImpl::class.java),
    SupportedPropertyType.Block(SigningConfig::class.java, com.android.build.gradle.internal.dsl.SigningConfigImpl::class.java),
    SupportedPropertyType.Block(Split::class.java, SplitOptions::class.java),
    SupportedPropertyType.Block(Splits::class.java, com.android.build.gradle.internal.dsl.Splits::class.java),
)

/**
 * The DSL decorator in this classloader in AGP.
 *
 * This is a static field, rather than a build service as it shares its lifetime with
 * the classloader that AGP is loaded in.
 */
val androidPluginDslDecorator = DslDecorator(AGP_SUPPORTED_PROPERTY_TYPES)
