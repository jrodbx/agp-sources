/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.api.artifact.impl.InternalScopedArtifacts
import com.android.build.gradle.internal.component.HostTestCreationConfig
import com.android.build.gradle.internal.res.GenerateLibraryRFileTask
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin

class UnitTestTaskManager(
    project: Project,
    globalConfig: GlobalTaskCreationConfig
): HostTestTaskManager(project, globalConfig) {

    fun createTopLevelTasks() {
        // Create top level unit test tasks.
        super.createTopLevelTasksCore(
            globalConfig.taskNames.test,
            "Run unit tests for all variants."
        )
    }

    /** Creates the tasks to build unit tests.  */
    fun createTasks(hostTestCreationConfig: HostTestCreationConfig) {
        val taskContainer = hostTestCreationConfig.taskContainer
        val testedVariant = hostTestCreationConfig.mainVariant
        val includeAndroidResources = globalConfig.unitTestOptions.isIncludeAndroidResources
        createAnchorTasks(hostTestCreationConfig)

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(hostTestCreationConfig)

        // process java resources
        createProcessJavaResTask(hostTestCreationConfig)

        if (includeAndroidResources) {
            setupAndroidRequiredTasks(testedVariant, hostTestCreationConfig)

            setupCompilationTaskDependencies(hostTestCreationConfig, taskContainer)

        } else {
            if (testedVariant.componentType.isAar && testedVariant.buildFeatures.androidResources) {
                // With compile classpath R classes, we need to generate a dummy R class for unit
                // tests
                // See https://issuetracker.google.com/143762955 for more context.
                taskFactory.register(
                    GenerateLibraryRFileTask.TestRuntimeStubRClassCreationAction(
                        hostTestCreationConfig
                    )
                )
            }
        }

        setupAssembleAndJavaCompilationTasks(
            hostTestCreationConfig, taskContainer, testedVariant, ASSEMBLE_UNIT_TEST)

        // TODO: use merged java res for unit tests (bug 118690729)
        super.createRunHostTestTask(
            hostTestCreationConfig,
            globalConfig.taskNames.test,
            JavaPlugin.TEST_TASK_NAME,
            InternalArtifactType.UNIT_TEST_CODE_COVERAGE)
    }

    override val javaResMergingScopes = setOf(
        InternalScopedArtifacts.InternalScope.SUB_PROJECTS,
        InternalScopedArtifacts.InternalScope.EXTERNAL_LIBS,
    )
}
