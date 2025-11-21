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

package com.android.build.gradle.internal.tasks

import com.android.builder.errors.IssueReporter
import com.google.common.collect.ImmutableMap
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import java.util.ArrayList

internal fun populateAssetPacksConfigurations(
    project: Project,
    issueReporter: IssueReporter,
    assetPacks: Set<String>,
    assetPackFilesConfiguration: Configuration,
    assetPackManifestConfiguration: Configuration
) {
    val depHandler = project.dependencies
    val notFound: MutableList<String> =
        ArrayList()
    for (assetPack in assetPacks) {
        if (project.findProject(assetPack) != null) {
            val filesDependency = ImmutableMap.of(
                "path",
                assetPack,
                "configuration",
                "packElements"
            )
            depHandler.add(assetPackFilesConfiguration.name, depHandler.project(filesDependency))
            val manifestDependency = ImmutableMap.of(
                "path",
                assetPack,
                "configuration",
                "manifestElements"
            )
            depHandler.add(
                assetPackManifestConfiguration.name,
                depHandler.project(manifestDependency)
            )
        } else {
            notFound.add(assetPack)
        }
    }
    if (!notFound.isEmpty()) {
        issueReporter.reportError(
            IssueReporter.Type.GENERIC,
            "Unable to find matching projects for Asset Packs: $notFound"
        )
    }
}
