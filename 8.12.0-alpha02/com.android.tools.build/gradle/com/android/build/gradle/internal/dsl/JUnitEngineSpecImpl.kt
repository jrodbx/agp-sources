/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.AgpTestSuiteInputParameters
import com.android.build.api.dsl.JUnitEngineSpec
import org.gradle.api.provider.Provider

abstract class JUnitEngineSpecImpl: JUnitEngineSpec {

    override val includeEngines = mutableSetOf<String>()
    override val inputs = mutableListOf<AgpTestSuiteInputParameters>()

    override fun addInputProperty(propertyName: String, propertyValue: String) {
        inputStaticProperties[propertyName] = propertyValue
    }

    override fun addInputProperty(propertyName: String, propertyValue: Provider<String>) {
        inputProperties[propertyName] = propertyValue
    }

    internal val inputStaticProperties = mutableMapOf<String, String>()
    internal val inputProperties = mutableMapOf<String, Provider<String>>()
}
