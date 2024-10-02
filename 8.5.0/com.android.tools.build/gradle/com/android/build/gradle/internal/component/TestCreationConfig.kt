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

package com.android.build.gradle.internal.component

import com.android.build.api.variant.AndroidVersion
import org.gradle.api.provider.Provider

/**
 * Interface for properties common to all test components.
 */
interface TestCreationConfig: ComponentCreationConfig {

    /**
     * In unit tests, we don't produce an apk. However, we still need to set the target sdk version
     * in the test manifest as robolectric depends on it. Sdk version may be taken from test options
     * if version defined there.
     */
    val targetSdkVersion: AndroidVersion

    /**
     * In unit tests, there is no dexing. However, aapt2 requires the instrumentation tag to be
     * present in the merged manifest to process android resources.
     */
    val instrumentationRunner: Provider<String>

    /**
     * The application of the app under tests
     */
    val testedApplicationId: Provider<String>
}
