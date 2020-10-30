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

import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.options.StringOption
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.internal.artifacts.ArtifactAttributes
import java.io.File

/**
 * To update:
 *
 * 1. Update this version string.
 * 2. Run bazel run //tools/base/bazel:add_dependency com.google.prefab:cli:jar:all:$VERSION
 * 3. Add the following block to
 *    //prebuilts/tools/common/repository/m2/com/google/prefab/cli/$VERSION/BUILD:
 *
 *    maven_java_import(
 *        name = "jar",
 *        jars = ["cli-$VERSION-all.jar"],
 *        pom = ":pom",
 *        visibility = ["//visibility:public"],
 *    )
 *
 *    The pom target should already exist, but the add_dependency tool doesn't handle the -all JAR
 *    properly. We need to manually add this target to the BUILD file until that is fixed.
 *
 *    See http://b/146079078 for more information.
 *
 * 4. Update the "prebuilts" maven_repo target in
 *    //tools/base/build-system/integration-test/native/BUILD.bazel with the new version number.
 */
private const val DEFAULT_PREFAB_VERSION = "1.0.0"
private const val PREFAB_CONFIG_NAME = "_internal_prefab_binary"

private fun getPrefabArtifact(configuration: Configuration): File =
    configuration.incoming.artifactView { config ->
        config.attributes {
            it.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                ArtifactTypeDefinition.JAR_TYPE
            )
        }
    }.artifacts.artifactFiles.singleFile

fun getPrefabFromMaven(globalScope: GlobalScope): File {
    val project = globalScope.project

    globalScope.projectOptions[StringOption.PREFAB_CLASSPATH]?.let {
        return project.file(it)
    }

    project.configurations.findByName(PREFAB_CONFIG_NAME)?.let {
        return getPrefabArtifact(it)
    }

    val config = project.configurations.create(PREFAB_CONFIG_NAME) {
        it.isVisible = false
        it.isTransitive = false
        it.isCanBeConsumed = false
        it.description = "The Prefab tool to use for generating native build system bindings."
    }

    val version = globalScope.projectOptions[StringOption.PREFAB_VERSION] ?: DEFAULT_PREFAB_VERSION
    project.dependencies.add(
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
