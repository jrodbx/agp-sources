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

package com.android.build.api.component

import com.android.build.api.variant.Aapt
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.ApkPackaging
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.SigningConfig
import org.gradle.api.Incubating
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.Serializable

/**
 * Properties for the android test Variant of a module.
 */
@Incubating
interface AndroidTest : TestComponent {

    /**
     * Variant's application ID as present in the final manifest file of the APK.
     */
    val applicationId: Property<String>

    /**
     * Variant's aaptOptions, initialized by the corresponding global DSL element.
     */
    val aapt: Aapt

    /**
     * The namespace of the generated R and BuildConfig classes. Also, the namespace used to resolve
     * any relative class names that are declared in the AndroidManifest.xml.
     *
     * This value supersedes any value specified by the `package` attribute in the source
     * AndroidManifest.xml, but doing a 'get' on this property will not retrieve the value specified
     * in the AndroidManifest.xml.
     */
    val namespace: Provider<String>

    /**
     * The instrumentationRunner to use to run the tests.
     */
    val instrumentationRunner: Property<String>

    /**
     * The handleProfiling value to use to run the tests.
     */
    val handleProfiling: Property<Boolean>

    /**
     * The functionalTest value to use to run the tests.
     */
    val functionalTest: Property<Boolean>

    /** The test label.  */
    val testLabel: Property<String?>

    /**
     * Variant's [BuildConfigField] which will be generated in the BuildConfig class.
     */
    val buildConfigFields: MapProperty<String, out BuildConfigField<out Serializable>>

    /**
     * Adds a ResValue element to the generated resources.
     * @param name The resource name.
     * @param type The resource type like 'string'.
     * @param value The resource value.
     * @param comment Optional comment to be added to the generated resource file for the field.
     */
    fun addResValue(name: String, type: String, value: String, comment: String?)

    /**
     * Adds a ResValue element to the generated resources.
     * @param name The resource name.
     * @param type The resource type like 'string'.
     * @param value A [Provider] for the value.
     * @param comment Optional comment to be added to the generated resource file for the field.
     */
    fun addResValue(name: String, type: String, value: Provider<String>, comment: String?)


    /**
     * [MapProperty] of the variant's manifest placeholders.
     *
     * Placeholders are organized with a key and a value. The value is a [String] that will be
     * used as is in the merged manifest.
     *
     * @return The [MapProperty] with keys as [String].
     */
    val manifestPlaceholders: MapProperty<String, String>

    /**
     * Variant's signingConfig, initialized by the corresponding DSL element.
     * @return Variant's config or null if the variant is not configured for signing.
     */
    val signingConfig: SigningConfig?

    /**
     * Variant's packagingOptions, initialized by the corresponding global DSL element.
     */
    val packaging: ApkPackaging

    /**
     * Variant specific settings for the renderscript compiler. This will return null when
     * [com.android.build.api.dsl.BuildFeatures.renderScript] is false.
     */
    val renderscript: Renderscript?
}
