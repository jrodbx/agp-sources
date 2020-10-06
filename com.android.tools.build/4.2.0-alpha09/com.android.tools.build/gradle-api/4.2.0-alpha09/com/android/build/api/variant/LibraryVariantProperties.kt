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

import org.gradle.api.Incubating
import org.gradle.api.provider.Provider

/** [VariantProperties] for Library projects */
@Incubating
interface LibraryVariantProperties : VariantProperties {

    /**
     * Variant's application ID as present in the final manifest file of the APK.
     *
     * This is a read-ony value based on the package entry in the manifest
     */
    override val applicationId: Provider<String>

    /**
     * Variant's packagingOptions, initialized by the corresponding global DSL element.
     */
    override val packagingOptions: LibraryPackagingOptions

    /**
     * Variant's packagingOptions, initialized by the corresponding global DSL element.
     */
    fun packagingOptions(action: LibraryPackagingOptions.() -> Unit)
}
