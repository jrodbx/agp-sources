/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.build.gradle.internal.core

import com.android.SdkConstants
import com.android.build.api.dsl.AndroidResources
import com.android.build.api.dsl.CompileOptions
import com.android.build.api.dsl.Lint
import com.android.build.api.dsl.PackagingOptions
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.dsl.TestFixtures
import com.android.build.api.transform.Transform
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.ResValue
import com.android.build.api.variant.impl.MutableAndroidVersion
import com.android.build.gradle.api.JavaCompileOptions
import com.android.build.gradle.internal.ProguardFileType
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.dsl.CoreExternalNativeBuildOptions
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.publishing.VariantPublishingInfo
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.AbstractProductFlavor
import com.android.builder.core.VariantType
import com.android.builder.dexing.DexingType
import com.android.builder.model.VectorDrawablesOptions
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File

/**
 * Represents a variant, initialized from the DSL object model (default config, build type, flavors)
 *
 * This class allows querying for the values set via the DSL model.
 *
 * Use [VariantDslInfoBuilder] to instantiate.
 *
 */
interface VariantDslInfo {

    val componentIdentity: ComponentIdentity

    val variantType: VariantType

    /** The list of product flavors. Items earlier in the list override later items.  */
    val productFlavorList: List<ProductFlavor>

    /**
     * Optional tested config in case this variant is used for testing another variant.
     *
     * @see VariantType.isTestComponent
     */
    val testedVariant: VariantDslInfo?

    /**
     * Returns a full name that includes the given splits name.
     *
     * @param splitName the split name
     * @return a unique name made up of the variant and split names.
     */
    fun computeFullNameWithSplits(splitName: String): String

    /**
     * Returns the expected output file name for the variant.
     *
     * @param archivesBaseName the project's archiveBaseName
     * @param baseName the variant baseName
     */
    fun getOutputFileName(archivesBaseName: String, baseName: String): String {
        // we only know if it is signed during configuration, if its the base module.
        // Otherwise, don't differentiate between signed and unsigned.
        // we only know if it is signed during configuration, if its the base module.
        // Otherwise, don't differentiate between signed and unsigned.
        val suffix =
            if (isSigningReady || !variantType.isBaseModule)
                SdkConstants.DOT_ANDROID_PACKAGE
            else "-unsigned.apk"
        return archivesBaseName + "-" + baseName + suffix
    }

    /**
     * Returns the full, unique name of the variant, including BuildType, flavors and test, dash
     * separated. (similar to full name but with dashes)
     *
     * @return the name of the variant
     */
    val baseName : String
    /**
     * Returns a base name that includes the given splits name.
     *
     * @param splitName the split name
     * @return a unique name made up of the variant and split names.
     */
    fun computeBaseNameWithSplits(splitName: String): String

    /**
     * Returns a unique directory name (can include multiple folders) for the variant, based on
     * build type, flavor and test.
     *
     *
     * This always uses forward slashes ('/') as separator on all platform.
     *
     * @return the directory name for the variant
     */
    val dirName: String


    /**
     * Returns a unique directory name (can include multiple folders) for the variant, based on
     * build type, flavor and test.
     *
     * @return the directory name for the variant
     */
    val directorySegments: Collection<String?>

    /**
     * Returns a unique directory name (can include multiple folders) for the variant, based on
     * build type, flavor and test, and splits.
     *
     *
     * This always uses forward slashes ('/') as separator on all platform.
     *
     * @return the directory name for the variant
     */
    fun computeDirNameWithSplits(vararg splitNames: String): String

    /**
     * Return the names of the applied flavors.
     *
     *
     * The list contains the dimension names as well.
     *
     * @return the list, possibly empty if there are no flavors.
     */
    val flavorNamesWithDimensionNames: List<String>

    fun hasFlavors(): Boolean

    /**
     * The namespace of the generated R and BuildConfig classes. Also, the namespace used to resolve
     * any relative class names that are declared in the AndroidManifest.xml.
     *
     * For test components, this is set to the tested variant's [testNamespace] value or to the
     * tested variant's [namespace] + ".test"
     *
     * Otherwise, this value comes from the namespace DSL element, if present, or from the `package`
     * attribute in the source AndroidManifest.xml if not specified in the DSL.
     */
    val namespace: Provider<String>

    /**
     * The namespace for the R class for AndroidTest.
     *
     * This is a special case due to legacy reasons.
     *
     *  TODO(b/176931684) Remove and use [namespace] after we stop supporting using applicationId
     *   to namespace the test component R class.
     */
    val namespaceForR: Provider<String>

    /**
     * Returns the application ID for this variant. This could be coming from the manifest or could
     * be overridden through the product flavors and/or the build type.
     *
     * @return the application ID
     */
    val applicationId: Property<String>

    /**
     * Returns the version name for this variant. This could be specified by the product flavors,
     * or, if not, it could be coming from the manifest. A suffix may be specified by the build
     * type.
     *
     * @return the version name or null if none defined
     */
    val versionName: Provider<String?>

    /**
     * Returns the version code for this variant. This could be specified by the product flavors,
     * or, if not, it could be coming from the manifest.
     *
     * @return the version code or -1 if there was none defined.
     */
    val versionCode: Provider<Int?>

    /**
     * Return the minSdkVersion for this variant.
     *
     *
     * This uses both the value from the manifest (if present), and the override coming from the
     * flavor(s) (if present).
     *
     * @return the minSdkVersion
     */
    val minSdkVersion: MutableAndroidVersion

    val maxSdkVersion: Int?

    /**
     * Return the targetSdkVersion for this variant.
     *
     *
     * This uses both the value from the manifest (if present), and the override coming from the
     * flavor(s) (if present).
     *
     * @return the targetSdkVersion
     */
    val targetSdkVersion: MutableAndroidVersion?

    val renderscriptTarget: Int

    val isWearAppUnbundled: Boolean?

    val missingDimensionStrategies: ImmutableMap<String, AbstractProductFlavor.DimensionRequest>

    val resourceConfigurations: ImmutableSet<String>

    val vectorDrawables: VectorDrawablesOptions

    /**
     * Returns a list of items for the BuildConfig class.
     *
     *
     * Items can be either fields (instance of [com.android.builder.model.ClassField]) or
     * comments (instance of String).
     *
     * @return a list of items.
     */
    fun getBuildConfigFields(): Map<String, BuildConfigField<out java.io.Serializable>>

    /**
     * Returns a list of generated resource values.
     *
     *
     * Items can be either fields (instance of [com.android.builder.model.ClassField]) or
     * comments (instance of String).
     *
     * @return a list of items.
     */
    fun getResValues(): Map<ResValue.Key, ResValue>

    val isTestCoverageEnabled: Boolean

    val isUnitTestCoverageEnabled: Boolean

    /**
     * Returns the merged manifest placeholders. All product flavors are merged first, then build
     * type specific placeholders are added and potentially overrides product flavors values.
     *
     * @return the merged manifest placeholders for a build variant.
     */
    val manifestPlaceholders: Map<String, String>

    // Only require specific multidex opt-in for legacy multidex.
    val isMultiDexEnabled: Boolean?

    // dynamic features can always be build in native multidex mode
    val dexingType: DexingType?

    /** Returns true if the variant output is a bundle.  */
    val isBundled: Boolean

    /**
     * Returns the API to which device/emulator we're deploying via the IDE or null if not.
     * Can be used to optimize some build steps when deploying via the IDE.
     *
     * This has no relation with targetSdkVersion from build.gradle/manifest.
     */
    val targetDeployApiFromIDE: Int?

    val nativeBuildSystem: VariantManager.NativeBuiltType?

    val ndkConfig: MergedNdkConfig

    val externalNativeBuildOptions: CoreExternalNativeBuildOptions

    /**
     * Returns the ABI filters associated with the artifact, or empty set if there are no filters.
     *
     * If the list contains values, then the artifact only contains these ABIs and excludes
     * others.
     */
    val supportedAbis: Set<String>

    fun getProguardFiles(into: ListProperty<RegularFile>)

    fun gatherProguardFiles(type: ProguardFileType, into: ListProperty<RegularFile>)

    val javaCompileOptions: JavaCompileOptions

    fun getPostProcessingOptions(): PostProcessingOptions

    val defaultGlslcArgs: List<String>

    val scopedGlslcArgs: Map<String, List<String>>

    val isDebuggable: Boolean

    val isEmbedMicroApp: Boolean

    val isPseudoLocalesEnabled: Boolean

    val isCrunchPngs: Boolean?

    @Deprecated("Can be removed once the AaptOptions crunch method is removed.")
    val isCrunchPngsDefault: Boolean

    val isRenderscriptDebuggable: Boolean

    val isJniDebuggable: Boolean

    val aarMetadata: MergedAarMetadata

    val publishInfo: VariantPublishingInfo?

    val testFixtures: TestFixtures

    val androidResources: AndroidResources

    val packaging: PackagingOptions

    val compileOptions: CompileOptions

    val transforms: List<Transform>

    val lintOptions: Lint

    ////////////////////////////////////////////////////////////////////////////////////////
    //  APIs below should only be used at CreationConfig/Variant instantiation time       //
    //  DO NOT USE THOSE IN TASKS                                                         //
    ////////////////////////////////////////////////////////////////////////////////////////

    // DO NOT USE, Use CreationConfig and subtypes methods.
    val multiDexKeepFile: File?
    // DO NOT USE, Use CreationConfig and subtypes methods.
    val multiDexKeepProguard: File?

    /**
     * Returns handleProfiling value to use to test this variant, or if the variant is a test, the
     * one to use to test the tested variant.
     *
     * @return the handleProfiling value
     */
    // DO NOT USE, Use CreationConfig and subtypes methods.
    val handleProfiling: Provider<Boolean>

    /**
     * Returns functionalTest value to use to test this variant, or if the variant is a test, the
     * one to use to test the tested variant.
     *
     * @return the functionalTest value
     */
    // DO NOT USE, Use CreationConfig and subtypes methods.
    val functionalTest: Provider<Boolean>

    /** Gets the test label for this variant  */
    // DO NOT USE, Use CreationConfig and subtypes methods.
    val testLabel: Provider<String?>

    /**
     * Returns the instrumentationRunner to use to test this variant, or if the variant is a test,
     * the one to use to test the tested variant.
     *
     * @param dexingType the selected dexing type for this variant.
     * @return the instrumentation test runner name
     */
    // DO NOT USE, Use CreationConfig and subtypes methods.
    fun getInstrumentationRunner(dexingType: DexingType): Provider<String>

    /**
     * Returns the instrumentationRunner arguments to use to test this variant, or if the variant is
     * a test, the ones to use to test the tested variant
     */
    // DO NOT USE, Use CreationConfig and subtypes methods.
    val instrumentationRunnerArguments: Map<String, String>

    /** Holds all SigningConfig information from the DSL and/or [ProjectOptions].  */
    // DO NOT USE, Use CreationConfig and subtypes methods.
    val signingConfig: SigningConfig?

    // DO NOT USE, Use CreationConfig and subtypes methods.
    val isSigningReady: Boolean

    /** Returns the renderscript support mode.  */
    // DO NOT USE, Use CreationConfig and subtypes methods.
    val renderscriptSupportModeEnabled: Boolean

    /** Returns the renderscript BLAS support mode.  */
    // DO NOT USE, Use CreationConfig and subtypes methods.
    val renderscriptSupportModeBlasEnabled: Boolean

    /** Returns the renderscript NDK mode.  */
    // DO NOT USE, Use CreationConfig and subtypes methods.
    val renderscriptNdkModeEnabled: Boolean

    // DO NOT USE, Use CreationConfig and subtypes methods.
    val renderscriptOptimLevel: Int

    // DO NOT USE, Use CreationConfig and subtypes methods.
    val experimentalProperties: Map<String, Any>
}
