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

package com.android.build.gradle.internal.cxx.settings

import com.android.build.gradle.internal.cxx.settings.PropertyValue.LookupPropertyValue
import com.android.build.gradle.internal.cxx.settings.PropertyValue.StringPropertyValue
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

/**
 * Serializes from json to [CMakeSettingsEnvironment].
 * A custom serializer is required because there are predefined properties like "namespace"
 * mixed with custom defined properties Map<String, String>.
 */
class CMakeSettingsEnvironmentSerializer :
    JsonDeserializer<CMakeSettingsEnvironment>,
    JsonSerializer<CMakeSettingsEnvironment> {
    override fun deserialize(
        element: JsonElement,
        type: Type,
        context: JsonDeserializationContext?
    ): CMakeSettingsEnvironment {
        val obj = element as JsonObject
        val result = CMakeSettingsEnvironment()
        val properties: MutableMap<String, PropertyValue> = mutableMapOf()
        var namespace = ""
        var environment = ""
        var groupPriority: Int? = null
        var inheritEnvironments: List<String> = listOf()

        for ((key, value) in obj.entrySet()) {
            when (key) {
                "namespace" -> namespace = value.asString
                "environment" -> environment = value.asString
                "groupPriority" -> groupPriority = value.asInt
                "inheritEnvironments" -> {
                    inheritEnvironments = value.asJsonArray
                        .map { it.asString }
                        .toMutableList()
                }
                else -> {
                    result.properties
                    properties[key] = StringPropertyValue(value.asString)
                }
            }
        }
        return CMakeSettingsEnvironment(
            namespace = namespace,
            environment = environment,
            groupPriority = groupPriority,
            inheritEnvironments = inheritEnvironments,
            properties = properties
        )
    }

    override fun serialize(
        environment: CMakeSettingsEnvironment,
        type: Type,
        context: JsonSerializationContext
    ): JsonElement {
        val list = mutableMapOf<String, Any?>()
        list["namespace"] = environment.namespace
        list["environment"] = environment.environment
        list["groupPriority"] = environment.groupPriority
        list["inheritEnvironments"] = environment.inheritEnvironments
        for ((key, value) in environment.properties) {
            when (value) {
                is StringPropertyValue -> list[key] = value.value
                is LookupPropertyValue -> list[key] = value.value()
            }
        }
        return context.serialize(list)
    }
}