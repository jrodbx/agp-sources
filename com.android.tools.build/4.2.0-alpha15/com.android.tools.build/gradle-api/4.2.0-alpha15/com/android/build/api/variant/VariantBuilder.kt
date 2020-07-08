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

import com.android.build.api.component.AndroidTestBuilder
import com.android.build.api.component.AndroidTest
import com.android.build.api.component.ComponentBuilder
import com.android.build.api.component.UnitTestBuilder
import com.android.build.api.component.UnitTest
import com.android.build.api.component.ActionableComponentObject
import org.gradle.api.Incubating

/**
 * Variant object that contains properties that must be set during configuration time as it
 * changes the build flow for the variant.
 */
@Incubating
interface VariantBuilder: ComponentBuilder, ActionableComponentObject {

    fun unitTest(action: UnitTestBuilder.() -> Unit)
    fun unitTestProperties(action: UnitTest.() -> Unit)

    fun androidTest(action: AndroidTestBuilder.() -> Unit)
    fun androidTestProperties(action: AndroidTest.() -> Unit)

    /**
     * Gets the minimum supported SDK Version for this variant.
     */
    var minSdkVersion: AndroidVersion

    /**
     * Gets the maximum supported SDK Version for this variant.
     */
    var maxSdkVersion: Int?

    /**
     * Specifies the bytecode version to be generated. We recommend you set this value to the
     * lowest API level able to provide all the functionality you are using
     *
     * @return the renderscript target api or -1 if not specified.
     */
    var renderscriptTargetApi: Int
}
