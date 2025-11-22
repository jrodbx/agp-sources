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

import com.android.build.gradle.internal.cxx.logging.errorln

/**
 * A name resolver that looks up user-defined values of name in a group of
 * [SettingsEnvironment]s. For example,
 *
 *   "environments": [ {
 *     "namespace": "ndk",
 *     "environment": "android-ndk-1",
 *     "groupPriority" : 50,
 *     // User defined below here
 *     "minPlatform": "16",
 *     "maxPlatform": "28"
 *     }, {
 *     "namespace": "ndk",
 *     "environment": "android-ndk-2",
 *     "inheritEnvironments": ["android-ndk-1"],
 *     // User defined below here
 *     "minPlatform": "17"
 *     } ]
 *
 * Resolution rules:
 * (1) A name must be resolved from the list of includeEnvironments passed to the resolve
 *     function **or** from the child includeEnvironments of those recursively.
 * (2) Environments with higher groupPriority take precedence when there is more than one
 *     possible value for a name.
 * (3) The resolution order within a single groupPriority set is by order of
 *     includeEnvironments. The includers take precedence of the includees.
 * (4) Mutually inclusive environments are handled by excluding an environment if it is
 *     seen a second time.
 * (5) While two environments with the same name is considered invalid, this function
 *     resolves the ambiguity by ignoring the second environment that has the same name.
 *
 * Rule (1) and (2) are defined by Microsoft. See comments in [SettingsEnvironment].
 *
 * Rule (3) isn't stated anywhere but it's the only rule that can work because user-written
 * CMakeSettings.json (the includer) would be expected to override built-in values that the
 * user has no control over.
 *
 * Rule (4) is required to keep the code from stack overflowing.
 *
 * Rule (5) is chosen and it is left the responsibility of higher level code to alert the
 * user. For example, a lint rule or quickfix in the IDE.
 */
class SettingsEnvironmentNameResolver(environments: List<SettingsEnvironment>) {

    private val lookupTables = mutableMapOf<List<String>, Map<String, String>>()

    /**
     * Group the list of environments in [Settings] by the name of the environment.
     * Note that multiple environments can have the same name.
     */
    private val environmentMap = environments
            .groupBy { env -> env.environment }

    /**
     * Stores a resolution match along with the groupPriority of the environment that it came
     * from.
     */
    private data class Match(
        val groupPriority : Int,
        val propertyValue: String
    )

    /**
     * Recursively walk through environments and inherited environments finding all properties.
     */
    private fun toLookup(
        environmentNames: List<String>,
        excludeEnvironmentNames: MutableSet<String>,
        matches : MutableMap<Pair<String, String>, Match> = mutableMapOf())  {
        for(environmentName in environmentNames) {
            if (!excludeEnvironmentNames.add(environmentName)) continue
            for(environment in environmentMap[environmentName] ?: listOf()) {
                val currentGroupPriority = environment.groupPriority ?: 0
                for((name, value) in environment.properties) {
                    val fullname = environment.namespace to name
                    val prior = matches[fullname]
                    if (prior == null ||
                        currentGroupPriority > prior.groupPriority) {
                        matches[fullname] = Match(currentGroupPriority, value)
                    }
                }
                toLookup(
                    environment.inheritEnvironments,
                    excludeEnvironmentNames,
                    matches
                )
            }
        }
    }

    private fun toLookupTable(environmentNames: List<String>) : Map<String, String> {
        val matches = mutableMapOf<Pair<String, String>, Match>()
        toLookup(
            environmentNames,
            mutableSetOf(),
            matches
        )
        val result = mutableMapOf<String, String>()
        for((key, match) in matches) {
            val (environment, name) = key
            val qualified = "${environment}.$name"
            result[qualified] = match.propertyValue
            if (environment == "env") {
                // "env" environment is special in that it doesn't require full
                // qualification to match.
                result[name] = match.propertyValue
            }
        }
        return result
    }

    /**
     * Resolve [qualifiedName] such as env.property to it's value within the scope of
     * [environmentNames].
     *
     * If [qualifiedName] doesn't contain a namespace then 'env' is assumed.
     */
    fun resolve(
        qualifiedName : String,
        environmentNames: List<String>) : String? {
        return lookupTables.computeIfAbsent(environmentNames) {
            toLookupTable(environmentNames = environmentNames)
        }[qualifiedName]
    }
}
