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

import com.android.build.gradle.api.TestVariant
import com.android.build.gradle.api.UnitTestVariant
import org.gradle.api.DomainObjectSet

/**
 * User configuration settings for android plugin with test component.
 */
interface TestedAndroidConfig : AndroidConfig {

    /** The name of the BuildType for testing. */
    override val testBuildType: String

    /**
     * The list of (Android) test variants. Since the collections is built after evaluation,
     * it should be used with Gradle's `all` iterator to process future items.
     */
    val testVariants: DomainObjectSet<TestVariant>

    /**
     * The list of (Android) test variants. Since the collections is built after evaluation,
     * it should be used with Gradle's `all` iterator to process future items.
     */
    val unitTestVariants: DomainObjectSet<UnitTestVariant>
}
