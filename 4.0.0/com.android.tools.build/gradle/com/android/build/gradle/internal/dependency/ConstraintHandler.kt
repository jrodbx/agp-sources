/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency

import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler
import org.gradle.api.artifacts.result.ResolvedComponentResult

/**
 * An Action to synchronize a dependency with a runtimeClasspath.
 *
 * This is meant to be passed to [ResolvableDependencies.beforeResolve]
 */
class ConstraintHandler(
    private val srcConfiguration: Configuration,
    private val constraints: DependencyConstraintHandler,
    private val isTest: Boolean
) : Action<ResolvableDependencies> {
    override fun execute(resolvableDependencies: ResolvableDependencies) {
        val srcConfigName = srcConfiguration.name
        val srcComponents: Set<ResolvedComponentResult> =
            srcConfiguration.incoming.resolutionResult.allComponents

        val configName = resolvableDependencies.name

        // loop on all the artifacts and set constraints for the compile classpath.
        srcComponents.forEach { resolvedComponentResult ->
            val id = resolvedComponentResult.id
            if (id is ModuleComponentIdentifier) {
                // using a repository with a flatDir to stock local AARs will result in an
                // external module dependency with no version.
                if (!id.version.isNullOrEmpty()) {
                    if (!isTest || id.module != "listenablefuture" || id.group != "com.google.guava" || id.version != "1.0") {
                        constraints.add(
                            configName,
                            "${id.group}:${id.module}:${id.version}"
                        ) { constraint ->
                            constraint.because(cacheString("$srcConfigName uses version ${id.version}"))
                            constraint.version { versionConstraint ->
                                versionConstraint.strictly(id.version)
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val strings = mutableMapOf<String, String>()

        internal fun cacheString(newString: String): String {
            synchronized(strings) {
                val existingString = strings[newString]
                return if (existingString == null) {
                    strings[newString] = newString
                    newString
                } else {
                    existingString
                }
            }
        }

        @JvmStatic
        fun clearCache() {
            synchronized(strings) {
                strings.clear()
            }
        }
    }
}