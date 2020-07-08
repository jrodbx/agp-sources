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

package com.android.build.gradle.internal.dependency

import com.android.Version
import com.android.tools.build.jetifier.core.config.ConfigParser
import com.android.tools.build.jetifier.processor.Processor
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DirectDependencyMetadata

/**
 * Singleton object that maintains AndroidX mappings and configures AndroidX dependency substitution
 * rules.
 */
object AndroidXDependencySubstitution {

    /**
     * Mappings from old dependencies to AndroidX dependencies.
     *
     * Each entry maps "old-group:old-module" (without version) to
     * "new-group:new-module:new-version" (with version).
     */
    @JvmStatic
    val androidXMappings: Map<String, String> = Processor.createProcessor3(
            config = ConfigParser.loadDefaultConfig()!!,
            dataBindingVersion = Version.ANDROID_GRADLE_PLUGIN_VERSION
        ).getDependenciesMap(filterOutBaseLibrary = false)

    // Data binding base library may or may not need to be substituted (see
    // https://issuetracker.google.com/78202536)
    private val androidXMappingsWithoutDataBindingBaseLibrary = androidXMappings.filterKeys {
        it != COM_ANDROID_DATABINDING_BASELIBRARY
    }

    /**
     * Replaces old support libraries with AndroidX.
     */
    fun replaceOldSupportLibraries(project: Project, reasonToReplace: String) {
        // TODO (AGP): This is a quick fix to work around Gradle bug with dependency
        // substitution (https://github.com/gradle/gradle/issues/5174). Once Gradle has fixed
        // this issue, this should be removed.
        // Note that this complements but does not replace the dependency substitution rules that
        // follow (at the end of this method)
        project.dependencies.components.all { component ->
            component.allVariants { variant ->
                variant.withDependencies { metadata ->
                    val oldDeps = mutableSetOf<String>()
                    val newDeps = mutableListOf<String>()
                    metadata.forEach {
                        val oldDep = "${it.group}:${it.name}"
                        // Data binding base library will be handled later
                        val newDep = androidXMappingsWithoutDataBindingBaseLibrary[oldDep]
                        if (newDep != null) {
                            oldDeps.add(oldDep)
                            newDeps.add(newDep)
                        }
                    }
                    // Can't use metadata.removeAll(Set<DirectDependenciesMetadata>) because of
                    // bug 161778526.
                    metadata.removeAll { "${it.group}:${it.name}" in oldDeps }
                    newDeps.forEach{ metadata.add(it) }
                }
            }
        }

        project.configurations.all { configuration ->
            // Apply the rules just before the configurations are resolved because too many rules
            // could significantly impact memory usage and build speed. (Many configurations are not
            // resolvable or resolvable but not actually resolved during a build.)
            if (!configuration.isCanBeResolved) {
                return@all
            }
            configuration.incoming.beforeResolve {
                configuration.resolutionStrategy.dependencySubstitution {
                    val mappings = if (skipDataBindingBaseLibrarySubstitution(configuration)) {
                        androidXMappingsWithoutDataBindingBaseLibrary
                    } else {
                        androidXMappings
                    }
                    for (entry in mappings) {
                        // entry.key is in the form of "group:module" (without a version), and
                        // Gradle accepts that form.
                        it.substitute(it.module(entry.key))
                            .because(reasonToReplace)
                            .with(it.module(entry.value))
                    }
                }
            }
        }
    }

    /**
     * Returns `true` if the data binding base library (com.android.databinding:baseLibrary) should
     * not be replaced with AndroidX in the given configuration (see
     * https://issuetracker.google.com/78202536).
     *
     * Specifically:
     *   - When data binding is enabled, the annotation processor classpath will contain
     *     androidx.databinding:databinding-compiler, which (transitively) depends on
     *     com.android.databinding:baseLibrary. In that case, com.android.databinding:baseLibrary
     *     should not be replaced with AndroidX.
     *   - If com.android.databinding:baseLibrary appears in a configuration that is not an
     *     annotation processor classpath, then it should still be replaced with AndroidX.
     */
    private fun skipDataBindingBaseLibrarySubstitution(configuration: Configuration): Boolean {
        return configuration.name.endsWith("AnnotationProcessorClasspath")
                || configuration.name.startsWith("kapt")
    }

    /**
     * Returns `true` if the given dependency (formatted as `group:name:version`) is an AndroidX
     * dependency.
     */
    fun isAndroidXDependency(dependency: String): Boolean {
        return dependency.startsWith("androidx")
                || dependency.startsWith(COM_GOOGLE_ANDROID_MATERIAL)
    }

    private const val COM_ANDROID_DATABINDING_BASELIBRARY = "com.android.databinding:baseLibrary"
    private const val COM_GOOGLE_ANDROID_MATERIAL = "com.google.android.material"
}