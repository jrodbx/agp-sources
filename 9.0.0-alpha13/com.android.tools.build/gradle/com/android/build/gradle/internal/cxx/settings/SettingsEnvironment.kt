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

package com.android.build.gradle.internal.cxx.settings
import com.google.gson.annotations.JsonAdapter

/**
 * Schema of 'environments' element from CMakeSettings.json.
 */
@JsonAdapter(SettingsEnvironmentSerializer::class)
data class SettingsEnvironment(
    /**
     * A way to categorize a list of “environment” groups. Allows it to be
     * referenced later.
     * Example: ‘env’ which would be referenced later as ‘env.variablename’.
     * Default is ‘env’ if not specified.
     */
    val namespace: String = "",

    /**
     * A unique identifier for this group of variables. Allows the group to be
     * inherited later in an 'inheritEnvironments' entry.
     */
    val environment: String = "",

    /**
     * The priority of these variables when evaluating them. Higher number
     * items are evaluated first.
     */
    val groupPriority: Int? = null,

    /**
     * A set of environments that are inherited by this group.
     * Any custom environment can be used.
     */
    val inheritEnvironments: List<String> = listOf(),

    /**
     * Environment properties.
     */
    val properties : Map<String, String> = mapOf()
)
