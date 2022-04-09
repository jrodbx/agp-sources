/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.build.gradle.options.StringOption

enum class ModuleStringPropertyKeys(private val _keyValue: String, private val _defaultValue: String?) {

    ANDROID_PRIVACY_SANDBOX_SDK_API_GENERATOR(
            StringOption.ANDROID_PRIVACY_SANDBOX_SDK_API_GENERATOR.propertyName,
            null),

    ANDROID_PRIVACY_SANDBOX_SDK_API_GENERATOR_GENERATED_RUNTIME_DEPENDENCIES(
            StringOption.ANDROID_PRIVACY_SANDBOX_SDK_API_GENERATOR_GENERATED_RUNTIME_DEPENDENCIES.propertyName,
            null),
    ;

    fun getValueAsType(properties: Map<String, Any>): String? {
        return properties[keyValue]?.toString() ?: _defaultValue
    }

    val keyValue: String get() = _keyValue
}
