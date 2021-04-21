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

import org.gradle.api.Incubating
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/**
 * [Variant] for test-only modules.
 */
@Incubating
interface TestVariant: Variant, ProducesDex<Dexing> {
    /**
     * Variant's application ID as present in the final manifest file of the APK.
     */
    override val applicationId: Property<String>

    /**
     * Variant's aaptOptions, initialized by the corresponding global DSL element.
     */
    val aapt: Aapt

    /**
     * Variant's aaptOptions, initialized by the corresponding global DSL element.
     */
    fun aaptOptions(action: Aapt.() -> Unit)

    /**
     * The application of the app under tests.
     */
    val testedApplicationId: Provider<String>

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

    /** the test label  */
    val testLabel: Property<String?>

    /**
     * Variant's packagingOptions, initialized by the corresponding global DSL element.
     */
    override val packaging: ApkPackaging

    /**
     * Variant's packagingOptions, initialized by the corresponding global DSL element.
     */
    fun packaging(action: ApkPackaging.() -> Unit)
}
