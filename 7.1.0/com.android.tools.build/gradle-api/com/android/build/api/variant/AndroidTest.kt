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

package com.android.build.api.variant

import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.Serializable

/**
 * Properties for the android test Variant of a module.
 */
interface AndroidTest : GeneratesTestApk, TestComponent, HasAndroidResources {

    /**
     * Variant's application ID as present in the final manifest file of the APK.
     */
    override val applicationId: Property<String>

    /**
     * The namespace of the generated R and BuildConfig classes. Also, the namespace used to resolve
     * any relative class names that are declared in the AndroidManifest.xml.
     */
    val namespace: Provider<String>

    /**
     * Variant's [BuildConfigField] which will be generated in the BuildConfig class.
     */
    val buildConfigFields: MapProperty<String, out BuildConfigField<out Serializable>>

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
     * List of proguard configuration files for this variant. The list is initialized from the
     * corresponding DSL element, and cannot be queried at configuration time. At configuration time,
     * you can only add new elements to the list.
     */
    val proguardFiles: ListProperty<RegularFile>
}
