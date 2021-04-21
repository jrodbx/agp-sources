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

import com.android.build.gradle.internal.ide.dependencies.BuildMapping
import com.android.build.gradle.internal.ide.dependencies.getBuildId
import com.android.build.gradle.internal.ide.dependencies.getVariantName
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import java.io.File
import java.lang.StringBuilder

internal data class ProjectKey(val buildId: String, val projectPath: String, val variantName: String?) {

    override fun toString(): String {
        return StringBuilder().apply {
            append(buildId)
            append(" ")
            append(projectPath)
            if (variantName != null) {
                append(" (").append(variantName).append(")")
            }
        }.toString()
    }
}

internal fun asProjectKey(buildMapping: BuildMapping, artifact: ResolvedArtifactResult): ProjectKey {
    val id = artifact.id.componentIdentifier as ProjectComponentIdentifier
    return ProjectKey(id.getBuildId(buildMapping)!!, id.projectPath, artifact.getVariantName())
}

internal fun ArtifactCollection.asProjectKeyedMap(buildMapping: BuildMapping): Map<ProjectKey, File> {
    return artifacts.asSequence().map { artifact -> asProjectKey(buildMapping, artifact) to artifact.file}.toMap()
}
