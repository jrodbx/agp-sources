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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.file.FileCollection

/**
 * To update:
 *
 * 1. Update this version string.
 * 2. Follow the steps to fetch new maven dependencies in tools/base/bazel/README.md
 * 3. Update all @maven//:com.google.prefab.cli_$VERSION in all BUILD files
 * 4. Optionally, delete the BUILD modules and prebuilts for the old version of Prefab. This should
 *    be done unless AGP will continue testing both the new and old version (uncommon).
 */
const val DEFAULT_PREFAB_VERSION = "2.1.0"
private const val PREFAB_CONFIG_NAME = "_internal_prefab_binary"

private fun getPrefabArtifact(configuration: Configuration): FileCollection =
    configuration.incoming.artifactView { config ->
        config.attributes {
            it.attribute(
                ARTIFACT_TYPE_ATTRIBUTE,
                ArtifactTypeDefinition.JAR_TYPE
            )
        }
    }.artifacts.artifactFiles

fun getPrefabFromMaven(
    projectOptions: ProjectOptions,
    services: TaskCreationServices
): FileCollection {

    projectOptions[StringOption.PREFAB_CLASSPATH]?.let {
        return services.files(it)
    }

    services.configurations.findByName(PREFAB_CONFIG_NAME)?.let {
        return getPrefabArtifact(it)
    }

    val config = services.configurations.create(PREFAB_CONFIG_NAME) {
        it.isVisible = false
        it.isTransitive = false
        it.isCanBeConsumed = false
        it.description = "The Prefab tool to use for generating native build system bindings."
    }

    val version = projectOptions[StringOption.PREFAB_VERSION] ?: DEFAULT_PREFAB_VERSION
    services.dependencies.add(
        config.name,
        mapOf(
            "group" to "com.google.prefab",
            "name" to "cli",
            "classifier" to "all",
            "version" to version
        )
    )

    return getPrefabArtifact(config)
}
