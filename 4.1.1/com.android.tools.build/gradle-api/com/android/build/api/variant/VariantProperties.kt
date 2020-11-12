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

import com.android.build.api.component.ComponentProperties
import org.gradle.api.Incubating
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import java.io.Serializable

/**
 * Parent interface for all types of variants.
 */
@Incubating
interface VariantProperties: ComponentProperties {

    /**
     * Variant's application ID as present in the final manifest file of the APK.
     *
     * Some type of variants allows this to be writeable but for some it's only read-only.
     */
    val applicationId: Provider<String>

    /**
     * The package name into which some classes are generated
     */
    val packageName: Provider<String>

    /**
     * Variant's [BuildConfigField] which will be generated in the BuildConfig class.
     */
    val buildConfigFields: MapProperty<String, BuildConfigField<out Serializable>>

    /**
     * Convenience method to add a new Build Config field which value is known at configuration
     * time.
     *
     * @param key the build config field name
     * @param value the build config field value which type must be [Serializable]
     * @param comment optional comment for the field.
     */
    fun addBuildConfigField(key: String, value: Serializable, comment: String?)

    /**
     * Adds a ResValue element to the generated resources.
     * @param name the resource name
     * @param type the resource type like 'string'
     * @param value the resource value
     * @param comment optional comment to be added to the generated resource file for the field.
     */
    fun addResValue(name: String, type: String, value: String, comment: String?)

    /**
     * Adds a ResValue element to the generated resources.
     * @param name the resource name
     * @param type the resource type like 'string'
     * @param value a [Provider] for the value
     * @param comment optional comment to be added to the generated resource file for the field.
     */
    fun addResValue(name: String, type: String, value: Provider<String>, comment: String?)

    /**
     * [MapProperty] of the variant's manifest placeholders.
     *
     * Placeholders are organized with a key and a value. The value is a [String] that will be
     * used as is in the merged manifest.
     *
     * @return the [MapProperty] with keys as [String]
     */
    val manifestPlaceholders: MapProperty<String, String>
}