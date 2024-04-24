/*
 * Copyright (C) 2021 The Android Open Source Project
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

import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

/**
 * Build-time properties for [Component] that produce test APKs.
 */
interface GeneratesTestApk: GeneratesApk {

    /**
     * The instrumentationRunner to use to run the tests.
     */
    val instrumentationRunner: Property<String>

    /**
     * The instrumentationRunnerArguments to use to run the tests.
     */
    val instrumentationRunnerArguments: MapProperty<String, String>

    /**
     * The handleProfiling value to use to run the tests.
     */
    val handleProfiling: Property<Boolean>

    /**
     * The functionalTest value to use to run the tests.
     */
    val functionalTest: Property<Boolean>

    /**
     * The test label.
     */
    val testLabel: Property<String?>
}
