/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle

/**
 * User configuration settings for 'com.android.test' project.
 */
interface TestAndroidConfig : AndroidConfig {

    /**
     * Returns the Gradle path of the project that this test project tests.
     */
    val targetProjectPath: String?

    /**
     * Returns the variant of the tested project.
     *
     * Default is 'debug'
     *
     * @deprecated This is deprecated, test module can now test all flavors.
     */
    @Deprecated("The test project now tests all flavors")
    val targetVariant: String
}
