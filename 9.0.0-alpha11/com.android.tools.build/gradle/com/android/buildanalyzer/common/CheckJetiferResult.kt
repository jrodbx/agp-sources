/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.buildanalyzer.common

import com.google.gson.GsonBuilder
import java.io.File
import java.util.SortedMap

/**
 * Aggregated over all projects result of the `CheckJetifier` task.
 *
 * It contains information about any direct dependencies that projects use which
 * directly/transitively depend on support libraries, or are support libraries themselves (see
 * [dependenciesDependingOnSupportLibs]).
 */
data class CheckJetifierResult(

    /**
     * A map where the key is a direct dependency that a project uses and the value is a
     * list of [FullDependencyPath] from a configuration of the project to the direct dependency, to any
     * intermediate dependencies, then to a support library. List contains [FullDependencyPath] for
     * each project where it was detected.
     *
     * The direct dependency may be a support library itself.
     *
     * There may be multiple paths to and from the direct dependency, but this class retains only
     * one path per project.
     *
     * A dependency is described by a String (as returned by
     * `org.gradle.api.artifacts.component.ComponentIdentifier.getDisplayName()`), usually in the
     * form of "group:name:version".
     */
    val dependenciesDependingOnSupportLibs: SortedMap<String, List<FullDependencyPath>>
) {

    fun isEmpty() = dependenciesDependingOnSupportLibs.isEmpty()

    fun getDisplayString(): String {
        return dependenciesDependingOnSupportLibs.flatMap { (directDependency, fullDependencyPath) ->
            listOf(directDependency) + fullDependencyPath.map { "    ${it.getPathString()}" }
        }.joinToString("\n")
    }

    companion object {

        private val gson by lazy { GsonBuilder().setPrettyPrinting().create() }

        fun save(result: CheckJetifierResult, resultFile: File) {
            resultFile.writeText(
                gson.toJson(
                    CheckJetiferResultFile(
                        resultData = result
                    )
                )
            )
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
                .fromJson(resultFile.readText(), DeserializedCheckJetifierResultFile::class.java)
                .toOriginal()
                .resultData
        }

        fun aggregateProjectResults(
            projectResults: List<CheckJetifierProjectResult>
        ): CheckJetifierResult = CheckJetifierResult(
            projectResults.flatMap { it.dependenciesDependingOnSupportLibs.entries }
                .groupBy { it.key }
                .mapValues { (_, entriesList) ->
                    entriesList.map { (_, fullDependencyPath) -> fullDependencyPath }
                        .distinct()
                        .sortedBy { it.getPathString() }
                }
                .toSortedMap()
        )

        fun aggregateResults(
            first: CheckJetifierResult,
            second: CheckJetifierResult
        ): CheckJetifierResult {
            val combinedResult = HashMap<String, List<FullDependencyPath>>(
                first.dependenciesDependingOnSupportLibs.size + second.dependenciesDependingOnSupportLibs.size
            )
            for (key in first.dependenciesDependingOnSupportLibs.keys + second.dependenciesDependingOnSupportLibs.keys) {
                val firstValue = first.dependenciesDependingOnSupportLibs[key] ?: emptyList()
                val secondValue = second.dependenciesDependingOnSupportLibs[key] ?: emptyList()
                val allPaths = (firstValue + secondValue).distinct().sortedBy { it.getPathString() }
                combinedResult[key] = allPaths
            }
            return CheckJetifierResult(combinedResult.toSortedMap())
        }
    }
}

/**
 * Result of the `CheckJetifier` task for one project.
 *
 * It contains information about any direct dependencies that projects use which
 * directly/transitively depend on support libraries, or are support libraries themselves (see
 * [dependenciesDependingOnSupportLibs]).
 *
 * These project results are used to print result per project during execution an
 * aggregated to a single [CheckJetifierResult] in [CheckJetifierResult.aggregateProjectResults].
 *
 * This class is used only during execution for results aggregation and is not serialized to
 * the results json file.
 */
data class CheckJetifierProjectResult(

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

    fun isEmpty() = dependenciesDependingOnSupportLibs.isEmpty()

    fun getDisplayString(): String {
        return dependenciesDependingOnSupportLibs.map { (directDependency, fullDependencyPath) ->
            "$directDependency (${fullDependencyPath.getPathString()})"
        }.joinToString("\n")
    }
}

/**
 * A path from a configuration of a project to a direct dependency, to any intermediate
 * dependencies, then to another dependency.
 */
data class FullDependencyPath(

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
data class DependencyPath(val elements: List<String>) {

    init {
        check(elements.isNotEmpty())
    }

    fun getPathString() = elements.joinToString(" -> ")
}

data class CheckJetiferResultFile(
    val version: Double = VERSION,
    val resultData: CheckJetifierResult
) {

    companion object {

        const val VERSION: Double = 2.0
    }
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

class UnknownFileVersionException(
    version: Double?
) : RuntimeException("Unknown version '$version' met when deserializing checkJetifier result file.")

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

private data class DeserializedCheckJetifierResultFile(
    private var version: Double? = null,
    private var dependenciesDependingOnSupportLibs: Map<String, DeserializedFullDependencyPath>? = null,
    private var resultData: DeserializedCheckJetifierResultV2? = null,
) : Deserialized<CheckJetiferResultFile> {

    override fun toOriginal(): CheckJetiferResultFile {
        val resultData = when (version) {
            1.0 -> convertFromV1Format()
            2.0 -> convertFromV2Format()
            else -> throw UnknownFileVersionException(version)
        }
        return CheckJetiferResultFile(version!!, resultData)
    }

    private fun convertFromV2Format(): CheckJetifierResult {
        return resultData?.toOriginal() ?: throw RequiredFieldMissingException(
            DeserializedCheckJetifierResultFile::class.java,
            "resultData"
        )
    }

    private fun convertFromV1Format(): CheckJetifierResult {
        return dependenciesDependingOnSupportLibs?.let {
            CheckJetifierResult(it.mapValues { listOf(it.value.toOriginal()) }.toSortedMap())
        } ?: throw RequiredFieldMissingException(
            DeserializedCheckJetifierResultFile::class.java,
            "dependenciesDependingOnSupportLibs"
        )
    }
}

private class DeserializedCheckJetifierResultV2(
    private val dependenciesDependingOnSupportLibs: Map<String, List<DeserializedFullDependencyPath>>? = null
) : Deserialized<CheckJetifierResult> {

    override fun toOriginal(): CheckJetifierResult {
        return dependenciesDependingOnSupportLibs?.let {
            CheckJetifierResult(
                it.mapValues { (_, paths) -> paths.map { it.toOriginal() } }.toSortedMap()
            )
        } ?: throw RequiredFieldMissingException(
            DeserializedCheckJetifierResultV2::class.java,
            "dependenciesDependingOnSupportLibs"
        )
    }
}
