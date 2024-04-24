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

@file:Suppress("DEPRECATION")

package com.android.build.api.variant

/**
 * Model for variant components that only contains configuration-time properties that impacts the
 * build flow.
 *
 * Variant components are the main output of the plugin (e.g. APKs, AARs). They contain references
 * to optional secondary components (tests, fixtures). Their presence can be controlled in select
 * subtypes of [VariantBuilder].
 *
 * It is the object passed to the [AndroidComponentsExtension.beforeVariants] method, like this:
 *
 * ```kotlin
 * androidComponents {
 *   beforeVariants(selector().all()) { variant: VariantBuilder ->
 *   }
 * }
 * ```
 *
 * Note that depending on the actual implementation of [AndroidComponentsExtension], the object
 * received may be a subtype. For instance [ApplicationAndroidComponentsExtension.beforeVariants]
 * will pass [ApplicationVariantBuilder] to the lambda.
 *
 * See [here](https://developer.android.com/build/extend-agp#variant-api-artifacts-tasks) for
 * more information
 *
 */
interface VariantBuilder: ComponentBuilder {

    /**
     * Sets the minimum supported SDK Version for this variant.
     * Setting this it will override previous calls of [minSdk] and [minSdkPreview] setters. Only
     * one of [minSdk] and [minSdkPreview] should be set.
     *
     * It is not safe to read this value. Use [Variant.minSdk] instead.
     *
     * @return the minimum supported SDK Version or null if [minSdkPreview] was used to set it.
     */
    var minSdk: Int?

    /**
     * Sets the minimum supported SDK Version for this variant as a Preview codename.
     * Setting this it will override previous calls of [minSdk] and [minSdkPreview] setters. Only
     * one of [minSdk] and [minSdkPreview] should be set.
     *
     * It is not safe to read this value. Use [Variant.minSdk] instead.
     *
     * @return the minimum supported SDK Version or null if [minSdk] was used to set it.
     */
    var minSdkPreview: String?

    /**
     * Sets the maximum supported SDK Version for this variant.
     *
     * It is not safe to read this value. Use [Variant.maxSdk] instead.
     */
    var maxSdk: Int?

    /**
     * Sets the target SDK Version for this variant as a Preview codename.
     * Setting this it will override previous calls of [targetSdk] and [targetSdkPreview] setters.
     * Only one of [targetSdk] and [targetSdkPreview] should be set.
     *
     * targetSdk is now managed by [GeneratesApkBuilder] instead of [VariantBuilder].
     *
     * @return the target SDK Version or null if [targetSdkPreview] was used to set it.
     */
    @Deprecated(
        "Will be removed in v9.0",
        replaceWith = ReplaceWith("GeneratesApkBuilder.targetSdk")
    )
    var targetSdk: Int?

    /**
     * Sets the target SDK Version for this variant as a Preview codename.
     * Setting this it will override previous calls of [targetSdk] and [targetSdkPreview] setters.
     * Only one of [targetSdk] and [targetSdkPreview] should be set.
     *
     * targetSdkPreview is now managed by [GeneratesApkBuilder] instead of [VariantBuilder].
     *
     * @return the target supported SDK Version or null if [targetSdkPreview] was used to set it.
     */
    @Deprecated(
        "Will be removed in v9.0",
        replaceWith = ReplaceWith("GeneratesApkBuilder.targetSdkPreview")
    )
    var targetSdkPreview: String?

    /**
     * Specifies the bytecode version to be generated. We recommend you set this value to the
     * lowest API level able to provide all the functionality you are using. -1 means unspecified.
     *
     * It is not safe to read this value. Use [GeneratesApk.renderscript] instead
     *
     * @return the renderscript target api or -1 if not specified.
     */
    var renderscriptTargetApi: Int

    /**
     * Set to `true` if the variant's has any unit tests, false otherwise. Value is [Boolean#True]
     * by default.
     */
    @Deprecated(
        "Will be removed in AGP 9.0.",
        replaceWith=ReplaceWith("HasUnitTestBuilder.enableUnitTest")
    )
    var unitTestEnabled: Boolean

    /**
     * Set to `true` if the variant's has any unit tests, false otherwise. Value is [Boolean#True]
     * by default.
     */
    @Deprecated(
        "Will be removed in AGP 9.0.",
        replaceWith=ReplaceWith("HasUnitTestBuilder.enableUnitTest")
    )
    var enableUnitTest: Boolean

    /**
     * Registers an extension object to the variant object. Extension objects can be looked up
     * during the [AndroidComponentsExtension.onVariants] callbacks
     * by using the [Variant.getExtension] API.
     *
     * This is very useful for third party plugins that want to attach some variant specific
     * configuration object to the Android Gradle Plugin variant object and make it available to
     * other plugins.
     *
     * @param type the registered object type (can be a supertype of [instance]), this is the type
     * that must be passed to the [Variant.getExtension] API.
     * @param instance the object to associate to the AGP Variant object.
     */
    fun <T: Any> registerExtension(type: Class<out T>, instance: T)
}
