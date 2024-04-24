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
 * 2. Update the artifact entry under toplevel.WORKSPACE file for
 *    com.google.prefab:cli:jar:all:$VERSION to the new version.
 * 3. Add the following block to //prebuilts/tools/common/m2/BUILD:
 *
 *    maven_java_import(
 *        name = "jar",
 *        classified_only = True,
 *        classifiers = ["all"],
 *        jars = ["repository/com/google/prefab/cli/$VERSION/cli-$VERSION-all.jar"],
 *        pom = ":com.google.prefab.cli.$VERSION_pom",
 *        repo_path = "repository/com/google/prefab/cli/$VERSION",
 *        visibility = ["//visibility:public"],
 *    )
 *
 *    The pom target should already exist, but the add_dependency tool doesn't handle the -all JAR
 *    properly. We need to manually add this target to the BUILD file until that is fixed.
 *
 *    See http://b/146079078 for more information.
 *
 * 4. Update the "prebuilts" maven_repo target in
 *    //tools/base/build-system/integration-test/BUILD.bazel with the new version number.
 *
 * 5. Optionally, delete the BUILD modules and prebuilts for the old version of Prefab. This should
 *    be done unless AGP will continue testing both the new and old version (uncommon).
 */
const val DEFAULT_PREFAB_VERSION = "2.0.0"
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
