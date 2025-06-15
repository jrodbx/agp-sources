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

@file:JvmName("CustomLintCheckUtils")
package com.android.build.gradle.internal.lint

import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.ide.DependencyFailureHandler
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.builder.errors.IssueReporter
import com.google.common.collect.ImmutableList
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.ArtifactView.ViewConfiguration
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.FileCollection
import java.io.File

/**
 * Gets the lint JARs for the model. Resolves the configuration (leniently) and reports any failures
 */
fun getLocalCustomLintChecksForModel(
    project: Project,
    issueReporter: IssueReporter,
): ImmutableList<File> {
    val lintChecks = project.configurations.getByName(VariantDependencies.CONFIG_NAME_LINTCHECKS)
    val artifactCollection = getLocalCustomLintChecks(lintChecks, lenientMode = true)
    val failures = artifactCollection.failures
    if (!failures.isEmpty()) {
        val failureHandler = DependencyFailureHandler()
        failureHandler.addErrors(project.path + "/" + lintChecks.name, failures)
        failureHandler.registerIssues(issueReporter)
    }
    return ImmutableList.copyOf(artifactCollection.artifactFiles.files)
}

/**
 * Gets the custom local lint checks for task dependencies.
 */
fun getLocalCustomLintChecks(lintChecks: Configuration): FileCollection {
    return getLocalCustomLintChecks(lintChecks, lenientMode = false).artifactFiles
}

private fun getLocalCustomLintChecks(lintChecks: Configuration, lenientMode: Boolean): ArtifactCollection {
    return lintChecks.incoming.artifactView { config: ViewConfiguration ->
        config.attributes { attributes: AttributeContainer ->
            // Query for JAR instead of PROCESSED_JAR as lint.jar doesn't need processing
            attributes.attribute(
                AndroidArtifacts.ARTIFACT_TYPE,
                AndroidArtifacts.ArtifactType.JAR.type
            )
        }
        config.lenient(lenientMode)
    }.artifacts
}
