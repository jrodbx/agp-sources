/*
 * Copyright (C) 2017 The Android Open Source Project
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

@file:JvmName("ConfigTableSchemaUtil")

package com.android.projectmodel

/**
 * Describes the schema for a [ConfigTable]. Specifically, it describes the set of dimensions
 * and the allowable values for [ConfigPath] instances along each dimension. For example, in
 * the case of Gradle projects the first dimensions correspond to flavors, if any, and the second-last
 * dimension corresponds to build type. For all build systems, the last dimension always corresponds
 * to an artifact name.
 */
data class ConfigTableSchema(
    /**
     * Dimensions for the table.
     */
    val dimensions: List<ConfigDimension> = listOf(defaultArtifactDimension)
) {
    init {
        if (dimensions.isEmpty() || !dimensions.last().values.contains(ARTIFACT_NAME_MAIN)) {
            throw IllegalArgumentException("The main artifact must be present in the config table")
        }
    }

    /**
     * Returns a [ConfigPath] that matches all [Config] instances that use the given
     * [dimensionValue]. If dimensionValue is null, the resulting path matches all
     * artifacts.
     */
    fun pathFor(dimensionValue: String?): ConfigPath {
        // TODO: Allow dimensionValue to be any ConfigPath simpleName. This would allow construction
        // of more elaborate multidimensional test cases using this utility method.
        dimensionValue ?: return matchAllArtifacts()
        val index = dimensions.indexOfFirst { it.values.contains(dimensionValue) }
        if (index == -1) {
            return matchNoArtifacts()
        }
        return matchDimension(index, dimensionValue)
    }

    /**
     * Returns true iff the given [SubmodulePath] is valid in this schema.
     */
    fun isValid(toTest: SubmodulePath) = isValid(toTest.toConfigPath())

    /**
     * Returns true iff the given [ConfigPath] is valid in this schema.
     */
    fun isValid(toTest: ConfigPath): Boolean {
        val segments = toTest.segments ?: return false
        for (index in 0 until (Math.min(segments.size, dimensions.size))) {
            val dim = dimensions[index]
            val nextSeg = segments[index]
            if (nextSeg != null && !dim.containsValue(nextSeg)) {
                return false
            }
        }
        return true
    }

    /**
     * Returns a [ConfigPath] that matches an artifact name, which is always stored as the last
     * segment of the path.
     */
    fun matchArtifact(artifactName: String): ConfigPath {
        return matchDimension(dimensions.size - 1, artifactName)
    }

    /**
     * Returns a sequence containing every [SubmodulePath] for an [Artifact] in this schema that
     * passes the given filter.
     */
    @JvmOverloads
    fun allArtifactPaths(filter: ConfigPath = matchAllArtifacts()) =
        allPathsOfLength(dimensions.size, filter)

    /**
     * Returns a sequence containing every [SubmodulePath] for a [Variant] in this schema that
     * passes the given filter.
     */
    @JvmOverloads
    fun allVariantPaths(filter: ConfigPath = matchAllArtifacts()) =
        allPathsOfLength(dimensions.size - 1, filter)

    /**
     * Returns a sequence containing every valid [SubmodulePath] prefix in this schema with the given
     * length that matches the given [ConfigPath].
     */
    private fun allPathsOfLength(desiredPathLength: Int, filter: ConfigPath): Sequence<SubmodulePath> {
        val filterList = filter.segments ?: return emptySequence()
        return if (desiredPathLength > dimensions.size)
            throw IllegalArgumentException("desiredPathLength $desiredPathLength must not be larger than the number of dimensions (${dimensions.size})")
        else allPathsOfLength(desiredPathLength, emptyList(), filterList)
    }

    private fun allPathsOfLength(
        desiredPathLength: Int,
        prefix: List<String>,
        filter: List<String?>
    ): Sequence<SubmodulePath> {
        return when {
            prefix.size == desiredPathLength ->
                sequenceOf(
                    submodulePathOf(
                        prefix
                    )
                )
            prefix.size < desiredPathLength -> dimensions[prefix.size].values.asSequence().flatMap {
                val filterAtPosition = if (filter.size <= prefix.size) null else filter[prefix.size]
                if (filterAtPosition != null && filterAtPosition != it) {
                    emptySequence()
                } else
                    allPathsOfLength(
                        desiredPathLength,
                        prefix + it,
                        filter
                    )
            }
            else -> emptySequence()
        }
    }

    override fun toString()
        = "ConfigTableSchema(${dimensions.joinToString(",") {"${it.dimensionName}[${it.values.joinToString(",")}]"}})"

    class Builder {
        private val dimensions = ArrayList<ConfigDimension.Builder>()
        private val nameToDimension = HashMap<String, ConfigDimension.Builder>()

        fun getOrPutDimension(name: String): ConfigDimension.Builder {
            return nameToDimension.getOrPut(name) {
                val builder = ConfigDimension.Builder(name)
                dimensions.add(builder)
                builder
            }
        }

        fun build(): ConfigTableSchema = ConfigTableSchema(dimensions.map { it.build() })
    }
}

/**
 * Name of the dimension that identifies the artifact.
 */
const val ARTIFACT_DIMENSION_NAME = "artifact"

/**
 * Default last dimension for a config table. It contains the default three artifacts for each
 * variant (a main artifact, a unit test artifact, and an android test artifact).
 */
val defaultArtifactDimension = ConfigDimension(
    ARTIFACT_DIMENSION_NAME, listOf(
        ARTIFACT_NAME_MAIN,
        ARTIFACT_NAME_UNIT_TEST,
        ARTIFACT_NAME_ANDROID_TEST
    )
)

/**
 * Construct a [ConfigTableSchema] from a vararg list of pairs. Intended primarily for providing
 * a concise syntax for hardcoded schema creation in unit tests. Schemas constructed this way always
 * use the default
 */
fun configTableSchemaWith(vararg dimensions: Pair<String, List<String>>): ConfigTableSchema {
    return ConfigTableSchema(dimensions.map {
        ConfigDimension(
            it.first,
            it.second
        )
    } + defaultArtifactDimension)
}
