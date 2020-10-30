/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.api.component.ComponentIdentity
import com.android.build.gradle.ProguardFiles
import com.android.build.gradle.api.JavaCompileOptions
import com.android.build.gradle.internal.PostprocessingFeatures
import com.android.build.gradle.internal.ProguardFileType
import com.android.build.gradle.internal.core.MergedFlavor.Companion.mergeFlavors
import com.android.build.gradle.internal.dsl.BaseFlavor
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.BuildType.PostProcessingConfiguration
import com.android.build.gradle.internal.dsl.CoreExternalNativeBuildOptions
import com.android.build.gradle.internal.dsl.CoreNdkOptions
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.variant.DimensionCombination
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.android.builder.core.AbstractProductFlavor
import com.android.builder.core.DefaultApiVersion
import com.android.builder.core.DefaultManifestParser
import com.android.builder.core.ManifestAttributeSupplier
import com.android.builder.core.VariantAttributesProvider
import com.android.builder.core.VariantType
import com.android.builder.dexing.DexingType
import com.android.builder.errors.IssueReporter
import com.android.builder.internal.ClassFieldImpl
import com.android.builder.model.ApiVersion
import com.android.builder.model.BaseConfig
import com.android.builder.model.ClassField
import com.android.builder.model.CodeShrinker
import com.android.builder.model.VectorDrawablesOptions
import com.android.sdklib.AndroidVersion
import com.android.utils.combineAsCamelCase
import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.collect.Sets
import org.gradle.api.Project
import java.io.File
import java.util.ArrayList
import java.util.function.BooleanSupplier
import java.util.function.IntSupplier
import java.util.function.Supplier

/**
 * Represents a variant, initialized from the DSL object model (default config, build type, flavors)
 *
 * This class allows querying for the values set via the DSL model.
 *
 * Use [VariantBuilder] to instantiate.
 *
 */
open class VariantDslInfoImpl internal constructor(
    override val componentIdentity: ComponentIdentity,
    override val variantType: VariantType,
    private val defaultConfig: DefaultConfig,
    manifestFile: File,
    /**
     * Public because this is needed by the old Variant API. Nothing else should touch this.
     */
     val buildTypeObj: BuildType,
    /** The list of product flavors. Items earlier in the list override later items.  */
    override val productFlavorList: List<ProductFlavor>,
    private val signingConfigOverride: SigningConfig? = null,
    manifestAttributeSupplier: ManifestAttributeSupplier? = null,
    private val testedVariantImpl: VariantDslInfoImpl? = null,
    private val projectOptions: ProjectOptions,
    private val issueReporter: IssueReporter,
    isInExecutionPhase: BooleanSupplier
): VariantDslInfo, DimensionCombination {

    override val buildType: String?
        get() = componentIdentity.buildType
    override val productFlavors: List<Pair<String, String>>
        get() = componentIdentity.productFlavors
    /**
     * This should be mostly private and not used outside of this class.
     * Unfortunately there are a few cases where this cannot happen.
     *
     * Still, DO NOT USE. You should mostly use [VariantDslInfo] which does not give access to this.
     */
    val mergedFlavor: MergedFlavor = mergeFlavors(defaultConfig, productFlavorList, issueReporter)

    /** Variant-specific build Config fields.  */
    private val mBuildConfigFields: MutableMap<String, ClassField> = Maps.newTreeMap()

    /** Variant-specific res values.  */
    private val mResValues: MutableMap<String, ClassField> = Maps.newTreeMap()

    /**
     * Optional tested config in case this variant is used for testing another variant.
     *
     * @see VariantType.isTestComponent
     */
    override val testedVariant: VariantDslInfo?
        get() = testedVariantImpl

    /**
     * For reading the attributes from the main manifest file in the default source set, combining
     * the results with the current flavor.
     */
    private val mVariantAttributesProvider: VariantAttributesProvider

    private val mergedNdkConfig = MergedNdkConfig()
    private val mergedExternalNativeBuildOptions =
        MergedExternalNativeBuildOptions()
    private val mergedJavaCompileOptions = MergedJavaCompileOptions()

    init {
        val manifestParser =
            manifestAttributeSupplier
                ?: DefaultManifestParser(
                    manifestFile,
                    isInExecutionPhase,
                    variantType.requiresManifest,
                    issueReporter
                )
        mVariantAttributesProvider = VariantAttributesProvider(
            mergedFlavor,
            buildTypeObj,
            variantType.isTestComponent,
            manifestParser,
            manifestFile,
            componentIdentity.name
        )
        mergeOptions()
    }


    /**
     * Returns a full name that includes the given splits name.
     *
     * @param splitName the split name
     * @return a unique name made up of the variant and split names.
     */
    override fun computeFullNameWithSplits(splitName: String): String {
        return VariantBuilder.computeFullNameWithSplits(
            componentIdentity,
            variantType,
            splitName
        )
    }

    /**
     * Returns the full, unique name of the variant, including BuildType, flavors and test, dash
     * separated. (similar to full name but with dashes)
     *
     * @return the name of the variant
     */
    override val baseName : String by lazy {
        VariantBuilder.computeBaseName(this, variantType)
    }

    /**
     * Returns a base name that includes the given splits name.
     *
     * @param splitName the split name
     * @return a unique name made up of the variant and split names.
     */
    override fun computeBaseNameWithSplits(splitName: String): String {
        val sb = StringBuilder()
        if (productFlavorList.isNotEmpty()) {
            for (pf in productFlavorList) {
                sb.append(pf.name).append('-')
            }
        }
        sb.append(splitName).append('-')
        sb.append(buildTypeObj.name)
        if (variantType.isTestComponent) {
            sb.append('-').append(variantType.prefix)
        }
        return sb.toString()
    }

    /**
     * Returns a unique directory name (can include multiple folders) for the variant, based on
     * build type, flavor and test.
     *
     *
     * This always uses forward slashes ('/') as separator on all platform.
     *
     * @return the directory name for the variant
     */
    override val dirName: String by lazy {
        Joiner.on('/').join(directorySegments)
    }

    /**
     * Returns a unique directory name (can include multiple folders) for the variant, based on
     * build type, flavor and test.
     *
     * @return the directory name for the variant
     */
    override val directorySegments: Collection<String?> by lazy {
        val builder =
            ImmutableList.builder<String>()
        if (variantType.isTestComponent) {
            builder.add(variantType.prefix)
        }
        if (productFlavorList.isNotEmpty()) {
            builder.add(
                combineAsCamelCase(
                    productFlavorList, ProductFlavor::getName
                )
            )
        }
        builder.add(buildTypeObj.name)
        builder.build()
    }

    /**
     * Returns a unique directory name (can include multiple folders) for the variant, based on
     * build type, flavor and test, and splits.
     *
     *
     * This always uses forward slashes ('/') as separator on all platform.
     *
     * @return the directory name for the variant
     */
    override fun computeDirNameWithSplits(vararg splitNames: String): String {
        val sb = StringBuilder()
        if (variantType.isTestComponent) {
            sb.append(variantType.prefix).append("/")
        }
        if (productFlavorList.isNotEmpty()) {
            for (flavor in productFlavorList) {
                sb.append(flavor.name)
            }
            sb.append('/')
        }
        for (splitName in splitNames) {
            sb.append(splitName).append('/')
        }
        sb.append(buildTypeObj.name)
        return sb.toString()
    }

    /**
     * Return the names of the applied flavors.
     *
     *
     * The list contains the dimension names as well.
     *
     * @return the list, possibly empty if there are no flavors.
     */
    override val flavorNamesWithDimensionNames: List<String>
        get() {
            if (productFlavorList.isEmpty()) {
                return emptyList()
            }
            val names: List<String>
            val count = productFlavorList.size
            if (count > 1) {
                names =
                    Lists.newArrayListWithCapacity(count * 2)
                for (i in 0 until count) {
                    names.add(productFlavorList[i].name)
                    names.add(productFlavorList[i].dimension)
                }
            } else {
                names = listOf(productFlavorList[0].name)
            }
            return names
        }


    override fun hasFlavors(): Boolean {
        return productFlavorList.isNotEmpty()
    }

    private val testedPackage: String
        get() = testedVariant?.applicationId ?: ""

    /**
     * Returns the original application ID before any overrides from flavors. If the variant is a
     * test variant, then the application ID is the one coming from the configuration of the tested
     * variant, and this call is similar to [.getApplicationId]
     *
     * @return the original application ID
     */
    override val originalApplicationId: String
        get() = mVariantAttributesProvider.getOriginalApplicationId(testedPackage)

    /**
     * Returns the application ID for this variant. This could be coming from the manifest or could
     * be overridden through the product flavors and/or the build type.
     *
     * @return the application ID
     */
    override val applicationId: String
        get() = mVariantAttributesProvider.getApplicationId(testedPackage)

    override val testApplicationId: String
        get() = mVariantAttributesProvider.getTestApplicationId(testedPackage)

    override val testedApplicationId: String?
        get() {
            if (variantType.isTestComponent) {
                val tested = testedVariant!!
                return if (tested.variantType.isAar) {
                    applicationId
                } else {
                    tested.applicationId
                }
            }
            return null
        }

    /**
     * Returns the application id override value coming from the Product Flavor and/or the Build
     * Type. If the package/id is not overridden then this returns null.
     *
     * @return the id override or null
     */
    override val idOverride: String?
        get() = mVariantAttributesProvider.idOverride

    /**
     * Returns the version name for this variant. This could be specified by the product flavors,
     * or, if not, it could be coming from the manifest. A suffix may be specified by the build
     * type.
     *
     * @return the version name or null if none defined
     */
    override val versionName: String?
        get() {
            val override =
                projectOptions[StringOption.IDE_VERSION_NAME_OVERRIDE]
            return override ?: getVersionName(false)
        }

    /**
     * Returns the version name for this variant. This could be specified by the product flavors,
     * or, if not, it could be coming from the manifest. A suffix may be specified by the build
     * type.
     *
     * @param ignoreManifest whether or not the manifest is ignored when getting the version code
     * @return the version name or null if none defined
     */
    override fun getVersionName(ignoreManifest: Boolean): String? {
        return mVariantAttributesProvider.getVersionName(ignoreManifest)
    }

    /**
     * Returns the version code for this variant. This could be specified by the product flavors,
     * or, if not, it could be coming from the manifest.
     *
     * @return the version code or -1 if there was none defined.
     */
    override val versionCode: Int
        get() {
            val override =
                projectOptions[IntegerOption.IDE_VERSION_CODE_OVERRIDE]
            return override ?: getVersionCode(false)
        }

    /**
     * Returns the version code for this variant. This could be specified by the product flavors,
     * or, if not, it could be coming from the manifest.
     *
     * @param ignoreManifest whether or not the manifest is ignored when getting the version code
     * @return the version code or -1 if there was none defined.
     */
    override fun getVersionCode(ignoreManifest: Boolean): Int {
        return mVariantAttributesProvider.getVersionCode(ignoreManifest)
    }

    override val manifestVersionNameSupplier: Supplier<String?>
        get() = mVariantAttributesProvider.manifestVersionNameSupplier

    override val manifestVersionCodeSupplier: IntSupplier
        get() = mVariantAttributesProvider.manifestVersionCodeSupplier

    /**
     * Returns the instrumentationRunner to use to test this variant, or if the variant is a test,
     * the one to use to test the tested variant.
     *
     * @return the instrumentation test runner name
     */
    override val instrumentationRunner: String
        get() {
            val variantDslInfo: VariantDslInfoImpl =
                if (variantType.isTestComponent) {
                    testedVariantImpl!!
                } else {
                    this
                }
            val runner = variantDslInfo.mVariantAttributesProvider.instrumentationRunner
            if (runner != null) {
                return runner
            }
            return if (isLegacyMultiDexMode) {
                MULTIDEX_TEST_RUNNER
            } else DEFAULT_TEST_RUNNER
        }

    /**
     * Returns the instrumentationRunner arguments to use to test this variant, or if the variant is
     * a test, the ones to use to test the tested variant
     */
    override val instrumentationRunnerArguments: Map<String, String>
        get() {
            val variantDslInfo: VariantDslInfoImpl =
                if (variantType.isTestComponent) {
                    testedVariantImpl!!
                } else {
                    this
                }
            return variantDslInfo.mergedFlavor.testInstrumentationRunnerArguments
        }

    /**
     * Returns handleProfiling value to use to test this variant, or if the variant is a test, the
     * one to use to test the tested variant.
     *
     * @return the handleProfiling value
     */
    override val handleProfiling: Boolean
        get() {
            val variantDslInfo: VariantDslInfoImpl =
                if (variantType.isTestComponent) {
                    testedVariantImpl!!
                } else {
                    this
                }
            return variantDslInfo.mVariantAttributesProvider.handleProfiling ?: DEFAULT_HANDLE_PROFILING
        }

    /**
     * Returns functionalTest value to use to test this variant, or if the variant is a test, the
     * one to use to test the tested variant.
     *
     * @return the functionalTest value
     */
    override val functionalTest: Boolean
        get() {
            val variantDslInfo: VariantDslInfoImpl =
                if (variantType.isTestComponent) {
                    testedVariantImpl!!
                } else {
                    this
                }
            return variantDslInfo.mVariantAttributesProvider.functionalTest ?: DEFAULT_FUNCTIONAL_TEST
        }

    /** Gets the test label for this variant  */
    override val testLabel: String?
        get() = mVariantAttributesProvider.testLabel

    /** Reads the package name from the manifest. This is unmodified by the build type.  */
    override val packageFromManifest: String
        get() = mVariantAttributesProvider.packageName// default to 1 for minSdkVersion.

    /**
     * Return the minSdkVersion for this variant.
     *
     *
     * This uses both the value from the manifest (if present), and the override coming from the
     * flavor(s) (if present).
     *
     * @return the minSdkVersion
     */
    override val minSdkVersion: AndroidVersion
        get() {
            if (testedVariantImpl != null) {
                return testedVariantImpl.minSdkVersion
            }
            var minSdkVersion = mergedFlavor.minSdkVersion
            if (minSdkVersion == null) { // default to 1 for minSdkVersion.
                minSdkVersion =
                    DefaultApiVersion.create(Integer.valueOf(1))
            }
            return AndroidVersion(
                minSdkVersion.apiLevel,
                minSdkVersion.codename
            )
        }

    override val maxSdkVersion: Int?
        get() = mergedFlavor.maxSdkVersion
    /**
     * Return the targetSdkVersion for this variant.
     *
     *
     * This uses both the value from the manifest (if present), and the override coming from the
     * flavor(s) (if present).
     *
     * @return the targetSdkVersion
     */
    override val targetSdkVersion: ApiVersion
        get() {
            if (testedVariantImpl != null) {
                return testedVariantImpl.targetSdkVersion
            }
            var targetSdkVersion =
                mergedFlavor.targetSdkVersion
            if (targetSdkVersion == null) { // default to -1 if not in build.gradle file.
                targetSdkVersion =
                    DefaultApiVersion.create(Integer.valueOf(-1))
            }
            return targetSdkVersion
        }

    override val renderscriptTarget: Int
        get() {
            val targetApi = mergedFlavor.renderscriptTargetApi ?: -1

            // default to -1 if not in build.gradle file.
            val minSdk = minSdkVersion.featureLevel

            return if (targetApi > minSdk) targetApi else minSdk
        }

    override val isWearAppUnbundled: Boolean?
        get() = mergedFlavor.wearAppUnbundled

    @Suppress("DEPRECATION")
    override val missingDimensionStrategies: ImmutableMap<String, AbstractProductFlavor.DimensionRequest>
        get() = ImmutableMap.copyOf(mergedFlavor.missingDimensionStrategies)

    override val resourceConfigurations: ImmutableSet<String>
        get() = ImmutableSet.copyOf(mergedFlavor.resourceConfigurations)

    override val vectorDrawables: VectorDrawablesOptions
        get() = mergedFlavor.vectorDrawables

    /**
     * Adds a variant-specific BuildConfig field.
     *
     * @param type the type of the field
     * @param name the name of the field
     * @param value the value of the field
     */
    override fun addBuildConfigField(
        type: String, name: String, value: String
    ) {
        val classField: ClassField = ClassFieldImpl(type, name, value)
        mBuildConfigFields[name] = classField
    }

    /**
     * Adds a variant-specific res value.
     *
     * @param type the type of the field
     * @param name the name of the field
     * @param value the value of the field
     */
    override fun addResValue(type: String, name: String, value: String) {
        val classField: ClassField = ClassFieldImpl(type, name, value)
        mResValues[name] = classField
    }// keep track of the names already added. This is because we show where the items
// come from so we cannot just put everything a map and let the new ones override the
// old ones.

    /**
     * Returns a list of items for the BuildConfig class.
     *
     *
     * Items can be either fields (instance of [com.android.builder.model.ClassField]) or
     * comments (instance of String).
     *
     * @return a list of items.
     */
    override val buildConfigItems: List<Any>
        get() {
            val fullList: MutableList<Any> =
                Lists.newArrayList()
            // keep track of the names already added. This is because we show where the items
            // come from so we cannot just put everything a map and let the new ones override the
            // old ones.
            val usedFieldNames = mutableSetOf<String>()

            var list: Collection<ClassField> = mBuildConfigFields.values
            if (!list.isEmpty()) {
                fullList.add("Fields from the variant")
                fillFieldList(fullList, usedFieldNames, list)
            }
            list = buildTypeObj.buildConfigFields.values
            if (!list.isEmpty()) {
                fullList.add("Fields from build type: " + buildTypeObj.name)
                fillFieldList(fullList, usedFieldNames, list)
            }
            for (flavor in productFlavorList) {
                list = flavor.buildConfigFields.values
                if (!list.isEmpty()) {
                    fullList.add("Fields from product flavor: " + flavor.name)
                    fillFieldList(fullList, usedFieldNames, list)
                }
            }
            list = defaultConfig.buildConfigFields.values
            if (!list.isEmpty()) {
                fullList.add("Fields from default config.")
                fillFieldList(fullList, usedFieldNames, list)
            }
            return fullList
        }// start from the lowest priority and just add it all. Higher priority fields
// will replace lower priority ones.

    /**
     * Return the merged build config fields for the variant.
     *
     *
     * This is made of the variant-specific fields overlaid on top of the build type ones, the
     * flavors ones, and the default config ones.
     *
     * @return a map of merged fields
     */
    override val mergedBuildConfigFields: Map<String, ClassField>
        get() {
            val mergedMap: MutableMap<String, ClassField> = Maps.newHashMap()

            // start from the lowest priority and just add it all. Higher priority fields
            // will replace lower priority ones.
            mergedMap.putAll(defaultConfig.buildConfigFields)
            for (i in productFlavorList.indices.reversed()) {
                mergedMap.putAll(productFlavorList[i].buildConfigFields)
            }
            mergedMap.putAll(buildTypeObj.buildConfigFields)
            mergedMap.putAll(mBuildConfigFields)
            return mergedMap
        }

    /**
     * Return the merged res values for the variant.
     *
     *
     * This is made of the variant-specific fields overlaid on top of the build type ones, the
     * flavors ones, and the default config ones.
     *
     * @return a map of merged fields
     */
    override val mergedResValues: Map<String, ClassField>
        get() {
            // start from the lowest priority and just add it all. Higher priority fields
            // will replace lower priority ones.
            val mergedMap: MutableMap<String, ClassField> = Maps.newHashMap()
            mergedMap.putAll(defaultConfig.resValues)
            for (i in productFlavorList.indices.reversed()) {
                mergedMap.putAll(productFlavorList[i].resValues)
            }
            mergedMap.putAll(buildTypeObj.resValues)
            mergedMap.putAll(mResValues)
            return mergedMap
        }

    /**
     * Returns a list of generated resource values.
     *
     *
     * Items can be either fields (instance of [com.android.builder.model.ClassField]) or
     * comments (instance of String).
     *
     * @return a list of items.
     */
    override val resValues: List<Any>
        get() {
            val fullList: MutableList<Any> =
                Lists.newArrayList()
            // keep track of the names already added. This is because we show where the items
            // come from so we cannot just put everything a map and let the new ones override the
            // old ones.
            val usedFieldNames: MutableSet<String> = Sets.newHashSet()
            var list: Collection<ClassField> = mResValues.values
            if (!list.isEmpty()) {
                fullList.add("Values from the variant")
                fillFieldList(fullList, usedFieldNames, list)
            }
            list = buildTypeObj.resValues.values
            if (!list.isEmpty()) {
                fullList.add("Values from build type: " + buildTypeObj.name)
                fillFieldList(fullList, usedFieldNames, list)
            }
            for (flavor in productFlavorList) {
                list = flavor.resValues.values
                if (!list.isEmpty()) {
                    fullList.add("Values from product flavor: " + flavor.name)
                    fillFieldList(fullList, usedFieldNames, list)
                }
            }
            list = defaultConfig.resValues.values
            if (!list.isEmpty()) {
                fullList.add("Values from default config.")
                fillFieldList(fullList, usedFieldNames, list)
            }
            return fullList
        }

    override val signingConfig: SigningConfig?
        get() {
            if (variantType.isDynamicFeature) {
                return null
            }
            if (signingConfigOverride != null) {
                return signingConfigOverride
            }
            val signingConfig: SigningConfig? = buildTypeObj.signingConfig
            // cast builder.SigningConfig to dsl.SigningConfig because MergedFlavor merges
            // dsl.SigningConfig of ProductFlavor objects
            return signingConfig ?: mergedFlavor.signingConfig as SigningConfig?
        }

    override val isSigningReady: Boolean
        get() {
            val signingConfig = signingConfig
            return signingConfig != null && signingConfig.isSigningReady
        }

    override val isTestCoverageEnabled: Boolean
        get() = buildTypeObj.isTestCoverageEnabled// so far, blindly override the build type placeholders

    /**
     * Returns the merged manifest placeholders. All product flavors are merged first, then build
     * type specific placeholders are added and potentially overrides product flavors values.
     *
     * @return the merged manifest placeholders for a build variant.
     */
    override val manifestPlaceholders: Map<String, Any>
        get() {
            val mergedFlavorsPlaceholders =
                mergedFlavor.manifestPlaceholders
            // so far, blindly override the build type placeholders
            mergedFlavorsPlaceholders.putAll(buildTypeObj.manifestPlaceholders)
            return mergedFlavorsPlaceholders
        }

    // Only require specific multidex opt-in for legacy multidex.
    override val isMultiDexEnabled: Boolean
        get() {
            // Only require specific multidex opt-in for legacy multidex.
            return buildTypeObj.multiDexEnabled
                ?: mergedFlavor.multiDexEnabled
                ?: (minSdkVersion.featureLevel >= 21)
        }

    override val multiDexKeepFile: File?
        get() {
            var value = buildTypeObj.multiDexKeepFile
            if (value != null) {
                return value
            }
            value = mergedFlavor.multiDexKeepFile
            return value
        }

    override val multiDexKeepProguard: File?
        get() {
            var value = buildTypeObj.multiDexKeepProguard
            if (value != null) {
                return value
            }
            value = mergedFlavor.multiDexKeepProguard
            return value
        }

    override val isLegacyMultiDexMode: Boolean
        get() = dexingType === DexingType.LEGACY_MULTIDEX

    // dynamic features can always be build in native multidex mode
    override val dexingType: DexingType
        get() = if (variantType.isDynamicFeature) {
            if (buildTypeObj.multiDexEnabled != null
                || mergedFlavor.multiDexEnabled != null
            ) {
                issueReporter
                    .reportWarning(
                        IssueReporter.Type.GENERIC,
                        "Native multidex is always used for dynamic features. Please "
                                + "remove 'multiDexEnabled true|false' from your "
                                + "build.gradle file."
                    )
            }
            // dynamic features can always be build in native multidex mode
            DexingType.NATIVE_MULTIDEX
        } else if (isMultiDexEnabled) {
            if (minSdkVersion.featureLevel < 21) DexingType.LEGACY_MULTIDEX else DexingType.NATIVE_MULTIDEX
        } else {
            DexingType.MONO_DEX
        }

    /** Returns the renderscript support mode.  */
    override val renderscriptSupportModeEnabled: Boolean
        get() = mergedFlavor.renderscriptSupportModeEnabled ?: false

    /** Returns the renderscript BLAS support mode.  */
    override val renderscriptSupportModeBlasEnabled: Boolean
        get() {
            val value = mergedFlavor.renderscriptSupportModeBlasEnabled
            return value ?: false
        }

    /** Returns the renderscript NDK mode.  */
    override val renderscriptNdkModeEnabled: Boolean
        get() = mergedFlavor.renderscriptNdkModeEnabled ?: false

    /** Returns true if the variant output is a bundle.  */
    override val isBundled: Boolean
        get() = variantType.isAar// Consider runtime API passed from the IDE only if multi-dex is enabled and the app is debuggable.

    /**
     * Returns the minimum SDK version for this variant, potentially overridden by a property passed
     * by the IDE.
     *
     * @see .getMinSdkVersion
     */
    override val minSdkVersionWithTargetDeviceApi: AndroidVersion
        get() {
            val targetApiLevel =
                projectOptions[IntegerOption.IDE_TARGET_DEVICE_API]
            return if (targetApiLevel != null && isMultiDexEnabled && buildTypeObj.isDebuggable) { // Consider runtime API passed from the IDE only if multi-dex is enabled and the app is
// debuggable.
                val minVersion: Int =
                    if (targetSdkVersion.apiLevel > 1) Integer.min(
                        targetSdkVersion.apiLevel,
                        targetApiLevel
                    ) else targetApiLevel
                AndroidVersion(minVersion)
            } else {
                minSdkVersion
            }
        }

    /**
     * Merge Gradle specific options from build types, product flavors and default config.
     */
    private fun mergeOptions() {
        computeMergedOptions(
            mergedJavaCompileOptions,
            { javaCompileOptions },
            { javaCompileOptions }
        )
        computeMergedOptions(
            mergedNdkConfig,
            { ndkConfig },
            { ndkConfig }
        )
        computeMergedOptions(
            mergedExternalNativeBuildOptions,
            { externalNativeBuildOptions },
            { externalNativeBuildOptions }
        )
    }

    override val ndkConfig: CoreNdkOptions
        get() = mergedNdkConfig

    override val externalNativeBuildOptions: CoreExternalNativeBuildOptions
        get() = mergedExternalNativeBuildOptions


    /**
     * Returns the ABI filters associated with the artifact, or null if there are no filters.
     *
     * If the list contains values, then the artifact only contains these ABIs and excludes
     * others.
     */
    override val supportedAbis: Set<String>?
        get() = mergedNdkConfig.abiFilters


    override fun gatherProguardFiles(type: ProguardFileType): List<File> {
        val result: MutableList<File> = ArrayList(defaultConfig.getProguardFiles(type))
        for (flavor in productFlavorList) {
            result.addAll(flavor.getProguardFiles(type))
        }
        return result
    }

    /**
     * Merge a specific option in GradleVariantConfiguration.
     *
     *
     * It is assumed that merged option type with a method to reset and append is created for the
     * option being merged.
     *
     *
     * The order of priority is BuildType, ProductFlavors, and default config. ProductFlavor
     * added earlier has higher priority than ProductFlavor added later.
     *
     * @param mergedOption The merged option store in the GradleVariantConfiguration.
     * @param getFlavorOption A Function to return the option from a ProductFlavor.
     * @param getBuildTypeOption A Function to return the option from a BuildType.
     * takes priority and overwrite option in the first input argument.
     * @param <CoreOptionsT> The core type of the option being merge.
     * @param <MergedOptionsT> The merge option type.
    </MergedOptionsT></CoreOptionsT> */
    private fun <CoreOptionsT, MergedOptionsT: MergedOptions<CoreOptionsT>> computeMergedOptions(
        mergedOption: MergedOptionsT,
        getFlavorOption: BaseFlavor.() -> CoreOptionsT?,
        getBuildTypeOption: BuildType.() -> CoreOptionsT?
    ) {
        mergedOption.reset()

        val defaultOption = defaultConfig.getFlavorOption()
        if (defaultOption != null) {
            mergedOption.append(defaultOption)
        }
        // reverse loop for proper order
        for (i in productFlavorList.indices.reversed()) {
            val flavorOption = productFlavorList[i].getFlavorOption()
            if (flavorOption != null) {
                mergedOption.append(flavorOption)
            }
        }
        val buildTypeOption = buildTypeObj.getBuildTypeOption()
        if (buildTypeOption != null) {
            mergedOption.append(buildTypeOption)
        }
    }

    override val javaCompileOptions: JavaCompileOptions
        get() = mergedJavaCompileOptions

    override fun createPostProcessingOptions(project: Project) : PostProcessingOptions {
        return if (buildTypeObj.postProcessingConfiguration == PostProcessingConfiguration.POSTPROCESSING_BLOCK) {
            PostProcessingBlockOptions(
                buildTypeObj.postprocessing, variantType.isTestComponent
            )
        } else object : PostProcessingOptions {
            override fun getProguardFiles(type: ProguardFileType): Collection<File> =
                buildTypeObj.getProguardFiles(type)

            override fun getDefaultProguardFiles(): List<File> =
                listOf(
                    ProguardFiles.getDefaultProguardFile(
                        ProguardFiles.ProguardFile.DONT_OPTIMIZE.fileName,
                        project.layout
                    )
                )

            override fun getPostprocessingFeatures(): PostprocessingFeatures? = null

            override fun getCodeShrinker() = when {
                !buildTypeObj.isMinifyEnabled -> null
                buildTypeObj.isUseProguard == true -> CodeShrinker.PROGUARD
                else -> CodeShrinker.R8
            }

            override fun resourcesShrinkingEnabled(): Boolean = buildTypeObj.isShrinkResources
        }
    }

    // add the lower priority one, to override them with the higher priority ones.
    // cant use merge flavor as it's not a prop on the base class.
    // reverse loop for proper order
    override val defaultGlslcArgs: List<String>
        get() {
            val optionMap: MutableMap<String, String> =
                Maps.newHashMap()
            // add the lower priority one, to override them with the higher priority ones.
            for (option in defaultConfig.shaders.glslcArgs) {
                optionMap[getKey(option)] = option
            }
            // cant use merge flavor as it's not a prop on the base class.
            // reverse loop for proper order
            for (i in productFlavorList.indices.reversed()) {
                for (option in productFlavorList[i].shaders.glslcArgs) {
                    optionMap[getKey(option)] = option
                }
            }
            // then the build type
            for (option in buildTypeObj.shaders.glslcArgs) {
                optionMap[getKey(option)] = option
            }
            return Lists.newArrayList(optionMap.values)
        }

    // first collect all possible keys.
    override val scopedGlslcArgs: Map<String, List<String>>
        get() {
            val scopedArgs: MutableMap<String, List<String>> =
                Maps.newHashMap()
            // first collect all possible keys.
            val keys = scopedGlslcKeys
            for (key in keys) { // first add to a temp map to resolve overridden values
                val optionMap: MutableMap<String, String> =
                    Maps.newHashMap()
                // we're going to go from lower priority, to higher priority elements, and for each
                // start with the non scoped version, and then add the scoped version.
                // 1. default config, global.
                for (option in defaultConfig.shaders.glslcArgs) {
                    optionMap[getKey(option)] = option
                }
                // 1b. default config, scoped.
                for (option in defaultConfig.shaders.scopedGlslcArgs[key]) {
                    optionMap[getKey(option)] = option
                }
                // 2. the flavors.
                // cant use merge flavor as it's not a prop on the base class.
                // reverse loop for proper order
                for (i in productFlavorList.indices.reversed()) { // global
                    for (option in productFlavorList[i].shaders.glslcArgs) {
                        optionMap[getKey(option)] = option
                    }
                    // scoped.
                    for (option in productFlavorList[i].shaders.scopedGlslcArgs[key]) {
                        optionMap[getKey(option)] = option
                    }
                }
                // 3. the build type, global
                for (option in buildTypeObj.shaders.glslcArgs) {
                    optionMap[getKey(option)] = option
                }
                // 3b. the build type, scoped.
                for (option in buildTypeObj.shaders.scopedGlslcArgs[key]) {
                    optionMap[getKey(option)] = option
                }
                // now add the full value list.
                scopedArgs[key] = ImmutableList.copyOf(optionMap.values)
            }
            return scopedArgs
        }

    private val scopedGlslcKeys: Set<String>
        get() {
            val keys: MutableSet<String> =
                Sets.newHashSet()
            keys.addAll(defaultConfig.shaders.scopedGlslcArgs.keySet())
            for (flavor in productFlavorList) {
                keys.addAll(flavor.shaders.scopedGlslcArgs.keySet())
            }
            keys.addAll(buildTypeObj.shaders.scopedGlslcArgs.keySet())
            return keys
        }

    override val isDebuggable: Boolean
        get() = buildTypeObj.isDebuggable

    override val isEmbedMicroApp: Boolean
        get() = buildTypeObj.isEmbedMicroApp

    override val isPseudoLocalesEnabled: Boolean
        get() = buildTypeObj.isPseudoLocalesEnabled

    override val isCrunchPngs: Boolean?
        get() = buildTypeObj.isCrunchPngs

    @Suppress("OverridingDeprecatedMember", "DEPRECATION")
    override val isCrunchPngsDefault: Boolean
        get() = buildTypeObj.isCrunchPngsDefault

    override val isMinifyEnabled: Boolean
        get() = buildTypeObj.isMinifyEnabled

    override val isRenderscriptDebuggable: Boolean
        get() = buildTypeObj.isRenderscriptDebuggable

    override val renderscriptOptimLevel: Int
        get() = buildTypeObj.renderscriptOptimLevel

    override val isJniDebuggable: Boolean
        get() = buildTypeObj.isJniDebuggable

    companion object {

        private const val DEFAULT_TEST_RUNNER = "android.test.InstrumentationTestRunner"
        private const val MULTIDEX_TEST_RUNNER =
            "com.android.test.runner.MultiDexTestRunner"
        private const val DEFAULT_HANDLE_PROFILING = false
        private const val DEFAULT_FUNCTIONAL_TEST = false

        /**
         * Fills a list of Object from a given list of ClassField only if the name isn't in a set. Each
         * new item added adds its name to the list.
         *
         * @param outList the out list
         * @param usedFieldNames the list of field names already in the list
         * @param list the list to copy items from
         */
        private fun fillFieldList(
            outList: MutableList<Any>,
            usedFieldNames: MutableSet<String>,
            list: Collection<ClassField>
        ) {
            for (f in list) {
                val name = f.name
                if (!usedFieldNames.contains(name)) {
                    usedFieldNames.add(f.name)
                    outList.add(f)
                }
            }
        }

        private fun getKey(fullOption: String): String {
            val pos = fullOption.lastIndexOf('=')
            return if (pos == -1) {
                fullOption
            } else fullOption.substring(0, pos)
        }
    }
}

private fun BaseConfig.getProguardFiles(type: ProguardFileType): Collection<File> = when(type) {
    ProguardFileType.EXPLICIT -> this.proguardFiles
    ProguardFileType.TEST -> this.testProguardFiles
    ProguardFileType.CONSUMER -> this.consumerProguardFiles
}
