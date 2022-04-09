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

package com.android.build.gradle.internal.utils

import com.android.build.api.dsl.Optimization
import com.android.build.gradle.internal.LoggerWrapper
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection

/**
 * Filter out extracted library proguard rules at execution time based on users' input from
 * [Optimization.keepRules]
 */
fun getFilteredConfigurationFiles(
        ignoreList: Set<String>,
        ignoreAll: Boolean,
        libraryKeepRules: ArtifactCollection,
        configurationFiles: FileCollection,
        logger: LoggerWrapper
) : FileCollection {
    val matchedArtifacts = mutableSetOf<String>()

    val ignoredArtifacts = libraryKeepRules.artifacts.asSequence()
            // Only external dependencies are considered to be ignored
            .filter { it.id.componentIdentifier is ModuleComponentIdentifier }
            .filter { artifact ->
                var toIgnore = ignoreAll
                if (!toIgnore) {
                    findMatchedArtifact(
                            artifact.id.componentIdentifier as ModuleComponentIdentifier,
                            ignoreList
                    )?.let {
                        matchedArtifacts.add(it)
                        toIgnore = true
                    }
                }
                toIgnore
            }.map { it.file }.toSet()

    val unmatchedIgnoreList = ignoreList.filterNot { matchedArtifacts.contains(it) }
    if (unmatchedIgnoreList.isNotEmpty()) {
        logger.warning("Keep rules from $unmatchedIgnoreList are specified to be ignored, " +
                "but we couldn't recognize them or find them in the project dependencies " +
                "list. Note we only allow users to ignore keep rules from remote library " +
                "dependencies.")
    }

    return configurationFiles.filter {
        !ignoredArtifacts.contains(it)
    }
}

private fun findMatchedArtifact(
        artifactId: ModuleComponentIdentifier,
        ignoreList: Set<String>
) : String? {
    if (ignoreList.contains(artifactId.toString())) {
        return artifactId.toString()
    }
    // Version wildcard matching
    val idWithoutVersion = "${artifactId.group}:${artifactId.module}"
    return ignoreList.find { idWithoutVersion == it }
}
