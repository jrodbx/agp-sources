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

import com.android.build.api.component.AndroidTest
import com.android.build.api.component.AndroidTestProperties
import com.android.build.api.component.Component
import com.android.build.api.component.UnitTest
import com.android.build.api.component.UnitTestProperties
import com.android.build.api.component.ActionableComponentObject
import org.gradle.api.Incubating

/**
 * Variant object that contains properties that must be set during configuration time as it
 * changes the build flow for the variant.
 *
 * @param PropertiesT the [VariantProperties] type associated with this [Variant]
 */
@Incubating
interface Variant<PropertiesT : VariantProperties>: Component<PropertiesT>,
    ActionableComponentObject {

    fun unitTest(action: UnitTest<UnitTestProperties>.() -> Unit)
    fun unitTestProperties(action: UnitTestProperties.() -> Unit)

    fun androidTest(action: AndroidTest<AndroidTestProperties>.() -> Unit)
    fun androidTestProperties(action: AndroidTestProperties.() -> Unit)

    var minSdkVersion: Int
}