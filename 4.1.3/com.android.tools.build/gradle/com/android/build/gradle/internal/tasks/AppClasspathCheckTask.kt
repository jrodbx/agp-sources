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

package com.android.build.gradle.internal.tasks

import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.ide.common.repository.GradleVersion
import com.android.utils.FileUtils
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Internal

/**
 * Pre build task that performs comparison of runtime and compile classpath for application. If
 * there are any differences between the two, that could lead to runtime issues.
 */
@CacheableTask
abstract class AppClasspathCheckTask : ClasspathComparisonTask() {

    @get:Internal("only for task execution")
    abstract val projectPath: Property<String>

    @get:Internal("only for task execution")
    abstract val projectBuildFile: RegularFileProperty

    override fun onDifferentVersionsFound(
        group: String,
        module: String,
        runtimeVersion: String,
        compileVersion: String
    ) {

        val suggestedVersion: String = try {
            val runtime = GradleVersion.parse(runtimeVersion)
            val compile = GradleVersion.parse(compileVersion)
            if (runtime > compile) {
                runtimeVersion
            } else {
                compileVersion
            }
        } catch (e: Throwable) {
            // in case we are unable to parse versions for some reason, choose runtime
            runtimeVersion
        }

        val message =
            """Conflict with dependency '$group:$module' in project '${projectPath.get()}'.
Resolved versions for runtime classpath ($runtimeVersion) and compile classpath ($compileVersion) differ.
This can lead to runtime crashes.
To resolve this issue follow advice at https://developer.android.com/studio/build/gradle-tips#configure-project-wide-properties.
Alternatively, you can try to fix the problem by adding this snippet to ${projectBuildFile.get().asFile}:

dependencies {
    implementation("$group:$module:$suggestedVersion")
}
"""

        throw RuntimeException(message)
    }

    class CreationAction(private val componentProperties: ComponentPropertiesImpl) :
        TaskCreationAction<AppClasspathCheckTask>() {

        override val name: String
        get() = componentProperties.computeTaskName("check", "Classpath")

        override val type: Class<AppClasspathCheckTask>
            get() = AppClasspathCheckTask::class.java

        override fun configure(task: AppClasspathCheckTask) {
            task.variantName = componentProperties.name

            val runtimeClasspath = componentProperties.variantDependencies.runtimeClasspath
            val compileClasspath = componentProperties.variantDependencies.compileClasspath
            task.runtimeVersionMap.set(
                task.project.providers.provider {
                    runtimeClasspath.toVersionMap()
                }
            )
            task.compileVersionMap.set(
                task.project.providers.provider {
                    compileClasspath.toVersionMap()
                }
            )
            task.fakeOutputDirectory = FileUtils.join(
                componentProperties.globalScope.intermediatesDir,
                name,
                componentProperties.dirName
            )
            task.projectPath.setDisallowChanges(task.project.path)
            task.projectBuildFile.set(task.project.buildFile)
            task.projectBuildFile.disallowChanges()
        }
    }
}
