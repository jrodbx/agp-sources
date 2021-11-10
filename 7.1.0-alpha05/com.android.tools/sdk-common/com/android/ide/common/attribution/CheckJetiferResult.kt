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

package com.android.ide.common.attribution

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

/**
 * Result of the `CheckJetifier` task.
 *
 * It contains information about any direct dependencies that a project uses which
 * directly/transitively depend on support libraries, or are support libraries themselves (see
 * [dependenciesDependingOnSupportLibs]).
 *
 * Note that this class can represent a sub-result as well as an aggregated result (see
 * [aggregateResult]).
 */
class CheckJetifierResult(

    /**
     * A map where the key is a direct dependency that a project uses and the value is a
     * [FullDependencyPath] from a configuration of the project to the direct dependency, to any
     * intermediate dependencies, then to a support library.
     *
     * The direct dependency may be a support library itself.
     *
     * There may be multiple paths to and from the direct dependency, but this class retains only
     * one path.
     */
    val dependenciesDependingOnSupportLibs: LinkedHashMap<String, FullDependencyPath>
) : Serializable {

    fun isEmpty() = dependenciesDependingOnSupportLibs.isEmpty()

    fun getDisplayString(): String {
        return dependenciesDependingOnSupportLibs.map { (directDependency, fullDependencyPath) ->
            "$directDependency (${fullDependencyPath.getPathString()})"
        }.joinToString("\n")
    }

    companion object {

        private const val serialVersionUID = 0L

        fun save(result: CheckJetifierResult, resultFile: File) {
            ObjectOutputStream(FileOutputStream(resultFile).buffered()).use {
                it.writeObject(result)
            }
        }

        fun load(resultFile: File): CheckJetifierResult {
            return ObjectInputStream(FileInputStream(resultFile).buffered()).use {
                it.readObject() as CheckJetifierResult
            }
        }

        fun aggregateResult(
            first: CheckJetifierResult,
            second: CheckJetifierResult
        ): CheckJetifierResult {
            val combinedResult =
                LinkedHashMap<String, FullDependencyPath>(
                    first.dependenciesDependingOnSupportLibs.size + second.dependenciesDependingOnSupportLibs.size
                )
            // If a key exists in both map, keep only one value (see `CheckJetifierResult`'s kdoc).
            for (key in first.dependenciesDependingOnSupportLibs.keys + second.dependenciesDependingOnSupportLibs.keys) {
                val firstValue = first.dependenciesDependingOnSupportLibs[key]
                val secondValue = second.dependenciesDependingOnSupportLibs[key]
                // Compare values to get a deterministic merge
                if (firstValue == null ||
                    secondValue != null && firstValue.getPathString() > secondValue.getPathString()) {
                    combinedResult[key] = secondValue!!
                } else {
                    combinedResult[key] = firstValue
                }
            }
            return CheckJetifierResult(combinedResult)
        }
    }
}

/**
 * A path from a configuration of a project to a direct dependency, to any intermediate
 * dependencies, then to another dependency.
 */
class FullDependencyPath(
    val projectPath: String,
    val configuration: String,
    val dependencyPath: DependencyPath
) : Serializable {

    companion object {
        private const val serialVersionUID = 0L
    }

    fun getPathString() = "Project '$projectPath', configuration '$configuration' -> " +
            dependencyPath.elements.joinToString(" -> ")
}

/**
 * A path from a dependency, to any intermediate dependencies, then to another dependency.
 *
 * The path contains at least 1 element.
 */
class DependencyPath(val elements: List<String>) : Serializable {

    companion object {
        private const val serialVersionUID = 0L
    }

    init {
        check(elements.isNotEmpty())
    }

    fun getPathString() = elements.joinToString(" -> ")
}
