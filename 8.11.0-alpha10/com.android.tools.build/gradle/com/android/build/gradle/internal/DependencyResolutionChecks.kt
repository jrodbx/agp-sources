/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal

import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.google.common.base.Throwables
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Adds a check to ensure dependencies are not resolved during configuration phase.
 *
 * See https://github.com/gradle/gradle/issues/2298
 */
fun registerDependencyCheck(
    project: Project,
    projectOptions: ProjectOptions
) {
    if (skipDependencyCheck(projectOptions)) {
        return
    }

    val isResolutionAllowed = AtomicBoolean(false)
    when {
        // There are 3 main sub-phases of interest in the configuration phase:
        //    1. Evaluation of all projects, which includes evaluation of the current project
        //    2. Task graph creation
        //    3. Configuration cache store time

        // If configuration-on-demand is enabled and there are included builds (b/154948828),
        // configurations may be resolved before all projects are evaluated but should not be
        // resolved during the current project's evaluation.
        project.gradle.startParameter.isConfigureOnDemand && project.gradle.includedBuilds.isNotEmpty() -> {
            project.afterEvaluate { isResolutionAllowed.set(true) }
        }

        // If this project is part of a composite build, configurations may be resolved during task
        // graph creation but should not be resolved during all projects' evaluation.
        project.gradle.parent != null || project.gradle.includedBuilds.isNotEmpty() -> {
            project.gradle.projectsEvaluated { isResolutionAllowed.set(true) }
        }

        // In all other cases, configurations may be resolved during configuration cache store time
        // or execution time but should not be resolved during all projects' evaluation and task
        // graph creation
        else -> project.gradle.taskGraph.whenReady { isResolutionAllowed.set(true) }
    }

    val failIfResolvedEarly = projectOptions[BooleanOption.DISALLOW_DEPENDENCY_RESOLUTION_AT_CONFIGURATION]

    project.configurations.configureEach { configuration ->
        configuration.incoming.beforeResolve {
            if (isResolutionAllowed.get()) {
                return@beforeResolve
            }
            if (configuration.name == "classpath") {
                return@beforeResolve
            }

            val errorMessage = errorMessage(configurationName = configuration.name)
            if (failIfResolvedEarly) {
                error(errorMessage)
            } else {
                project.logger.warn("$errorMessage\nRun with --info for a stacktrace.")
                // TODO b/80230357: Heuristically sanitized stacktrace to show what triggered the resolution.
                if (project.logger.isEnabled(LogLevel.INFO)) {
                    project.logger.info(
                        Throwables.getStackTraceAsString(RuntimeException(errorMessage))
                    )
                }
            }
        }
    }
}

private fun skipDependencyCheck(projectOptions: ProjectOptions): Boolean {
    return projectOptions[BooleanOption.IDE_BUILD_MODEL_ONLY]
        || projectOptions[BooleanOption.IDE_BUILD_MODEL_ONLY_V2]
}

private fun errorMessage(configurationName: String): String {
    return """
    Configuration '$configurationName' was resolved during configuration time.
    This is a build performance and scalability issue.
    See https://github.com/gradle/gradle/issues/2298
    """.trimIndent()
}
