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
@file:JvmName("ConfigTableUtil")
package com.android.projectmodel

/**
 * A config table holds the set of [Config] instances for an [AndroidSubmodule] and describes how they
 * are shared among build types, [Artifact] instances, and [Variant] instances.
 *
 * New properties may be added in the future; clients that invoke the constructor are encouraged to
 * use Kotlin named arguments to stay source compatible.
 */
data class ConfigTable(
        /**
         * Describes the dimensions of this matrix, the names each dimension, and the possible
         * values for each dimension.
         */
        val schema: ConfigTableSchema = ConfigTableSchema(),
        /**
         * Holds the association of the project's [Config] instances with the [ConfigPath] they apply to.
         * The list is ordered. [Config] instances later in the list take precedence over earlier ones.
         */
        val associations: List<ConfigAssociation> = emptyList()
) {
    /**
     * Returns all [Config] instances in this table.
     */
    val configs: List<Config>
        get() = associations.map { it.config }

    /**
     * Returns the list of [Config] instances that have any intersection with the table region
     * described by the given path.
     *
     * For example, if given the path to a [Variant], it will return all [Config] instances that
     * have any possibility of being used by an [Artifact] in that [Variant]. It will include
     * [Config] instances that only apply to specific [Artifact] instances, but will exclude
     * [Config] instances that don't apply to the [Variant] at all.
     *
     * If given the path to an [Artifact], it will return all the [Config] instances for that
     * [Artifact].
     */
    fun configsIntersecting(searchCriteria: ConfigPath): List<Config> =
            filterIntersecting(searchCriteria).configs

    /**
     * Returns the list of [Config] instances that have any intersection with the given path.
     *
     * For example, if given the path to a [Variant], it will return all [Config] instances that
     * have any possibility of being used by an [Artifact] in that [Variant]. It will include
     * [Config] instances that only apply to specific [Artifact] instances, but will exclude
     * [Config] instances that don't apply to the [Variant] at all.
     *
     * If given the path to an [Artifact], it will return all the [Config] instances for that
     * [Artifact].
     */
    fun configsIntersecting(searchCriteria: SubmodulePath): List<Config> =
        filterIntersecting(searchCriteria.toConfigPath()).configs

    /**
     * Returns the list of [Config] instances that do not apply to any [Artifact] within the table
     * region described by the given [ConfigPath].
     *
     * For example, if given the path to a [Variant], it will return all [Config] instances
     * that are not used by any [Artifact] with that [Variant].
     */
    fun configsNotIntersecting(searchCriteria: ConfigPath): List<Config> =
            filterNotIntersecting(searchCriteria).configs

    /**
     * Returns the [ConfigTable] containing the [Config] instances from this table that pass the given
     * filter.
     */
    inline fun filter(func: (ConfigAssociation)-> Boolean): ConfigTable {
        return ConfigTable(schema, associations.filter(func))
    }

    /**
     * Returns the [ConfigTable] containing the [Config] instances from this table that intersect
     * the table region described by [searchCriteria].
     */
    fun filterIntersecting(searchCriteria: ConfigPath): ConfigTable {
        return ConfigTable(schema, if (searchCriteria.matchesEverything) associations
        else associations.filter {
            it.path.intersects(searchCriteria)
        })
    }

    /**
     * Returns the [ConfigTable] containing the [Config] instances from this table that do not
     * intersect the table region described by [searchCriteria].
     */
    fun filterNotIntersecting(searchCriteria: ConfigPath): ConfigTable {
        return ConfigTable(schema, associations.filter {
            !it.path.intersects(searchCriteria)
        })
    }

    /**
     * Generates all possible [Artifact] instances for this [ConfigTable] by merging every
     * combination of schema dimensions.
     */
    fun generateArtifacts(): Map<SubmodulePath, Artifact> =
        schema.allArtifactPaths().associate { it to Artifact(
            // We can rely on segments being non-null since allPaths returns the paths to artifacts,
            // and null segments indicates a path that never matches any artifact. We can rely on
            // there being at least one segment because there needs to be at least one dimension
            // in order for allArtifactPaths() to return a non-empty sequence and the number of segments
            // equals the number of dimensions.
            resolved = configsIntersecting(it.toConfigPath()).merged()
        )}
}

/**
 * Trivial schema for a table containing exactly one variant and one artifact. Note that the
 * main artifact name is required to be [ARTIFACT_NAME_MAIN], so all trivial schemas will always
 * look exactly like this.
 */
internal val TRIVIAL_SCHEMA = ConfigTableSchema(
    listOf(
        ConfigDimension(
            ARTIFACT_DIMENSION_NAME,
            listOf(ARTIFACT_NAME_MAIN)
        )
    )
)

/**
 * Path to the main artifact in a [TRIVIAL_SCHEMA].
 */
internal val TRIVIAL_MAIN_ARTIFACT_PATH = matchArtifactsWith(listOf(ARTIFACT_NAME_MAIN))

/**
 * Creates a [ConfigTable] for a project containing exactly one variant of one artifact, given
 * the resolved [Config] of that artifact. The main artifact is always required to be named
 * [ARTIFACT_NAME_MAIN], so the schema and artifact paths for trivial [ConfigTable] instances is
 * always the same.
 *
 * This is a convenience method for constructing such trivial instances. In addition to saving
 * boilerplate, it also saves memory by reusing the same schema instance for all trivial
 * [ConfigTable] instances.
 *
 */
fun configTableWith(config: Config) = ConfigTable(
    TRIVIAL_SCHEMA,
    listOf(
        ConfigAssociation(
            TRIVIAL_MAIN_ARTIFACT_PATH,
            config
        )
    )
)

/**
 * Constructs a [ConfigTable] from the given [ConfigTableSchema]. This is intended primarily
 * as a convenient way to construct hardcoded [ConfigTable] instances from Java, in cases where
 * there may be more than one variant or artifact.
 */
fun configTableWith(schema: ConfigTableSchema, associations: Map<String?, Config>) = ConfigTable(
    schema, associations.map { ConfigAssociation(schema.pathFor(it.key), it.value)}
)

/**
 * Constructs a [ConfigTable] from the given [ConfigTableSchema]. This is intended primarily
 * as a convenient way to construct hardcoded [ConfigTable] instances from Kotlin.
 *
 * @param associations the entries to include in the table, where the keys map onto entries
 * in the schema.
 */
fun ConfigTableSchema.buildTable(vararg associations: Pair<String?, Config>) = ConfigTable(
    this, associations.map { ConfigAssociation(this.pathFor(it.first), it.second) })
