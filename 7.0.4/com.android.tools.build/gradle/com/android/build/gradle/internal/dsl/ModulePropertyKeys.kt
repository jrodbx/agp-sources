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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.provider.MapProperty

enum class ModulePropertyKeys(private val keyValue: String, private val defaultValue: Any) {

    /**
     * If false - the test APK instruments the target project APK, and the classes are provided.
     * If true - the test APK targets itself (e.g. for macro benchmarks)
     */
    SELF_INSTRUMENTING("android.experimental.self-instrumenting", false);


    fun getValue(properties: Map<String, Any>): Any {
        return properties[keyValue] ?: return defaultValue
    }

    fun getValueAsBoolean(properties: Map<String, Any>): Boolean {
        val value = properties[keyValue]
        if (value is Boolean) return value
        return if (value is String) value.toBoolean()
        else defaultValue as Boolean
    }
}
