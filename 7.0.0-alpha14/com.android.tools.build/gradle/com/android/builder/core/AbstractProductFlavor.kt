/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.builder.core

import com.android.builder.internal.BaseConfigImpl
import com.android.builder.model.ApiVersion
import com.android.builder.model.BaseConfig
import com.android.builder.model.ProductFlavor
import com.android.builder.model.SigningConfig
import com.google.common.base.MoreObjects
import com.google.common.base.Strings
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Maps
import com.google.common.collect.Sets

/**
 * Builder-level implementation of ProductFlavor.
 *
 *
 * This is also used to describe the default configuration of all builds, even those that do not
 * contain any flavors.
 *
 */
@Deprecated("This is deprecated, use DSL objects directly.")
abstract class AbstractProductFlavor(
    private val name: String
) : BaseConfigImpl(), ProductFlavor {

    override fun getName(): String = name

    override var dimension: String? = null

    // For kotlin script source compatibility.
    @Deprecated("Replaced with the dimension property", replaceWith = ReplaceWith("dimension(dimension)"))
    fun setDimension(dimension: String?): Void? {
        this.dimension = dimension
        return null
    }

    open fun dimension(dimension: String?) {
        this.dimension = dimension
    }

    override var applicationId: String? = null

    open fun setApplicationId(applicationId: String?): ProductFlavor {
        this.applicationId = applicationId
        return this
    }

    open fun applicationId(applicationId: String?) {
        this.applicationId = applicationId
    }

    /**
     * Implemented as a separate property to make versionCode read-only in MergedFlavor,
     * but still allow [mergeWithHigherPriorityFlavor] to set it.
     */
    private var _versionCode: Int? = null

    override var versionCode: Int?
        get() = _versionCode
        set(value) { _versionCode = value }

    open fun setVersionCode(versionCode: Int?): ProductFlavor {
        this.versionCode = versionCode
        return this
    }

    open fun versionCode(versionCode: Int?) {
        this.versionCode = versionCode
    }

    /**
     * Implemented as a separate property to make versionName read-only in MergedFlavor,
     * but still allow [mergeWithHigherPriorityFlavor] to set it.
     */
    private var _versionName: String? = null

    override var versionName: String?
        get() = _versionName
        set(value) { _versionName = value }

    open fun setVersionName(versionName: String?): ProductFlavor {
        this.versionName = versionName
        return this
    }

    open fun versionName(versionName: String?) {
        this.versionName = versionName
    }

    fun setMinSdkVersion(minSdkVersion: ApiVersion?): ProductFlavor {
        this.minSdkVersion = minSdkVersion
        return this
    }

    /** Min SDK version. */
    override var minSdkVersion: ApiVersion? = null

    /** Target SDK version. */
    override var targetSdkVersion: ApiVersion? = null

    /** Sets the targetSdkVersion to the given value.  */
    fun setTargetSdkVersion(targetSdkVersion: ApiVersion?): ProductFlavor {
        this.targetSdkVersion = targetSdkVersion
        return this
    }

    override var maxSdkVersion: Int? = null

    fun setMaxSdkVersion(maxSdkVersion: Int?): ProductFlavor {
        this.maxSdkVersion = maxSdkVersion
        return this
    }

    override var renderscriptTargetApi: Int? = null

    override var renderscriptSupportModeEnabled: Boolean? = null

    fun setRenderscriptSupportModeEnabled(renderscriptSupportMode: Boolean?): ProductFlavor {
        renderscriptSupportModeBlasEnabled = renderscriptSupportMode
        return this
    }

    open fun renderscriptSupportModeEnabled(renderscriptSupportModeEnabled: Boolean?) {
        this.renderscriptSupportModeEnabled = renderscriptSupportModeEnabled
    }

    override var renderscriptSupportModeBlasEnabled: Boolean? = null

    fun setRenderscriptSupportModeBlasEnabled(renderscriptSupportModeBlas: Boolean?): ProductFlavor {
        this.renderscriptSupportModeBlasEnabled = renderscriptSupportModeBlas
        return this
    }

    open fun renderscriptSupportModeBlasEnabled(renderscriptSupportModeBlas: Boolean?) {
        this.renderscriptSupportModeBlasEnabled = renderscriptSupportModeBlas
    }

    override var renderscriptNdkModeEnabled: Boolean? = null

    fun setRenderscriptNdkModeEnabled(renderscriptNdkMode: Boolean?): ProductFlavor {
        this.renderscriptNdkModeEnabled = renderscriptNdkMode
        return this
    }

    open fun renderscriptNdkModeEnabled(renderscriptNdkModeEnabled: Boolean?) {
        this.renderscriptNdkModeEnabled = renderscriptNdkModeEnabled
    }

    override var testApplicationId: String? = null

    fun setTestApplicationId(applicationId: String?): ProductFlavor {
        testApplicationId = applicationId
        return this
    }

    open fun testApplicationId(applicationId: String?) {
        testApplicationId = applicationId
    }

    override var testInstrumentationRunner: String? = null

    fun setTestInstrumentationRunner(testInstrumentationRunner: String?): ProductFlavor {
        this.testInstrumentationRunner = testInstrumentationRunner
        return this
    }

    open fun testInstrumentationRunner(testInstrumentationRunner: String?) {
        this.testInstrumentationRunner = testInstrumentationRunner
    }

    override val testInstrumentationRunnerArguments: MutableMap<String, String> = Maps.newHashMap()

    fun setTestInstrumentationRunnerArguments(
        testInstrumentationRunnerArguments: MutableMap<String, String>
    ): ProductFlavor {
        this.testInstrumentationRunnerArguments.clear()
        this.testInstrumentationRunnerArguments.putAll(testInstrumentationRunnerArguments)
        return this
    }

    override var testHandleProfiling: Boolean? = null

    fun setTestHandleProfiling(handleProfiling: Boolean): ProductFlavor {
        testHandleProfiling = handleProfiling
        return this
    }

    override var testFunctionalTest: Boolean? = null

    fun setTestFunctionalTest(functionalTest: Boolean): ProductFlavor {
        testFunctionalTest = functionalTest
        return this
    }

    private var _signingConfig: SigningConfig? = null

    /** Signing config used by this product flavor. e.g.: `signingConfig = signingConfigs.myConfig` */
    override val signingConfig: SigningConfig?
        get() = _signingConfig

    fun setSigningConfig(signingConfig: SigningConfig?): ProductFlavor {
        _signingConfig = signingConfig
        return this
    }

    /**
     * Options to configure the build-time support for `vector` drawables.
     */
    abstract override val vectorDrawables: DefaultVectorDrawablesOptions

    override var wearAppUnbundled: Boolean? = null

    override val resourceConfigurations: MutableSet<String> = Sets.newHashSet()

    /**
     * Adds a res config filter (for instance 'hdpi')
     */
    fun addResourceConfiguration(configuration: String) {
        resourceConfigurations.add(configuration)
    }

    /**
     * Adds a res config filter (for instance 'hdpi')
     */
    fun addResourceConfigurations(vararg configurations: String) {
        resourceConfigurations.addAll(listOf(*configurations))
    }

    /**
     * Adds a res config filter (for instance 'hdpi')
     */
    fun addResourceConfigurations(configurations: Collection<String>) {
        resourceConfigurations.addAll(configurations)
    }

    /** Class representing a request with fallbacks.  */
    class DimensionRequest(val requested: String, val fallbacks: ImmutableList<String>) {
        fun getFallbacks(): List<String> = fallbacks
    }

    /** map of dimension -> request  */
    private var missingDimensionSelections: MutableMap<String, DimensionRequest>? = null

    /**
     * Specifies a flavor that the plugin should try to use from a given dimension in a dependency.
     *
     * Android plugin 3.0.0 and higher try to match each variant of your module with the same one
     * from its dependencies. For example, consider if both your app and its dependencies include a
     * "tier" [flavor dimension](/studio/build/build-variants.html#flavor-dimensions),
     * with flavors "free" and "paid". When you build a "freeDebug" version of your app, the plugin
     * tries to match it with "freeDebug" versions of the local library modules the app depends on.
     *
     * However, there may be situations in which **a library dependency includes a flavor
     * dimension that your app does not**. For example, consider if a library dependency includes
     * flavors for a "minApi" dimension, but your app includes flavors for only the "tier"
     * dimension. So, when you want to build the "freeDebug" version of your app, the plugin doesn't
     * know whether to use the "minApi23Debug" or "minApi18Debug" version of the dependency, and
     * you'll see an error message similar to the following:
     *
     * ```
     * Error:Failed to resolve: Could not resolve project :mylibrary.
     * Required by:
     * project :app
     * ```
     *
     * In this type of situation, use `missingDimensionStrategy` in the
     * [`defaultConfig`](com.android.build.gradle.internal.dsl.DefaultConfig.html)
     * block to specify the default flavor the plugin should select from each missing
     * dimension, as shown in the sample below. You can also override your selection in the
     * [`productFlavors`](com.android.build.gradle.internal.dsl.ProductFlavor.html)
     * block, so each flavor can specify a different matching strategy for a missing dimension.
     * (Tip: you can also use this property if you simply want to change the matching strategy for a
     * dimension that exists in both the app and its dependencies.)
     *
     * ```
     * // In the app's build.gradle file.
     * android {
     *     defaultConfig {
     *         // Specifies a flavor that the plugin should try to use from
     *         // a given dimension. The following tells the plugin that, when encountering
     *         // a dependency that includes a "minApi" dimension, it should select the
     *         // "minApi18" flavor.
     *         missingDimensionStrategy 'minApi', 'minApi18'
     *         // You should specify a missingDimensionStrategy property for each
     *         // dimension that exists in a local dependency but not in your app.
     *         missingDimensionStrategy 'abi', 'x86'
     *     }
     *     flavorDimensions 'tier'
     *     productFlavors {
     *         free {
     *             dimension 'tier'
     *             // You can override the default selection at the product flavor
     *             // level by configuring another missingDimensionStrategy property
     *             // for the "minApi" dimension.
     *             missingDimensionStrategy 'minApi', 'minApi23'
     *         }
     *         paid { }
     *     }
     * }
     * ```
     */
    fun missingDimensionStrategy(dimension: String, requestedValue: String) {
        missingDimensionStrategy(dimension, ImmutableList.of(requestedValue))
    }

    /**
     * Specifies a sorted list of flavors that the plugin should try to use from a given dimension
     * in a dependency.
     *
     *
     * Android plugin 3.0.0 and higher try to match each variant of your module with the same one
     * from its dependencies. For example, consider if both your app and its dependencies include a
     * "tier" [flavor dimension](/studio/build/build-variants.html#flavor-dimensions),
     * with flavors "free" and "paid". When you build a "freeDebug" version of your app, the plugin
     * tries to match it with "freeDebug" versions of the local library modules the app depends on.
     *
     *
     * However, there may be situations in which **a library dependency includes a flavor
     * dimension that your app does not**. For example, consider if a library dependency includes
     * flavors for a "minApi" dimension, but your app includes flavors for only the "tier"
     * dimension. So, when you want to build the "freeDebug" version of your app, the plugin doesn't
     * know whether to use the "minApi23Debug" or "minApi18Debug" version of the dependency, and
     * you'll see an error message similar to the following:
     *
     * ```
     * Error:Failed to resolve: Could not resolve project :mylibrary.
     * Required by:
     * project :app
     * ```
     *
     *
     * In this type of situation, use `missingDimensionStrategy` in the
     * [`defaultConfig`](com.android.build.gradle.internal.dsl.DefaultConfig.html)
     * block to specify the default flavor the plugin should select from each missing
     * dimension, as shown in the sample below. You can also override your selection in the
     * [`productFlavors`](com.android.build.gradle.internal.dsl.ProductFlavor.html)
     * block, so each flavor can specify a different matching strategy for a missing dimension.
     * (Tip: you can also use this property if you simply want to change the matching strategy for a
     * dimension that exists in both the app and its dependencies.)
     *
     * ```
     * // In the app's build.gradle file.
     * android {
     *     defaultConfig {
     *         // Specifies a flavor that the plugin should try to use from
     *         // a given dimension. The following tells the plugin that, when encountering
     *         // a dependency that includes a "minApi" dimension, it should select the
     *         // "minApi18" flavor.
     *         missingDimensionStrategy 'minApi', 'minApi18'
     *         // You should specify a missingDimensionStrategy property for each
     *         // dimension that exists in a local dependency but not in your app.
     *         missingDimensionStrategy 'abi', 'x86'
     *     }
     *     flavorDimensions 'tier'
     *     productFlavors {
     *         free {
     *             dimension 'tier'
     *             // You can override the default selection at the product flavor
     *             // level by configuring another missingDimensionStrategy property
     *             // for the "minApi" dimension.
     *             missingDimensionStrategy 'minApi', 'minApi23'
     *         }
     *         paid { }
     *     }
     * }
     * ```
     */
    fun missingDimensionStrategy(dimension: String, vararg requestedValues: String) {
        missingDimensionStrategy(dimension, ImmutableList.copyOf(requestedValues))
    }

    /**
     * Specifies a sorted list of flavors that the plugin should try to use from a given dimension
     * in a dependency.
     *
     *
     * Android plugin 3.0.0 and higher try to match each variant of your module with the same one
     * from its dependencies. For example, consider if both your app and its dependencies include a
     * "tier" [flavor dimension](/studio/build/build-variants.html#flavor-dimensions),
     * with flavors "free" and "paid". When you build a "freeDebug" version of your app, the plugin
     * tries to match it with "freeDebug" versions of the local library modules the app depends on.
     *
     *
     * However, there may be situations in which **a library dependency includes a flavor
     * dimension that your app does not**. For example, consider if a library dependency includes
     * flavors for a "minApi" dimension, but your app includes flavors for only the "tier"
     * dimension. So, when you want to build the "freeDebug" version of your app, the plugin doesn't
     * know whether to use the "minApi23Debug" or "minApi18Debug" version of the dependency, and
     * you'll see an error message similar to the following:
     *
     * ```
     * Error:Failed to resolve: Could not resolve project :mylibrary.
     * Required by:
     * project :app
     * ```
     *
     * In this type of situation, use `missingDimensionStrategy` in the
     * [`defaultConfig`](com.android.build.gradle.internal.dsl.DefaultConfig.html)
     * block to specify the default flavor the plugin should select from each missing
     * dimension, as shown in the sample below. You can also override your selection in the
     * [`productFlavors`](com.android.build.gradle.internal.dsl.ProductFlavor.html)
     * block, so each flavor can specify a different matching strategy for a missing dimension.
     * (Tip: you can also use this property if you simply want to change the matching strategy for a
     * dimension that exists in both the app and its dependencies.)
     *
     * ```
     * // In the app's build.gradle file.
     * android {
     *     defaultConfig {
     *         // Specifies a flavor that the plugin should try to use from
     *         // a given dimension. The following tells the plugin that, when encountering
     *         // a dependency that includes a "minApi" dimension, it should select the
     *         // "minApi18" flavor.
     *         missingDimensionStrategy 'minApi', 'minApi18'
     *         // You should specify a missingDimensionStrategy property for each
     *         // dimension that exists in a local dependency but not in your app.
     *         missingDimensionStrategy 'abi', 'x86'
     *     }
     *     flavorDimensions 'tier'
     *     productFlavors {
     *         free {
     *             dimension 'tier'
     *             // You can override the default selection at the product flavor
     *             // level by configuring another missingDimensionStrategy property
     *             // for the "minApi" dimension.
     *             missingDimensionStrategy 'minApi', 'minApi23'
     *         }
     *         paid { }
     *     }
     * }
     * ```
     */
    fun missingDimensionStrategy(dimension: String, requestedValues: List<String>) {
        if (requestedValues.isEmpty()) {
            throw RuntimeException("List of requested values cannot be empty")
        }
        val selection = computeRequestedAndFallBacks(requestedValues)
        if (missingDimensionSelections == null) {
            missingDimensionSelections = Maps.newHashMap()
        }
        missingDimensionSelections!![dimension] = selection
    }

    /**
     * Computes the requested value and the fallback list from the list of values provided in the
     * DSL
     *
     * @param requestedValues the values provided in the DSL
     * @return a DimensionRequest with the main requested value and the fallbacks.
     */
    protected open fun computeRequestedAndFallBacks(requestedValues: List<String>): DimensionRequest { // default implementation is that the fallback's first item is the requested item.
        return DimensionRequest(
                requestedValues[0],
                ImmutableList.copyOf(requestedValues.subList(1, requestedValues.size)))
    }

    val missingDimensionStrategies: Map<String, DimensionRequest>
        get() = missingDimensionSelections ?: ImmutableMap.of()

    /**
     * Merges a higher-priority flavor (overlay) on top of this one.
     *
     *
     * The behavior is that if a value is present in the overlay, then it is used, otherwise we
     * use the existing value.
     *
     * @param overlay the higher-priority flavor to apply to this flavor
     */
    protected fun mergeWithHigherPriorityFlavor(overlay: ProductFlavor) {
        minSdkVersion = chooseNotNull(overlay.minSdkVersion, minSdkVersion)
        targetSdkVersion = chooseNotNull(overlay.targetSdkVersion, targetSdkVersion)
        maxSdkVersion = chooseNotNull(overlay.maxSdkVersion, maxSdkVersion)
        renderscriptTargetApi = chooseNotNull(overlay.renderscriptTargetApi, renderscriptTargetApi)
        renderscriptSupportModeEnabled = chooseNotNull(
                overlay.renderscriptSupportModeEnabled,
                renderscriptSupportModeEnabled)
        renderscriptSupportModeBlasEnabled = chooseNotNull(
                overlay.renderscriptSupportModeBlasEnabled,
                renderscriptSupportModeBlasEnabled)
        renderscriptNdkModeEnabled = chooseNotNull(overlay.renderscriptNdkModeEnabled, renderscriptNdkModeEnabled)
        _versionCode = chooseNotNull(overlay.versionCode, versionCode)
        _versionName = chooseNotNull(overlay.versionName, versionName)
        versionNameSuffix = mergeVersionNameSuffix(overlay.versionNameSuffix, versionNameSuffix)
        applicationIdSuffix = mergeApplicationIdSuffix(
                overlay.applicationIdSuffix, applicationIdSuffix)
        testApplicationId = chooseNotNull(overlay.testApplicationId,
            testApplicationId
        )
        testInstrumentationRunner = chooseNotNull(overlay.testInstrumentationRunner,
            testInstrumentationRunner
        )
        testInstrumentationRunnerArguments.putAll(overlay.testInstrumentationRunnerArguments)
        testHandleProfiling = chooseNotNull(overlay.testHandleProfiling,
            testHandleProfiling
        )
        testFunctionalTest = chooseNotNull(overlay.testFunctionalTest, testFunctionalTest)
        // should this be a copy instead?
        _signingConfig = chooseNotNull(overlay.signingConfig, signingConfig)
        wearAppUnbundled = chooseNotNull(overlay.wearAppUnbundled,
            wearAppUnbundled
        )
        addResourceConfigurations(overlay.resourceConfigurations)
        addManifestPlaceholders(overlay.manifestPlaceholders)
        addResValues(overlay.resValues)
        addBuildConfigFields(overlay.buildConfigFields)
        multiDexEnabled = chooseNotNull(overlay.multiDexEnabled, multiDexEnabled)
        multiDexKeepFile = chooseNotNull(overlay.multiDexKeepFile, multiDexKeepFile)
        multiDexKeepProguard = chooseNotNull(overlay.multiDexKeepProguard, multiDexKeepProguard)
        vectorDrawables
                .setGeneratedDensities(
                        chooseNotNull(
                                overlay.vectorDrawables.generatedDensities,
                                vectorDrawables.generatedDensities))
        vectorDrawables.useSupportLibrary = chooseNotNull(
                overlay.vectorDrawables.useSupportLibrary,
                vectorDrawables.useSupportLibrary)
        if (overlay is AbstractProductFlavor) {
            if (missingDimensionSelections == null) {
                missingDimensionSelections = Maps.newHashMap()
            }
            missingDimensionSelections!!.putAll(
                    overlay.missingDimensionStrategies)
        }
        // no need to merge missingDimensionStrategies, it's not queried from the merged flavor.
// TODO this should all be clean up with the new variant DSL/API in 3.1
    }

    protected fun cloneFrom(flavor: ProductFlavor) { // nothing to do
    }

    override fun _initWith(that: BaseConfig) {
        super._initWith(that)
        if (that is ProductFlavor) {
            val thatProductFlavor = that
            dimension = thatProductFlavor.dimension
            minSdkVersion = thatProductFlavor.minSdkVersion
            targetSdkVersion = thatProductFlavor.targetSdkVersion
            maxSdkVersion = thatProductFlavor.maxSdkVersion
            renderscriptTargetApi = thatProductFlavor.renderscriptTargetApi
            renderscriptSupportModeEnabled = thatProductFlavor.renderscriptSupportModeEnabled
            renderscriptSupportModeBlasEnabled = thatProductFlavor.renderscriptSupportModeBlasEnabled
            renderscriptNdkModeEnabled = thatProductFlavor.renderscriptNdkModeEnabled
            _versionCode = thatProductFlavor.versionCode
            _versionName = thatProductFlavor.versionName
            versionNameSuffix = thatProductFlavor.versionNameSuffix
            testApplicationId = thatProductFlavor.testApplicationId
            testInstrumentationRunner = thatProductFlavor.testInstrumentationRunner
            testInstrumentationRunnerArguments.clear()
            testInstrumentationRunnerArguments.putAll(thatProductFlavor.testInstrumentationRunnerArguments)
            testHandleProfiling = thatProductFlavor.testHandleProfiling
            testFunctionalTest = thatProductFlavor.testFunctionalTest
            // should this be a copy instead?
            _signingConfig = thatProductFlavor.signingConfig
            wearAppUnbundled = thatProductFlavor.wearAppUnbundled
            addResourceConfigurations(thatProductFlavor.resourceConfigurations)
            addManifestPlaceholders(thatProductFlavor.manifestPlaceholders)
            addResValues(thatProductFlavor.resValues)
            addBuildConfigFields(thatProductFlavor.buildConfigFields)
            multiDexEnabled = thatProductFlavor.multiDexEnabled
            multiDexKeepFile = thatProductFlavor.multiDexKeepFile
            multiDexKeepProguard = thatProductFlavor.multiDexKeepProguard
        }
        if (that is AbstractProductFlavor) {
            // the objects inside the map are immutable, so it's fine to keep them.
            missingDimensionSelections = Maps.newHashMap(that.missingDimensionStrategies)
        }
    }

    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("dimension", dimension)
                .add("minSdkVersion", minSdkVersion)
                .add("targetSdkVersion", targetSdkVersion)
                .add("renderscriptTargetApi", renderscriptTargetApi)
                .add("renderscriptSupportModeEnabled", renderscriptSupportModeEnabled)
                .add("renderscriptSupportModeBlasEnabled", renderscriptSupportModeBlasEnabled)
                .add("renderscriptNdkModeEnabled", renderscriptNdkModeEnabled)
                .add("versionCode", versionCode)
                .add("versionName", versionName)
                .add("applicationId", applicationId)
                .add("testApplicationId", testApplicationId)
                .add("testInstrumentationRunner",
                    testInstrumentationRunner
                )
                .add("testInstrumentationRunnerArguments",
                    testInstrumentationRunnerArguments
                )
                .add("testHandleProfiling", testHandleProfiling)
                .add("testFunctionalTest", testFunctionalTest)
                .add("signingConfig", signingConfig)
                .add("resConfig", resourceConfigurations)
                .add("buildConfigFields", buildConfigFields)
                .add("resValues", resValues)
                .add("proguardFiles", proguardFiles)
                .add("consumerProguardFiles", consumerProguardFiles)
                .add("manifestPlaceholders", manifestPlaceholders)
                .add("wearAppUnbundled", wearAppUnbundled)
                .toString()
    }

    companion object {
        private const val serialVersionUID = 1L
        protected fun <T> chooseNotNull(overlay: T?, base: T): T {
            return overlay ?: base
        }

        @JvmStatic
        fun mergeApplicationIdSuffix(overlay: String?, base: String?): String {
            return Strings.nullToEmpty(joinWithSeparator(overlay, base, '.'))
        }

        @JvmStatic
        fun mergeVersionNameSuffix(overlay: String?, base: String?): String {
            return Strings.nullToEmpty(joinWithSeparator(overlay, base, null))
        }

        private fun joinWithSeparator(
            overlay: String?,
            base: String?,
            separator: Char?
        ): String? {
            return if (!Strings.isNullOrEmpty(overlay)) {
                val baseSuffix = chooseNotNull<String?>(base, "")
                if (separator == null || overlay!![0] == separator) {
                    baseSuffix + overlay
                } else {
                    baseSuffix + separator + overlay
                }
            } else {
                base
            }
        }
    }
}
