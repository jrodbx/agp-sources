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

import com.android.ide.common.attribution.CheckJetifierResult.Companion.aggregateResults
import com.android.ide.common.attribution.CheckJetifierResult.Version.CURRENT_VERSION
import com.google.gson.GsonBuilder
import java.io.File

/**
 * Result of the `CheckJetifier` task.
 *
 * It contains information about any direct dependencies that a project uses which
 * directly/transitively depend on support libraries, or are support libraries themselves (see
 * [dependenciesDependingOnSupportLibs]).
 *
 * Note that this class can represent a sub-result as well as an aggregated result (see
 * [aggregateResults]).
 */
class CheckJetifierResult(

    /**
     * Version of this object, used to handle backward/forward compatibility during deserialization.
     *
     * Note that if this object is created through deserialization, the version may be different
     * from [CURRENT_VERSION].
     *
     * We use type [Double] instead of [String] for easy parsing and comparison.
     *
     * IMPORTANT: If the name or type of any of this object's fields has changed, remember to
     * increase [CURRENT_VERSION] (this applies recursively to the types of those fields). Note that
     * the name and type of this [version] field must not change.
     */
    val version: Double,

    /**
     * A map where the key is a direct dependency that a project uses and the value is a
     * [FullDependencyPath] from a configuration of the project to the direct dependency, to any
     * intermediate dependencies, then to a support library.
     *
     * The direct dependency may be a support library itself.
     *
     * There may be multiple paths to and from the direct dependency, but this class retains only
     * one path.
     *
     * A dependency is described by a String (as returned by
     * `org.gradle.api.artifacts.component.ComponentIdentifier.getDisplayName()`), usually in the
     * form of "group:name:version".
     */
    val dependenciesDependingOnSupportLibs: LinkedHashMap<String, FullDependencyPath>
) {

    private object Version {

        /**
         * The current version of [CheckJetifierResult] (see [CheckJetifierResult.version]).
         *
         * IMPORTANT: If the name or type of any of [CheckJetifierResult]'s fields has changed,
         * remember to increase [CURRENT_VERSION] (this applies recursively to the types of those
         * fields). Note that the type of this [CURRENT_VERSION] constant must not change.
         */
        const val CURRENT_VERSION: Double = 1.0
    }

    constructor(dependenciesDependingOnSupportLibs: LinkedHashMap<String, FullDependencyPath>) :
            this(CURRENT_VERSION, dependenciesDependingOnSupportLibs)

    fun isEmpty() = dependenciesDependingOnSupportLibs.isEmpty()

    fun getDisplayString(): String {
        return dependenciesDependingOnSupportLibs.map { (directDependency, fullDependencyPath) ->
            "$directDependency (${fullDependencyPath.getPathString()})"
        }.joinToString("\n")
    }

    companion object {

        private val gson by lazy { GsonBuilder().setPrettyPrinting().create() }

        fun save(result: CheckJetifierResult, resultFile: File) {
            resultFile.writeText(gson.toJson(result))
        }

        /**
         * Loads [CheckJetifierResult] from a file, potentially throwing an exception if the data
         * can't be read (e.g., if the file does not exist or if the data format has changed
         * in an incompatible way).
         *
         * IMPORTANT: The caller of this method should ensure that the file exists and be prepared
         * to handle exceptions caused by data incompatibility.
         */
        fun load(resultFile: File): CheckJetifierResult {
            return gson
                .fromJson(resultFile.readText(), DeserializedCheckJetifierResult::class.java)
                .toOriginal()
        }

        fun aggregateResults(
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
                    secondValue != null && firstValue.getPathString() > secondValue.getPathString()
                ) {
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

    /** Fully qualified name of the project (e.g., ":app", "lib1:lib2"). */
    val projectPath: String = "Unknown",

    val configuration: String = "Unknown",

    val dependencyPath: DependencyPath
) {

    fun getPathString() = "Project '$projectPath', configuration '$configuration' -> " +
            dependencyPath.elements.joinToString(" -> ")
}

/**
 * A path from a dependency, to any intermediate dependencies, then to another dependency.
 *
 * The path contains at least 1 element.
 *
 * A dependency is described by a String (as returned by
 * `org.gradle.api.artifacts.component.ComponentIdentifier.getDisplayName()`), usually in the
 * form of "group:name:version".
 */
class DependencyPath(val elements: List<String>) {

    init {
        check(elements.isNotEmpty())
    }

    fun getPathString() = elements.joinToString(" -> ")
}

/**
 * Intermediate deserialized object that can be converted to the original object via [toOriginal].
 *
 * Motivation: To handle data backward/forward compatibility, we are using the Gson library for
 * serialization. During deserialization, the Gson library fills in missing data with default values
 * such as null/false/0, which may break the nullability of types or have other unintended effects.
 *
 * Therefore, we introduced this intermediate object between the Json data and the original object
 * to handle missing data during deserialization explicitly.
 *
 * The usage pattern is as follows:
 *
 *      class SomeType {
 *          val optionalFieldWithDefaultValue: TypeA = "someDefaultValue"
 *          val optionalFieldWithoutDefaultValue: TypeB? = null
 *          val requiredField: TypeC
 *      }
 *
 *      class DeserializedSomeType(
 *          // All fields must have default value `null` here for Gson to work,
 *          // it will be handled later in `toOriginal()`.
 *          private val optionalFieldWithDefaultValue: TypeA? = null
 *          private val optionalFieldWithoutDefaultValue: TypeB? = null
 *          private val requiredField: TypeC? = null
 *      ) : Deserialized<SomeType> {
 *
 *          override fun toOriginal(): SomeType {
 *              return SomeType(
 *                  optionalFieldWithDefaultValue ?: "someDefaultValue",
 *                  optionalFieldWithoutDefaultValue,
 *                  requiredField ?: throw RequiredFieldMissingException(...)
 *              )
 *          }
 *      }
 *
 *      fun save(data: SomeType, dataFile: File) {
 *           dataFile.writeText(gson.toJson(data))
 *      }
 *
 *      fun load(dataFile: File): SomeType {
 *          val deserialized = gson.fromJson(dataFile.readText(), DeserializedSomeType::class.java)
 *          return deserialized.toOriginal()
 *      }
 */
interface Deserialized<T> {

    /**
     * Converts this intermediate object to the original object, potentially throwing a
     * [RequiredFieldMissingException] if a required field is missing.
     */
    @Throws(RequiredFieldMissingException::class)
    fun toOriginal(): T
}

/**
 * Indicates that a required field was missing during deserialization.
 *
 * See [Deserialized.toOriginal].
 */
class RequiredFieldMissingException(
    clazz: Class<*>,
    field: String
) : RuntimeException("Required field '$field' was missing when deserializing object of type '${clazz.name}'")

private class DeserializedCheckJetifierResult(
    private val version: Double? = null,
    private val dependenciesDependingOnSupportLibs: LinkedHashMap<String, DeserializedFullDependencyPath>? = null,
) : Deserialized<CheckJetifierResult> {

    override fun toOriginal(): CheckJetifierResult {
        checkNotNull(version) {
            throw RequiredFieldMissingException(DeserializedCheckJetifierResult::class.java, "version")
        }
        checkNotNull(dependenciesDependingOnSupportLibs) {
            throw RequiredFieldMissingException(DeserializedCheckJetifierResult::class.java, "dependenciesDependingOnSupportLibs")
        }
        return CheckJetifierResult(
            version,
            dependenciesDependingOnSupportLibs.mapValuesTo(LinkedHashMap()) { it.value.toOriginal() })
    }
}

private class DeserializedFullDependencyPath(
    private val projectPath: String? = null,
    private val configuration: String? = null,
    private val dependencyPath: DeserializedDependencyPath? = null
) : Deserialized<FullDependencyPath> {

    override fun toOriginal() = FullDependencyPath(
        projectPath ?: "Unknown",
        configuration ?: "Unknown",
        dependencyPath?.toOriginal() ?: throw RequiredFieldMissingException(
            DeserializedFullDependencyPath::class.java, "dependencyPath"
        )
    )
}

private class DeserializedDependencyPath(private val elements: List<String>? = null) : Deserialized<DependencyPath> {

    override fun toOriginal() = elements?.let { DependencyPath(it) }
        ?: throw RequiredFieldMissingException(DeserializedDependencyPath::class.java, "elements")
}
