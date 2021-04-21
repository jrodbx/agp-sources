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
import com.android.build.api.component.ComponentIdentity
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.impl.ResValue
import com.android.build.gradle.api.JavaCompileOptions
import com.android.build.gradle.internal.ProguardFileType
import com.android.build.gradle.internal.dsl.CoreExternalNativeBuildOptions
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.builder.core.AbstractProductFlavor
import com.android.builder.core.VariantType
import com.android.builder.dexing.DexingType
import com.android.builder.model.ApiVersion
import com.android.builder.model.VectorDrawablesOptions
import com.android.sdklib.AndroidVersion
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File

/**
 * Represents a variant, initialized from the DSL object model (default config, build type, flavors)
 *
 * This class allows querying for the values set via the DSL model.
 *
 * Use [VariantBuilder] to instantiate.
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
     * The Package Name of the variant.
     *
     * This is the package name original present in the manifest (for non-test variants).
     *
     * This is not impacted by (test)ApplicationId values coming from the manifest.
     *
     * For test components, this is the package name of the tested variant + '.test'
     */
    val packageName: Provider<String>

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
     * Returns the instrumentationRunner to use to test this variant, or if the variant is a test,
     * the one to use to test the tested variant.
     *
     * @return the instrumentation test runner name
     */
    val instrumentationRunner: Provider<String>

    /**
     * Returns the instrumentationRunner arguments to use to test this variant, or if the variant is
     * a test, the ones to use to test the tested variant
     */
    val instrumentationRunnerArguments: Map<String, String>

    /**
     * Returns handleProfiling value to use to test this variant, or if the variant is a test, the
     * one to use to test the tested variant.
     *
     * @return the handleProfiling value
     */
    val handleProfiling: Provider<Boolean>

    /**
     * Returns functionalTest value to use to test this variant, or if the variant is a test, the
     * one to use to test the tested variant.
     *
     * @return the functionalTest value
     */
    val functionalTest: Provider<Boolean>

    /** Gets the test label for this variant  */
    val testLabel: Provider<String?>

    /**
     * Return the minSdkVersion for this variant.
     *
     *
     * This uses both the value from the manifest (if present), and the override coming from the
     * flavor(s) (if present).
     *
     * @return the minSdkVersion
     */
    val minSdkVersion: AndroidVersion

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
    val targetSdkVersion: ApiVersion

    val renderscriptTarget: Int

    val isWearAppUnbundled: Boolean?

    val missingDimensionStrategies: ImmutableMap<String, AbstractProductFlavor.DimensionRequest>

    val resourceConfigurations: ImmutableSet<String>

    val vectorDrawables: VectorDrawablesOptions

    /**
     * Adds a variant-specific BuildConfig field.
     *
     * @param type the type of the field
     * @param name the name of the field
     * @param value the value of the field
     */
    fun addBuildConfigField(
        type: String, name: String, value: String
    )

    /**
     * Adds a variant-specific res value.
     *
     * @param type the type of the field
     * @param name the name of the field
     * @param value the value of the field
     */
    fun addResValue(type: String, name: String, value: String)

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

    val signingConfig: SigningConfig?

    val isSigningReady: Boolean

    val isTestCoverageEnabled: Boolean

    /**
     * Returns the merged manifest placeholders. All product flavors are merged first, then build
     * type specific placeholders are added and potentially overrides product flavors values.
     *
     * @return the merged manifest placeholders for a build variant.
     */
    val manifestPlaceholders: Map<String, String>

    // Only require specific multidex opt-in for legacy multidex.
    val isMultiDexEnabled: Boolean

    val multiDexKeepFile: File?

    val multiDexKeepProguard: File?

    val isLegacyMultiDexMode: Boolean

    // dynamic features can always be build in native multidex mode
    val dexingType: DexingType

    /** Returns the renderscript support mode.  */
    val renderscriptSupportModeEnabled: Boolean

    /** Returns the renderscript BLAS support mode.  */
    val renderscriptSupportModeBlasEnabled: Boolean

    /** Returns the renderscript NDK mode.  */
    val renderscriptNdkModeEnabled: Boolean

    /** Returns true if the variant output is a bundle.  */
    val isBundled: Boolean

    /**
     * Returns the minimum SDK version for this variant, potentially overridden by a property passed
     * by the IDE.
     *
     * @see .getMinSdkVersion
     */
    val minSdkVersionWithTargetDeviceApi: AndroidVersion

    val ndkConfig: MergedNdkConfig

    val externalNativeBuildOptions: CoreExternalNativeBuildOptions

    /**
     * Returns the ABI filters associated with the artifact, or null if there are no filters.
     *
     * If the list contains values, then the artifact only contains these ABIs and excludes
     * others.
     */
    val supportedAbis: Set<String>


    fun gatherProguardFiles(type: ProguardFileType): List<File>

    val javaCompileOptions: JavaCompileOptions

    fun createPostProcessingOptions(buildDirectory: DirectoryProperty) : PostProcessingOptions

    val defaultGlslcArgs: List<String>

    val scopedGlslcArgs: Map<String, List<String>>

    val isDebuggable: Boolean

    val isEmbedMicroApp: Boolean

    val isPseudoLocalesEnabled: Boolean

    val isCrunchPngs: Boolean?

    @Deprecated("Can be removed once the AaptOptions crunch method is removed.")
    val isCrunchPngsDefault: Boolean

    val isMinifyEnabled: Boolean

    val isRenderscriptDebuggable: Boolean

    val renderscriptOptimLevel: Int

    val isJniDebuggable: Boolean

    val aarMetadata: MergedAarMetadata
}
