/*
 * Copyright (C) 2024 The Android Open Source Project
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

import org.gradle.api.Incubating
import org.gradle.api.Named
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import java.io.Serializable

/**
 * Model for Device Test components that contains build-time properties
 *
 * This object is accessible on subtypes of [Variant] that implement [HasDeviceTests], via
 * [HasDeviceTests.deviceTests]. It is also part of [Variant.nestedComponents].
 *
 * The presence of this component in a variant is controlled by
 * [HasDeviceTestsBuilder.deviceTests] and [DeviceTestBuilder.enable] which is accessible on
 * subtypes of [VariantBuilder] that implement [HasDeviceTestsBuilder]
 */
@Incubating
interface DeviceTest: GeneratesTestApk, HasAndroidResources, TestComponent {

    /**
     * Variant's application ID as present in the final manifest file of the APK.
     */
    @get:Incubating
    override val applicationId: Property<String>

    /**
     * Variant's signingConfig, initialized by the corresponding DSL element.
     * @return Variant's config or null if the variant is not configured for signing.
     */
    @get:Incubating
    val signingConfig: SigningConfig?

    /**
     * Variant's [BuildConfigField] which will be generated in the BuildConfig class.
     */
    @get:Incubating
    val buildConfigFields: MapProperty<String, out BuildConfigField<out Serializable>>

    /**
     * List of proguard configuration files for this variant. The list is initialized from the
     * corresponding DSL element, and cannot be queried at configuration time. At configuration time,
     * you can only add new elements to the list.
     */
    @get:Incubating
    val proguardFiles: ListProperty<RegularFile>
}
