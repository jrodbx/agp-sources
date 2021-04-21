/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.component.TestCreationConfig
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Internal
import java.io.File

/**
 * Pre build task that checks that there are not differences between artifact versions between the
 * runtime classpath of tested variant, and runtime classpath of test variant.
 */
@CacheableTask
abstract class TestPreBuildTask : ClasspathComparisonTask() {

    @get:Internal("only for task execution")
    abstract val projectPath: Property<String>

    override fun onDifferentVersionsFound(
        group: String,
        module: String,
        runtimeVersion: String,
        compileVersion: String
    ) {
        throw GradleException(
            """Conflict with dependency '$group:$module' in project '${projectPath.get()}'.
Resolved versions for app ($compileVersion) and test app ($runtimeVersion) differ.
See https://d.android.com/r/tools/test-apk-dependency-conflicts.html for details."""
        )
    }

    class CreationAction(creationConfig: TestCreationConfig) :
        TaskManager.AbstractPreBuildCreationAction<TestPreBuildTask>(creationConfig) {

        override val type: Class<TestPreBuildTask>
            get() = TestPreBuildTask::class.java

        override fun configure(
            task: TestPreBuildTask
        ) {
            super.configure(task)
            val runtimeClasspath = creationConfig.variantDependencies.runtimeClasspath
            val compileClasspath =
                creationConfig.testedConfig?.variantDependencies?.runtimeClasspath
            task.runtimeVersionMap.set(
                task.project.providers.provider {
                    runtimeClasspath.toVersionMap()
                }
            )
            task.compileVersionMap.set(
                task.project.providers.provider {
                    compileClasspath?.toVersionMap()
                }
            )
            task.fakeOutputDirectory = File(
                creationConfig.globalScope.intermediatesDir,
                "prebuild/${creationConfig.dirName}"
            )
            task.projectPath.setDisallowChanges(task.project.path)
        }
    }
}
