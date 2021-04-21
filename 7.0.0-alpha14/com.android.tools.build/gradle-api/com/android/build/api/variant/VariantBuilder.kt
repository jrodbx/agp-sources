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

package com.android.build.api.variant

import com.android.build.api.component.ComponentBuilder
import org.gradle.api.Incubating

/**
 * Variant object that contains properties that must be set during configuration time as it
 * changes the build flow for the variant.
 */
@Incubating
interface VariantBuilder: ComponentBuilder {

    /**
     * Gets or sets the minimum supported SDK Version for this variant.
     * Setting this it will override previous calls of [minSdk] and [minSdkPreview] setters. Only
     * one of [minSdk] and [minSdkPreview] should be set.
     *
     * @return the minimum supported SDK Version or null if [minSdkPreview] was used to set it.
     */
    var minSdk: Int?

    /**
     * Gets or sets the minimum supported SDK Version for this variant as a Preview codename.
     * Setting this it will override previous calls of [minSdk] and [minSdkPreview] setters. Only
     * one of [minSdk] and [minSdkPreview] should be set.
     *
     * @return the minimum supported SDK Version or null if [minSdk] was used to set it.
     */
    var minSdkPreview: String?

    /**
     * Gets the maximum supported SDK Version for this variant.
     */
    var maxSdk: Int?

    /**
     * Gets or sets the target SDK Version for this variant as a Preview codename.
     * Setting this it will override previous calls of [targetSdk] and [targetSdkPreview] setters.
     * Only one of [targetSdk] and [targetSdkPreview] should be set.
     *
     * @return the target SDK Version or null if [targetSdkPreview] was used to set it.
     */
    var targetSdk: Int?

    /**
     * Gets or sets the target SDK Version for this variant as a Preview codename.
     * Setting this it will override previous calls of [targetSdk] and [targetSdkPreview] setters.
     * Only one of [targetSdk] and [targetSdkPreview] should be set.
     *
     * @return the target supported SDK Version or null if [targetSdkPreview] was used to set it.
     */
    var targetSdkPreview: String?

    /**
     * Specifies the bytecode version to be generated. We recommend you set this value to the
     * lowest API level able to provide all the functionality you are using
     *
     * @return the renderscript target api or -1 if not specified.
     */
    var renderscriptTargetApi: Int

    /**
     * Set to `true` if the variant's has any unit tests, false otherwise. Value is [Boolean#True]
     * by default.
     */
    @get:Deprecated("Use enableUnitTest", replaceWith=ReplaceWith("enableUnitTest"))
    var unitTestEnabled: Boolean

    /**
     * Set to `true` if the variant's has any unit tests, false otherwise. Value is [Boolean#True]
     * by default.
     */
    var enableUnitTest: Boolean


    /**
     * Registers an extension object to the variant object. Extension objects can be looked up
     * during the [com.android.build.api.extension.AndroidComponentsExtension.onVariants] callbacks
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
