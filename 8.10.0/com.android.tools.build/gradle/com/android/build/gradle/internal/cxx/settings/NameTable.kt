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

/**
 * Convenience class for pairing [Macro] with their string value.
 */
class NameTable(vararg pairs : Pair<Macro, String?>) {
    private val table = mutableMapOf<Macro, String>()

    init {
        addAll(*pairs)
    }

    /**
     * Set a sing [Macro] value.
     */
    operator fun set(key : Macro, value: String?) {
        if (value == null) return
        if (key.ref == value) return
        table[key] = value
    }

    /**
     * Set multiple [Macro] values at once.
     */
    fun addAll(vararg pairs: Pair<Macro, String?>) {
        addAll(pairs.toList())
    }

    /**
     * Set multiple [Macro] values at once. Ignore null values.
     */
    fun addAll(pairs: List<Pair<Macro, String?>>) {
        pairs.forEach { (key, value) -> set(key, value) }
    }

    /**
     * For all of the recorded macros, break them out by their individual environments.
     */
    fun environments() = table
        .toList()
        .groupBy { (macro,_) -> macro.environment }
        .map { (environment, properties) ->
            SettingsEnvironment(
                    namespace = environment.namespace,
                    environment = environment.environment,
                    inheritEnvironments = environment.inheritEnvironments.map { it.environment },
                    properties = properties
                            .map { (macro, property) -> Pair(macro.tag, property) }
                            .toMap()
            )
        }
}
