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

import com.android.build.gradle.internal.cxx.logging.errorln

/**
 * A name resolver that looks up user-defined values of name in a group of
 * [CMakeSettingsEnvironment]s. For example,
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
 * Rule (1) and (2) are defined by Microsoft. See comments in [CMakeSettingsEnvironment].
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
class CMakeSettingsNameResolver(environments: List<CMakeSettingsEnvironment>) {

    /**
     * Group the list of environments in [CMakeSettings] by the name of the environment.
     * Note that multiple environments can have the same name.
     */
    private val environmentMap = environments.groupBy { env -> env.environment }

    /**
     * Stores a resolution match along with the groupPriority of the environment that it came
     * from.
     */
    private data class Match(
        val groupPriority : Int,
        val propertyValue: PropertyValue
    )

    /**
     * Recursively walk through environments and inherited environments finding all properties
     * that match [namespace] and [name].
     */
    private fun resolveAll(
        namespace: String,
        name: String,
        environmentNames: List<String>,
        excludeEnvironmentNames: MutableSet<String>,
        matches : MutableSet<Match>) {
        environmentNames
            .filter { !excludeEnvironmentNames.contains(it) }
            .forEach { environmentName ->
                excludeEnvironmentNames.add(environmentName)
                environmentMap[environmentName]
                    ?.forEach { environment ->
                        if (namespace == environment.namespace) {
                            environment.properties[name]?.let { value ->
                                matches += Match(environment.groupPriority ?: 0, value)
                            }
                        }
                        resolveAll(
                            namespace,
                            name,
                            environment.inheritEnvironments,
                            excludeEnvironmentNames,
                            matches)
                    }
        }
    }

    /**
     * Resolve [qualifiedName] such as env.property to it's value within the scope of
     * [environmentNames].
     *
     * If [qualifiedName] doesn't contain a namespace then 'env' is assumed.
     */
    fun resolve(
        qualifiedName : String,
        environmentNames: List<String>) : PropertyValue? {
        val (namespace, name) =
            if (qualifiedName.contains(".")) {
                val split = qualifiedName.split(".")
                if (split.size != 2) {
                    errorln("Expected qualified name '$qualifiedName' to have two parts " +
                            "but it had ${split.size}")
                }
                split
            }
            else listOf("env", qualifiedName)
        val matches = mutableSetOf<Match>()
        resolveAll(
            namespace,
            name,
            environmentNames,
            mutableSetOf(),
            matches)
        return matches
            .sortedByDescending { it.groupPriority }
            .firstOrNull()
            ?.propertyValue
    }
}
