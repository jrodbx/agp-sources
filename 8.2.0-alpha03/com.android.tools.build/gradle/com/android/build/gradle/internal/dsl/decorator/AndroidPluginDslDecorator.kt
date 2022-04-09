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

import com.android.build.api.dsl.AarMetadata
import com.android.build.api.dsl.AbiSplit
import com.android.build.api.dsl.AndroidResources
import com.android.build.api.dsl.AnnotationProcessorOptions
import com.android.build.api.dsl.ApplicationAndroidResources
import com.android.build.api.dsl.ApplicationPublishing
import com.android.build.api.dsl.AssetPackBundleExtension
import com.android.build.api.dsl.Bundle
import com.android.build.api.dsl.BundleAbi
import com.android.build.api.dsl.BundleDensity
import com.android.build.api.dsl.BundleDeviceTier
import com.android.build.api.dsl.BundleLanguage
import com.android.build.api.dsl.BundleTexture
import com.android.build.api.dsl.BundleCodeTransparency
import com.android.build.api.dsl.BundleCountrySet
import com.android.build.api.dsl.Cmake
import com.android.build.api.dsl.LibraryPublishing
import com.android.build.api.dsl.CompileOptions
import com.android.build.api.dsl.DataBinding
import com.android.build.api.dsl.DensitySplit
import com.android.build.api.dsl.DependenciesInfo
import com.android.build.api.dsl.DexPackaging
import com.android.build.api.dsl.ExternalNativeBuild
import com.android.build.api.dsl.FusedLibraryExtension
import com.android.build.api.dsl.JavaCompileOptions
import com.android.build.api.dsl.JniLibsPackaging
import com.android.build.api.dsl.KeepRules
import com.android.build.api.dsl.Lint
import com.android.build.api.dsl.NdkBuild
import com.android.build.api.dsl.Packaging
import com.android.build.api.dsl.Optimization
import com.android.build.api.dsl.ResourcesPackaging
import com.android.build.api.dsl.SigningConfig
import com.android.build.api.dsl.Split
import com.android.build.api.dsl.Splits
import com.android.build.api.dsl.BundleStoreArchive
import com.android.build.api.dsl.PrivacySandboxSdkBundle
import com.android.build.api.dsl.PrivacySandboxSdkExtension
import com.android.build.api.dsl.ViewBinding
import com.android.build.gradle.internal.dsl.AaptOptions
import com.android.build.gradle.internal.dsl.AarMetadataImpl
import com.android.build.gradle.internal.dsl.AbiSplitOptions
import com.android.build.gradle.internal.dsl.ApplicationAndroidResourcesImpl
import com.android.build.gradle.internal.dsl.AnnotationProcessorOptions as AnnotationProcessorOptionsImpl
import com.android.build.gradle.internal.dsl.ApplicationPublishingImpl
import com.android.build.gradle.internal.dsl.AssetPackBundleExtensionImpl
import com.android.build.gradle.internal.dsl.BundleOptions
import com.android.build.gradle.internal.dsl.BundleOptionsAbi
import com.android.build.gradle.internal.dsl.BundleOptionsCodeTransparency
import com.android.build.gradle.internal.dsl.BundleOptionsCountrySet
import com.android.build.gradle.internal.dsl.BundleOptionsDensity
import com.android.build.gradle.internal.dsl.BundleOptionsDeviceTier
import com.android.build.gradle.internal.dsl.BundleOptionsLanguage
import com.android.build.gradle.internal.dsl.BundleOptionsStoreArchive
import com.android.build.gradle.internal.dsl.BundleOptionsTexture
import com.android.build.gradle.internal.dsl.CmakeOptions
import com.android.build.gradle.internal.dsl.ConfigurableFiles
import com.android.build.gradle.internal.dsl.ConfigurableFilesImpl
import com.android.build.gradle.internal.dsl.DataBindingOptions
import com.android.build.gradle.internal.dsl.DensitySplitOptions
import com.android.build.gradle.internal.dsl.DependenciesInfoImpl
import com.android.build.gradle.internal.dsl.DependencyVariantSelection
import com.android.build.gradle.internal.dsl.DependencyVariantSelectionImpl
import com.android.build.gradle.internal.dsl.DexPackagingImpl
import com.android.build.gradle.internal.dsl.FusedLibraryExtensionImpl
import com.android.build.gradle.internal.dsl.ExternalNativeBuild as ExternalNativeBuildImpl
import com.android.build.gradle.internal.dsl.LintImpl
import com.android.build.gradle.internal.dsl.JavaCompileOptions as JavaCompileOptionsImpl
import com.android.build.gradle.internal.dsl.JniLibsPackagingImpl
import com.android.build.gradle.internal.dsl.KmpOptimization
import com.android.build.gradle.internal.dsl.KmpOptimizationImpl
import com.android.build.gradle.internal.dsl.LibraryPublishingImpl
import com.android.build.gradle.internal.dsl.NdkBuildOptions
import com.android.build.gradle.internal.dsl.PrivacySandboxSdkBundleImpl
import com.android.build.gradle.internal.dsl.PrivacySandboxSdkExtensionImpl
import com.android.build.gradle.internal.dsl.ResourcesPackagingImpl
import com.android.build.gradle.internal.dsl.SplitOptions
import com.android.build.gradle.internal.dsl.ViewBindingOptionsImpl
import org.gradle.api.JavaVersion

/** The list of all the supported property types for the production AGP */
val AGP_SUPPORTED_PROPERTY_TYPES: List<SupportedPropertyType> = listOf(
    SupportedPropertyType.Var.String,
    SupportedPropertyType.Var.Boolean,
    SupportedPropertyType.Var.NullableBoolean,
    SupportedPropertyType.Var.Int,
    SupportedPropertyType.Var.NullableInt,
    SupportedPropertyType.Var.File,
    SupportedPropertyType.Var.Enum(JavaVersion::class.java),

    SupportedPropertyType.Collection.List,
    SupportedPropertyType.Collection.Set,
    SupportedPropertyType.Collection.Map,

    SupportedPropertyType.Block(AarMetadata::class.java, AarMetadataImpl::class.java),
    SupportedPropertyType.Block(AbiSplit::class.java, AbiSplitOptions::class.java),
    SupportedPropertyType.Block(AndroidResources::class.java, AaptOptions::class.java),
    SupportedPropertyType.Block(AnnotationProcessorOptions::class.java, AnnotationProcessorOptionsImpl::class.java),
    SupportedPropertyType.Block(ApplicationAndroidResources::class.java, ApplicationAndroidResourcesImpl::class.java),
    SupportedPropertyType.Block(ApplicationPublishing::class.java, ApplicationPublishingImpl::class.java),
    SupportedPropertyType.Block(AssetPackBundleExtension::class.java, AssetPackBundleExtensionImpl::class.java),
    SupportedPropertyType.Block(Bundle::class.java, BundleOptions::class.java),
    SupportedPropertyType.Block(BundleAbi::class.java, BundleOptionsAbi::class.java),
    SupportedPropertyType.Block(BundleDensity::class.java, BundleOptionsDensity::class.java),
    SupportedPropertyType.Block(BundleDeviceTier::class.java, BundleOptionsDeviceTier::class.java),
    SupportedPropertyType.Block(BundleCountrySet::class.java, BundleOptionsCountrySet::class.java),
    SupportedPropertyType.Block(BundleLanguage::class.java, BundleOptionsLanguage::class.java),
    SupportedPropertyType.Block(BundleTexture::class.java, BundleOptionsTexture::class.java),
    SupportedPropertyType.Block(BundleCodeTransparency::class.java, BundleOptionsCodeTransparency::class.java),
    SupportedPropertyType.Block(BundleStoreArchive::class.java, BundleOptionsStoreArchive::class.java),
    SupportedPropertyType.Block(Cmake::class.java, CmakeOptions::class.java),
    SupportedPropertyType.Block(CompileOptions::class.java, com.android.build.gradle.internal.CompileOptions::class.java),
    SupportedPropertyType.Block(DataBinding::class.java, DataBindingOptions::class.java),
    SupportedPropertyType.Block(DensitySplit::class.java, DensitySplitOptions::class.java),
    SupportedPropertyType.Block(DexPackaging::class.java, DexPackagingImpl::class.java),
    SupportedPropertyType.Block(DependenciesInfo::class.java, DependenciesInfoImpl::class.java),
    SupportedPropertyType.Block(ExternalNativeBuild::class.java, ExternalNativeBuildImpl::class.java),
    SupportedPropertyType.Block(JavaCompileOptions::class.java, JavaCompileOptionsImpl::class.java),
    SupportedPropertyType.Block(JniLibsPackaging::class.java, JniLibsPackagingImpl::class.java),
    SupportedPropertyType.Block(KeepRules::class.java, com.android.build.gradle.internal.dsl.KeepRulesImpl::class.java),
    SupportedPropertyType.Block(LibraryPublishing::class.java, LibraryPublishingImpl::class.java),
    SupportedPropertyType.Block(Lint::class.java, LintImpl::class.java),
    SupportedPropertyType.Block(NdkBuild::class.java, NdkBuildOptions::class.java),
    SupportedPropertyType.Block(Packaging::class.java, com.android.build.gradle.internal.dsl.PackagingOptions::class.java),
    SupportedPropertyType.Block(Optimization::class.java, com.android.build.gradle.internal.dsl.OptimizationImpl::class.java),
    SupportedPropertyType.Block(ResourcesPackaging::class.java, ResourcesPackagingImpl::class.java),
    SupportedPropertyType.Block(SigningConfig::class.java, com.android.build.gradle.internal.dsl.SigningConfigImpl::class.java),
    SupportedPropertyType.Block(Split::class.java, SplitOptions::class.java),
    SupportedPropertyType.Block(Splits::class.java, com.android.build.gradle.internal.dsl.Splits::class.java),
    SupportedPropertyType.Block(ViewBinding::class.java, ViewBindingOptionsImpl::class.java),
    SupportedPropertyType.Block(ConfigurableFiles::class.java, ConfigurableFilesImpl::class.java),
    SupportedPropertyType.Block(KmpOptimization::class.java, KmpOptimizationImpl::class.java),
    SupportedPropertyType.Block(DependencyVariantSelection::class.java, DependencyVariantSelectionImpl::class.java),

    // FusedLibrary Extensions.
    SupportedPropertyType.Block(FusedLibraryExtension::class.java, FusedLibraryExtensionImpl::class.java),
    SupportedPropertyType.Block(PrivacySandboxSdkExtension::class.java, PrivacySandboxSdkExtensionImpl::class.java),
    SupportedPropertyType.Block(PrivacySandboxSdkBundle::class.java, PrivacySandboxSdkBundleImpl::class.java)
)

/**
 * The DSL decorator in this classloader in AGP.
 *
 * This is a static field, rather than a build service as it shares its lifetime with
 * the classloader that AGP is loaded in.
 */
val androidPluginDslDecorator = DslDecorator(AGP_SUPPORTED_PROPERTY_TYPES)
