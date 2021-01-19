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

import com.android.build.gradle.internal.ide.DependencyFailureHandler
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.options.BooleanOption
import org.gradle.api.Action
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.ArtifactView.ViewConfiguration
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.FileCollection

/**
 * Gets the lint JAR from the lint checking configuration.
 *
 * @return the resolved lint.jar artifact files from the lint checking configuration
 */
fun getLocalCustomLintChecks(lintChecks: Configuration, dslServices: DslServices, projectPath: String): FileCollection {

    val lenientMode = dslServices.projectOptions[BooleanOption.IDE_BUILD_MODEL_ONLY]

    // Query for JAR instead of PROCESSED_JAR as we want to get the original lint.jar
    val attributes =
        Action { container: AttributeContainer ->
            container.attribute(
                AndroidArtifacts.ARTIFACT_TYPE, AndroidArtifacts.ArtifactType.JAR.type
            )
        }
    val artifactCollection: ArtifactCollection =
        lintChecks.incoming.artifactView { config: ViewConfiguration ->
            config.attributes(attributes)
            config.lenient(lenientMode)
        }
        .artifacts
    if (lenientMode) {
        val failures = artifactCollection.failures
        if (!failures.isEmpty()) {
            val failureHandler = DependencyFailureHandler()
            failureHandler.addErrors(projectPath + "/" + lintChecks.name, failures)
            failureHandler.registerIssues(dslServices.issueReporter)
        }
    }
    return artifactCollection.artifactFiles
}
