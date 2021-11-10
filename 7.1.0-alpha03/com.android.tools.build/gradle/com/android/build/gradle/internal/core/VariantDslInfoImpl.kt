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
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.ResValue
import com.android.build.api.variant.impl.ResValueKeyImpl
import com.android.build.gradle.ProguardFiles
import com.android.build.gradle.api.JavaCompileOptions
import com.android.build.gradle.internal.PostprocessingFeatures
import com.android.build.gradle.internal.ProguardFileType
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.core.MergedFlavor.Companion.mergeFlavors
import com.android.build.gradle.internal.dsl.BaseFlavor
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.BuildType.PostProcessingConfiguration
import com.android.build.gradle.internal.dsl.CoreExternalNativeBuildOptions
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.manifest.ManifestDataProvider
import com.android.build.gradle.internal.publishing.VariantPublishingInfo
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import com.android.build.gradle.internal.variant.DimensionCombination
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.options.Version
import com.android.builder.core.AbstractProductFlavor
import com.android.builder.core.DefaultApiVersion
import com.android.builder.core.VariantType
import com.android.builder.dexing.DexingType
import com.android.builder.dexing.isLegacyMultiDexMode
import com.android.builder.errors.IssueReporter
import com.android.builder.model.ApiVersion
import com.android.builder.model.BaseConfig
import com.android.builder.model.ClassField
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
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File
import java.util.ArrayList
import java.util.concurrent.Callable

/**
 * Represents a variant, initialized from the DSL object model (default config, build type, flavors)
 *
 * This class allows querying for the values set via the DSL model.
 *
 * Use [VariantDslInfoBuilder] to instantiate.
 *
 */
open class VariantDslInfoImpl<CommonExtensionT: CommonExtension<*, *, *, *>> internal constructor(
    override val componentIdentity: ComponentIdentity,
    override val dslExtension: CommonExtensionT,
    override val variantType: VariantType,
    private val defaultConfig: DefaultConfig,
    /**
     * Public because this is needed by the old Variant API. Nothing else should touch this.
     */
     val buildTypeObj: BuildType,
    /** The list of product flavors. Items earlier in the list override later items.  */
    override val productFlavorList: List<ProductFlavor>,
    private val signingConfigOverride: SigningConfig? = null,
    private val testedVariantImpl: VariantDslInfoImpl<*>? = null,
    val dataProvider: ManifestDataProvider,
    @Deprecated("Only used for merged flavor")
    private val dslServices: DslServices,
    private val services: VariantPropertiesApiServices,
    private val buildDirectory: DirectoryProperty,
    private val dslNamespaceProvider: Provider<String>?,
    private val dslTestNamespace: String?,
    override val nativeBuildSystem: VariantManager.NativeBuiltType?,
    private val publishingInfo: VariantPublishingInfo?,
    override val experimentalProperties: Map<String, Any>,
    override val enableTestFixtures: Boolean,
): VariantDslInfo<CommonExtensionT>, DimensionCombination {

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
    val mergedFlavor: MergedFlavor by lazy {
        mergeFlavors(defaultConfig, productFlavorList, applicationId, dslServices)
    }

    /**
     * Optional tested config in case this variant is used for testing another variant.
     *
     * @see VariantType.isTestComponent
     */
    override val testedVariant: VariantDslInfo<*>?
        get() = testedVariantImpl

    private val mergedNdkConfig = MergedNdkConfig()
    private val mergedExternalNativeBuildOptions =
        MergedExternalNativeBuildOptions()
    private val mergedJavaCompileOptions = MergedJavaCompileOptions(dslServices)
    private val mergedAarMetadata = MergedAarMetadata()

    init {
        mergeOptions()
    }

    /**
     * Returns a full name that includes the given splits name.
     *
     * @param splitName the split name
     * @return a unique name made up of the variant and split names.
     */
    override fun computeFullNameWithSplits(splitName: String): String {
        return VariantDslInfoBuilder.computeFullNameWithSplits(
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
    override val baseName: String by lazy {
        VariantDslInfoBuilder.computeBaseName(this, variantType)
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
        if (variantType.isTestComponent || variantType.isTestFixturesComponent) {
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

    // use lazy mechanism as this is referenced by other properties, like applicationId or itself
    override val namespace: Provider<String> by lazy {
        when {
            // -------------
            // Special case for test components
            // The namespace is the tested component's testNamespace or else the tested component's
            // namespace + ".test"
            testedVariantImpl != null -> {
                testedVariantImpl.testNamespace?.let { services.provider { it } }
                    ?: testedVariantImpl.namespace.map { "$it.test" }
            }

            // -------------
            // Special case for separate test sub-projects
            // If there is no namespace from the DSL or package attribute in the manifest, we use
            // testApplicationId, if present. This allows the test project to not have a manifest if
            // all is declared in the DSL.
            // TODO(b/170945282, b/172361895) Remove this special case - users should use namespace
            //  DSL instead of testApplicationId DSL for this... currently a warning
            variantType.isSeparateTestProject -> {
                if (dslNamespaceProvider != null) {
                    dslNamespaceProvider
                } else {
                    val testAppIdFromFlavors =
                        productFlavorList.asSequence().map { it.testApplicationId }
                            .firstOrNull { it != null }
                            ?: defaultConfig.testApplicationId

                    dataProvider.manifestData.map {
                        it.packageName
                                ?: testAppIdFromFlavors?.also {
                                    val message =
                                        "Namespace not specified. Please specify a namespace for " +
                                                "the generated R and BuildConfig classes via " +
                                                "android.namespace in the test module's " +
                                                "build.gradle file. Currently, this test module " +
                                                "uses the testApplicationId " +
                                                "($testAppIdFromFlavors) as its namespace, but " +
                                                "version ${Version.VERSION_8_0} of the Android " +
                                                "Gradle Plugin will require that a namespace be " +
                                                "specified explicitly like so:\n\n" +
                                                "android {\n" +
                                                "    namespace '$testAppIdFromFlavors'\n" +
                                                "}\n\n"
                                    services.issueReporter
                                        .reportWarning(IssueReporter.Type.GENERIC, message)
                                }
                                ?: throw RuntimeException(
                                    getMissingPackageNameErrorMessage(dataProvider.manifestLocation)
                                )
                    }
                }
            }

            // -------------
            // All other types of projects, get it from the DSL or read it from the manifest.
            else -> dslOrManifestNamespace
        }
    }

    // The namespace as specified by the user, either via the DSL or the `package` attribute of the
    // source AndroidManifest.xml
    private val dslOrManifestNamespace: Provider<String> by lazy {
        dslNamespaceProvider
            ?: dataProvider.manifestData.map {
                it.packageName
                    ?: throw RuntimeException(
                        getMissingPackageNameErrorMessage(dataProvider.manifestLocation)
                    )
            }
    }

    private fun getMissingPackageNameErrorMessage(manifestLocation: String): String =
        "Package Name not found in $manifestLocation, and namespace not specified. Please " +
                "specify a namespace for the generated R and BuildConfig classes via " +
                "android.namespace in the module's build.gradle file like so:\n\n" +
                "android {\n" +
                "    namespace 'com.example.namespace'\n" +
                "}\n\n"

    override val testNamespace: String? = dslTestNamespace ?: dslNamespaceProvider?.let { "${it.get()}.test" }

    /**
     * Returns the application ID for this variant. This could be coming from the manifest or could
     * be overridden through the product flavors and/or the build type.
     *
     * @return the application ID
     */
    override val applicationId: Property<String> =
        services.newPropertyBackingDeprecatedApi(
            String::class.java,
            initApplicationId(),
            "applicationId"
        )


    private fun initApplicationId(): Provider<String> {
            // -------------
            // Special case for test components and separate test sub-projects
            if (variantType.isForTesting) {
                // get first non null testAppId from flavors/default config
                val testAppIdFromFlavors =
                    productFlavorList.asSequence().map { it.testApplicationId }
                        .firstOrNull { it != null }
                        ?: defaultConfig.testApplicationId

                return if (testAppIdFromFlavors == null) {
                    testedVariantImpl?.applicationId?.map {
                        "$it.test"
                    } ?: namespace
                } else {
                    // needed to make nullability work in kotlinc
                    val finalTestAppIdFromFlavors: String = testAppIdFromFlavors
                    services.provider(Callable { finalTestAppIdFromFlavors })
                }
            }

            // -------------
            // All other project types

            // get first non null appId from flavors/default config
            val appIdFromFlavors =
                productFlavorList.asSequence().map { it.applicationId }.firstOrNull { it != null }
                    ?: defaultConfig.applicationId

            return if (appIdFromFlavors == null) {
                // No appId value set from DSL; use the namespace value from the DSL or manifest.
                // using map will allow us to keep task dependency should the manifest be generated
                // or transformed via a task.
                dslOrManifestNamespace.map { "$it${computeApplicationIdSuffix()}" }
            } else {
                // use value from flavors/defaultConfig
                // needed to make nullability work in kotlinc
                val finalAppIdFromFlavors: String = appIdFromFlavors
                services.provider(
                    Callable { "$finalAppIdFromFlavors${computeApplicationIdSuffix()}" })
            }
    }

    /**
     * Combines all the appId suffixes into a single one.
     *
     * The suffixes are separated by '.' whether their first char is a '.' or not.
     */
    private fun computeApplicationIdSuffix(): String {
        // for the suffix we combine the suffix from all the flavors. However, we're going to
        // want the higher priority one to be last.
        val suffixes = mutableListOf<String>()
        defaultConfig.applicationIdSuffix?.let {
            suffixes.add(it)
        }

        suffixes.addAll(productFlavorList.mapNotNull { it.applicationIdSuffix })

        // then we add the build type after.
        buildTypeObj.applicationIdSuffix?.let {
            suffixes.add(it)
        }
        val nonEmptySuffixes = suffixes.filter { it.isNotEmpty() }
        return if (nonEmptySuffixes.isNotEmpty()) {
            ".${nonEmptySuffixes.joinToString(separator = ".", transform = { it.removePrefix(".") })}"
        } else {
            ""
        }
    }

    override val versionName: Provider<String?>
        get() {
            // This value is meaningless for tests
            if (variantType.isForTesting) {
                val callable: Callable<String?> = Callable { null }
                return services.provider(callable)
            }

            // TODO: figure out whether it's worth it to put all this inside a Provider to make it lazy.
            val injectedVersionName =
                services.projectOptions[StringOption.IDE_VERSION_NAME_OVERRIDE]
            if (injectedVersionName != null) {
                return services.provider(Callable { injectedVersionName })
            }

            // If the version name from the flavors is null, then we read from the manifest and combine
            // with suffixes, unless it's a test at which point we just return.
            // If the name is not-null, we just combine it with suffixes
            val versionNameFromFlavors =
                productFlavorList.asSequence().map { it.versionName }.firstOrNull { it != null }
                    ?: defaultConfig.versionName

            return if (versionNameFromFlavors == null) {
                // rely on manifest value
                // using map will allow us to keep task dependency should the manifest be generated or
                // transformed via a task.
                dataProvider.manifestData.map {
                    if (it.versionName == null) {
                        it.versionName
                    } else {
                        "${it.versionName}${computeVersionNameSuffix()}"
                    }
                }
            } else {
                // use value from flavors
                services.provider(
                    Callable { "$versionNameFromFlavors${computeVersionNameSuffix()}" })
            }
        }

    private fun computeVersionNameSuffix(): String {
        // for the suffix we combine the suffix from all the flavors. However, we're going to
        // want the higher priority one to be last.
        val suffixes = mutableListOf<String>()
        defaultConfig.versionNameSuffix?.let {
            suffixes.add(it)
        }

        suffixes.addAll(productFlavorList.mapNotNull { it.versionNameSuffix })

        // then we add the build type after.
        buildTypeObj.versionNameSuffix?.let {
            suffixes.add(it)
        }

        return if (suffixes.isNotEmpty()) {
            suffixes.joinToString(separator = "")
        } else {
            ""
        }
    }

    override val versionCode: Provider<Int?>
        get() {
            // This value is meaningless for tests
            if (variantType.isForTesting) {
                val callable: Callable<Int?> = Callable { null }
                return services.provider(callable)
            }

            // TODO: figure out whether it's worth it to put all this inside a Provider to make it lazy.
            val injectedVersionCode =
                services.projectOptions.get(IntegerOption.IDE_VERSION_CODE_OVERRIDE)
            if (injectedVersionCode != null) {
                return services.provider(Callable { injectedVersionCode })
            }

            // If the version code from the flavors is null, then we read from the manifest and combine
            // with suffixes, unless it's a test at which point we just return.
            // If the name is not-null, we just combine it with suffixes
            val versionCodeFromFlavors =
                productFlavorList.asSequence().map { it.versionCode }.firstOrNull { it != null }
                    ?: defaultConfig.versionCode

            return if (versionCodeFromFlavors == null) {
                // rely on manifest value
                // using map will allow us to keep task dependency should the manifest be generated or
                // transformed via a task.
                dataProvider.manifestData.map { it.versionCode }
            } else {
                // use value from flavors
                services.provider(Callable { versionCodeFromFlavors })
            }
        }

    override fun getInstrumentationRunner(dexingType: DexingType): Provider<String> {
            if (!variantType.isForTesting) {
                throw RuntimeException("instrumentationRunner is not available to non-test variant")
            }

            // first check whether the DSL has the info
            val fromFlavor =
                productFlavorList.asSequence().map { it.testInstrumentationRunner }
                    .firstOrNull { it != null }
                    ?: defaultConfig.testInstrumentationRunner

            if (fromFlavor != null) {
                val finalFromFlavor: String = fromFlavor
                return services.provider(Callable { finalFromFlavor })
            }

            // else return the value from the Manifest
            return dataProvider.manifestData.map {
                it.instrumentationRunner
                    ?: if (dexingType.isLegacyMultiDexMode()) {
                        MULTIDEX_TEST_RUNNER
                    } else {
                        DEFAULT_TEST_RUNNER
                    }
            }
        }

    /**
     * Returns the instrumentationRunner arguments to use to test this variant, or if the variant is
     * a test, the ones to use to test the tested variant
     */
    override val instrumentationRunnerArguments: Map<String, String>
        get() {
            val variantDslInfo: VariantDslInfoImpl<*> =
                if (variantType.isTestComponent) {
                    testedVariantImpl!!
                } else {
                    this
                }
            return variantDslInfo.mergedFlavor.testInstrumentationRunnerArguments
        }

    override val handleProfiling: Provider<Boolean>
        get() {
            if (!variantType.isForTesting) {
                throw RuntimeException("handleProfiling is not available to non-test variant")
            }

            // first check whether the DSL has the info
            val fromFlavor =
                productFlavorList.asSequence().map { it.testHandleProfiling }
                    .firstOrNull { it != null }
                    ?: defaultConfig.testHandleProfiling

            if (fromFlavor != null) {
                val finalFromFlavor: Boolean = fromFlavor
                return services.provider(Callable { finalFromFlavor })
            }

            // else return the value from the Manifest
            return dataProvider.manifestData.map { it.handleProfiling ?: DEFAULT_HANDLE_PROFILING }
        }

    override val functionalTest: Provider<Boolean>
        get() {
            if (!variantType.isForTesting) {
                throw RuntimeException("functionalTest is not available to non-test variant")
            }

            // first check whether the DSL has the info
            val fromFlavor =
                productFlavorList.asSequence().map { it.testFunctionalTest }
                    .firstOrNull { it != null }
                    ?: defaultConfig.testFunctionalTest

            if (fromFlavor != null) {
                val finalFromFlavor: Boolean = fromFlavor
                return services.provider(Callable { finalFromFlavor })
            }

            // else return the value from the Manifest
            return dataProvider.manifestData.map { it.functionalTest ?: DEFAULT_FUNCTIONAL_TEST }
        }

    override val testLabel: Provider<String?>
        get() {
            if (!variantType.isForTesting) {
                throw RuntimeException("handleProfiling is not available to non-test variant")
            }

            // there is actually no DSL value for this.
            return dataProvider.manifestData.map { it.testLabel }
        }

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
            // default to 1 for minSdkVersion.
            val minSdkVersion =
                mergedFlavor.minSdkVersion ?: DefaultApiVersion.create(Integer.valueOf(1))

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
            return mergedFlavor.targetSdkVersion
                // default to -1 if not in build.gradle file.
                ?: DefaultApiVersion.create(Integer.valueOf(-1))
        }

    override val renderscriptTarget: Int = mergedFlavor.renderscriptTargetApi ?: -1

    override val isWearAppUnbundled: Boolean?
        get() = mergedFlavor.wearAppUnbundled

    @Suppress("DEPRECATION")
    override val missingDimensionStrategies: ImmutableMap<String, AbstractProductFlavor.DimensionRequest>
        get() = ImmutableMap.copyOf(mergedFlavor.missingDimensionStrategies)

    override val resourceConfigurations: ImmutableSet<String>
        get() = ImmutableSet.copyOf(mergedFlavor.resourceConfigurations)

    override val vectorDrawables: VectorDrawablesOptions
        get() = mergedFlavor.vectorDrawables

    override fun getBuildConfigFields(): Map<String, BuildConfigField<out java.io.Serializable>> {
        val buildConfigFieldsMap =
            mutableMapOf<String, BuildConfigField<out java.io.Serializable>>()

        fun addToListIfNotAlreadyPresent(classField: ClassField, comment: String) {
            if (!buildConfigFieldsMap.containsKey(classField.name)) {
                buildConfigFieldsMap[classField.name] =
                        BuildConfigField(classField.type , classField.value, comment)
            }
        }

        buildTypeObj.buildConfigFields.values.forEach { classField ->
            addToListIfNotAlreadyPresent(classField, "Field from build type: ${buildTypeObj.name}")
        }

        for (flavor in productFlavorList) {
            flavor.buildConfigFields.values.forEach { classField ->
                addToListIfNotAlreadyPresent(
                    classField,
                    "Field from product flavor: ${flavor.name}"
                )
            }
        }
        defaultConfig.buildConfigFields.values.forEach { classField ->
            addToListIfNotAlreadyPresent(classField, "Field from default config.")
        }
        return buildConfigFieldsMap
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
    override fun getResValues(): Map<ResValue.Key, ResValue> {
        val resValueFields = mutableMapOf<ResValue.Key, ResValue>()

        fun addToListIfNotAlreadyPresent(classField: ClassField, comment: String) {
            val key = ResValueKeyImpl(classField.type, classField.name)
            if (!resValueFields.containsKey(key)) {
                resValueFields[key] = ResValue(
                    value = classField.value,
                    comment = comment
                )
            }
        }

        buildTypeObj.resValues.values.forEach { classField ->
            addToListIfNotAlreadyPresent(classField, "Value from build type: ${buildTypeObj.name}")
        }

        productFlavorList.forEach { flavor ->
            flavor.resValues.values.forEach { classField ->
                addToListIfNotAlreadyPresent(
                    classField,
                    "Value from product flavor: ${flavor.name}"
                )
            }
        }

        defaultConfig.resValues.values.forEach { classField ->
            addToListIfNotAlreadyPresent(classField, "Value from default config.")
        }

        return resValueFields
    }

    override val signingConfig: SigningConfig?
        get() {
            if (variantType.isDynamicFeature) {
                return null
            }
            // cast builder.SigningConfig to dsl.SigningConfig because MergedFlavor merges
            // dsl.SigningConfig of ProductFlavor objects
            val dslSigningConfig: SigningConfig? =
                (buildTypeObj.signingConfig ?: mergedFlavor.signingConfig) as SigningConfig?
            signingConfigOverride?.let {
                // use enableV1 and enableV2 from the DSL if the override values are null
                if (it.enableV1Signing == null) {
                    it.enableV1Signing = dslSigningConfig?.enableV1Signing
                }
                if (it.enableV2Signing == null) {
                    it.enableV2Signing = dslSigningConfig?.enableV2Signing
                }
                // use enableV3 and enableV4 from the DSL because they're not injectable
                it.enableV3Signing = dslSigningConfig?.enableV3Signing
                it.enableV4Signing = dslSigningConfig?.enableV4Signing
                return it
            }
            return dslSigningConfig
        }

    override val isSigningReady: Boolean
        get() {
            val signingConfig = signingConfig
            return signingConfig != null && signingConfig.isSigningReady
        }

    override val isTestCoverageEnabled: Boolean
        get() = buildTypeObj.isTestCoverageEnabled // so far, blindly override the build type placeholders

    /**
     * Returns the merged manifest placeholders. All product flavors are merged first, then build
     * type specific placeholders are added and potentially overrides product flavors values.
     *
     * @return the merged manifest placeholders for a build variant.
     */
    override val manifestPlaceholders: Map<String, String> by lazy {
        val mergedFlavorsPlaceholders: MutableMap<String, String> = mutableMapOf()
        mergedFlavor.manifestPlaceholders.forEach { (key, value) ->
            mergedFlavorsPlaceholders[key] = value.toString()
        }
        // so far, blindly override the build type placeholders
        buildTypeObj.manifestPlaceholders.forEach { (key, value) ->
            mergedFlavorsPlaceholders[key] = value.toString()
        }
        mergedFlavorsPlaceholders
    }

    // Only require specific multidex opt-in for legacy multidex.
    override val isMultiDexEnabled: Boolean?
        get() {
            // Only require specific multidex opt-in for legacy multidex.
            return buildTypeObj.multiDexEnabled
                ?: mergedFlavor.multiDexEnabled
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

    // dynamic features can always be build in native multidex mode
    override val dexingType: DexingType?
        get() = if (variantType.isDynamicFeature) {
            if (buildTypeObj.multiDexEnabled != null ||
                mergedFlavor.multiDexEnabled != null
            ) {
                dslServices.issueReporter
                    .reportWarning(
                        IssueReporter.Type.GENERIC,
                        "Native multidex is always used for dynamic features. Please " +
                                "remove 'multiDexEnabled true|false' from your " +
                                "build.gradle file."
                    )
            }
            // dynamic features can always be build in native multidex mode
            DexingType.NATIVE_MULTIDEX
        } else null

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
        get() = variantType.isAar // Consider runtime API passed from the IDE only if multi-dex is enabled and the app is debuggable.

    /**
     * Returns if the property passed by the IDE is set, the minimum SDK version or
     * null if not.
     */
    override val minSdkVersionFromIDE: Int? =
        dslServices.projectOptions.get(IntegerOption.IDE_TARGET_DEVICE_API)

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
        computeMergedOptions(
            mergedAarMetadata,
            { aarMetadata },
            { aarMetadata }
        )
    }

    override val ndkConfig: MergedNdkConfig
        get() = mergedNdkConfig

    override val externalNativeBuildOptions: CoreExternalNativeBuildOptions
        get() = mergedExternalNativeBuildOptions

    override val aarMetadata: MergedAarMetadata
        get() = mergedAarMetadata

    /**
     * Returns the ABI filters associated with the artifact, or empty set if there are no filters.
     *
     * If the list contains values, then the artifact only contains these ABIs and excludes
     * others.
     */
    override val supportedAbis: Set<String>
        get() = if (variantType.isDynamicFeature) setOf() else mergedNdkConfig.abiFilters

    override fun getProguardFiles(into: ListProperty<RegularFile>) {
        val result: MutableList<File> = ArrayList(mergedProguardFiles(ProguardFileType.EXPLICIT))
        if (result.isEmpty()) {
            result.addAll(_postProcessingOptions.getDefaultProguardFiles())
        }

        val projectDir = services.projectInfo.getProject().layout.projectDirectory
        result.forEach { file ->
            into.add(projectDir.file(file.absolutePath))
        }
    }

    override fun gatherProguardFiles(type: ProguardFileType, into: ListProperty<RegularFile>) {
        val projectDir = services.projectInfo.getProject().layout.projectDirectory
        mergedProguardFiles(type).forEach {
            into.add(projectDir.file(it.absolutePath))
        }
    }

    private fun mergedProguardFiles(type: ProguardFileType): Collection<File> {
        val result: MutableList<File> = ArrayList(defaultConfig.getProguardFiles(type))
        for (flavor in productFlavorList) {
            result.addAll(flavor.getProguardFiles(type))
        }
        result.addAll(_postProcessingOptions.getProguardFiles(type))
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
    private fun <CoreOptionsT, MergedOptionsT : MergedOptions<CoreOptionsT>> computeMergedOptions(
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

    var _postProcessingOptions: PostProcessingOptions =
            if (buildTypeObj.postProcessingConfiguration == PostProcessingConfiguration.POSTPROCESSING_BLOCK) {
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
                            buildDirectory
                        )
                    )

                override fun getPostprocessingFeatures(): PostprocessingFeatures? = null

                override fun codeShrinkerEnabled() = buildTypeObj.isMinifyEnabled

                override fun resourcesShrinkingEnabled(): Boolean = buildTypeObj.isShrinkResources
            }

    override fun getPostProcessingOptions(): PostProcessingOptions = _postProcessingOptions

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

    override val isRenderscriptDebuggable: Boolean
        get() = buildTypeObj.isRenderscriptDebuggable

    override val renderscriptOptimLevel: Int
        get() = buildTypeObj.renderscriptOptimLevel

    override val isJniDebuggable: Boolean
        get() = buildTypeObj.isJniDebuggable

    override val publishInfo: VariantPublishingInfo?
        get() = publishingInfo

    companion object {

        const val DEFAULT_TEST_RUNNER = "android.test.InstrumentationTestRunner"
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

private fun BaseConfig.getProguardFiles(type: ProguardFileType): Collection<File> = when (type) {
    ProguardFileType.EXPLICIT -> this.proguardFiles
    ProguardFileType.TEST -> this.testProguardFiles
    ProguardFileType.CONSUMER -> this.consumerProguardFiles
}
