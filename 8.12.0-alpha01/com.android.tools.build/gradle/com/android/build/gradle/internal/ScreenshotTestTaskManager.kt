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
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import org.gradle.api.Project

class ScreenshotTestTaskManager(
    project: Project,
    globalConfig: GlobalTaskCreationConfig
): HostTestTaskManager(project, globalConfig) {

    fun createTopLevelTasks() {
        // Create top level screenshot test tasks.
        super.createTopLevelTasksCore(
            Companion.SCREENSHOT_TEST_EXECUTION_TASK_NAME,
            "Run screenshot tests for all variants."
        )
    }

    /** Creates the tasks to build screenshot tests.  */
    fun createTasks(screenshotTestCreationConfig: HostTestCreationConfig) {
        val taskContainer = screenshotTestCreationConfig.taskContainer
        val testedVariant = screenshotTestCreationConfig.mainVariant
        createAnchorTasks(screenshotTestCreationConfig)

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(screenshotTestCreationConfig)

        // process java resources
        createProcessJavaResTask(screenshotTestCreationConfig)

        setupAndroidRequiredTasks(testedVariant, screenshotTestCreationConfig)

        setupCompilationTaskDependencies(screenshotTestCreationConfig, taskContainer)

        setupAssembleTasks(screenshotTestCreationConfig, taskContainer, ASSEMBLE_SCREENSHOT_TEST)

        setupJavaCompilationTasks(screenshotTestCreationConfig, taskContainer, testedVariant)

        maybeCreateTransformClassesWithAsmTask(screenshotTestCreationConfig)

        setupLintTasks(screenshotTestCreationConfig)

        createRunHostTestTask(
            screenshotTestCreationConfig,
            SCREENSHOT_TEST_EXECUTION_TASK_NAME,
            SCREENSHOT_TEST_EXECUTION_TASK_NAME,
            InternalArtifactType.SCREENSHOT_TEST_CODE_COVERAGE
        )
    }

    override val javaResMergingScopes = setOf(
        InternalScopedArtifacts.InternalScope.SUB_PROJECTS,
        InternalScopedArtifacts.InternalScope.EXTERNAL_LIBS,
    )
}
