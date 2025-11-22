/*
 * Copyright (C) 2025 The Android Open Source Project
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
import com.android.build.api.dsl.TestTaskContext
import com.android.build.gradle.internal.component.TestSuiteCreationConfig
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.tasks.TestSuiteTestTask
import org.gradle.api.Project

class TestSuiteTaskManager(
    project: Project,
    globalConfig: GlobalTaskCreationConfig,
): TaskManager(project, globalConfig) {

    override val javaResMergingScopes: Set<InternalScopedArtifacts.InternalScope>
        get() = setOf()

    fun createTasks(creationConfig: TestSuiteCreationConfig) {
        creationConfig.targets
            .filter { it.value.enabled }
            .forEach { mapEntry ->
                val target = mapEntry.value
                taskFactory.register(
                    TestSuiteTestTask.CreationAction(creationConfig, target)
                ).also {
                    val context = object : TestTaskContext {
                        override val targetName: String
                            get() = target.name
                        override val suiteName: String
                            get() = creationConfig.name
                        override val targetedVariant: String
                            get() = creationConfig.testedVariant.name
                        override val targetedDevices: Collection<String>
                            get() = target.targetDevices

                        override fun toString(): String {
                            return super.toString() + "targetName:$targetName, suiteName:$suiteName," +
                                    " targetedVariant:$targetedVariant, " +
                                    "devices = ${targetedDevices.joinToString(separator = ":")}"
                        }
                    }
                    creationConfig.runTestTaskConfigurationActions(context, it)
                }
            }
    }
}
