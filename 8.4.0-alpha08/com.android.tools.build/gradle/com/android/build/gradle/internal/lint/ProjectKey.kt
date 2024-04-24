/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.lint

import com.android.build.gradle.internal.ide.dependencies.getVariantName
import com.android.build.gradle.internal.ide.dependencies.hasProjectTestFixturesCapability
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import java.io.File

internal data class ProjectKey(val buildTreePath: String, val variantName: String?) {

    override fun toString(): String {
        return StringBuilder().apply {
            append(buildTreePath)
            if (variantName != null) {
                append(" (").append(variantName).append(")")
            }
        }.toString()
    }
}

internal fun asProjectKey(artifact: ResolvedArtifactResult): ProjectKey {
    val id = artifact.id.componentIdentifier as ProjectComponentIdentifier
    return ProjectKey(id.buildTreePath, artifact.getVariantName())
}

internal fun ArtifactCollection.asProjectKeyedMap(): Map<ProjectKey, File> {
    return artifacts.asSequence().map { artifact -> asProjectKey(artifact) to artifact.file}.toMap()
}

/**
 * This is used to differentiate between main and testFixtures artifacts in
 * [ExternalLintModelArtifactHandler] where the artifacts are cached based on the project key
 * and so the project key needs to be different in that case.
 * TODO: Remove when the non checkDependencies code path is removed.
 */
internal data class ProjectSourceSetKey(
    val buildTreePath: String,
    val variantName: String?,
    val isTestFixtures: Boolean = false
) {

    override fun toString(): String {
        return StringBuilder().apply {
            append(buildTreePath)
            if (isTestFixtures) {
                append(" (testFixtures)")
            }
            if (variantName != null) {
                append(" (").append(variantName).append(")")
            }
        }.toString()
    }
}

internal fun asProjectSourceSetKey(artifact: ResolvedArtifactResult): ProjectSourceSetKey {
    val id = artifact.id.componentIdentifier as ProjectComponentIdentifier
    return ProjectSourceSetKey(
        id.buildTreePath,
        artifact.getVariantName(),
        artifact.hasProjectTestFixturesCapability()
    )
}

internal fun ArtifactCollection.asProjectSourceSetKeyedMap(): Map<ProjectSourceSetKey, File> {
    return artifacts.asSequence().map { artifact -> asProjectSourceSetKey(artifact) to artifact.file}.toMap()
}

