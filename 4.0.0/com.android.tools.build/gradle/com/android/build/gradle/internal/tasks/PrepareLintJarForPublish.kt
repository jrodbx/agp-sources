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

import com.android.SdkConstants.FN_LINT_JAR
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.options.BooleanOption
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

/**
 * Task that takes the configuration result, and check that it's correct.
 *
 * <p>Then copies it in the build folder to (re)publish it. This is not super efficient but because
 * publishing is done at config time when we don't know yet what lint.jar file we're going to
 * publish, we have to do this.
 */
abstract class PrepareLintJarForPublish : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var lintChecks: FileCollection
        private set
    @get:OutputFile abstract val outputLintJar: RegularFileProperty

    @get:Internal
    val projectName = project.name

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @get:Input
    abstract val enableGradleWorkers: Property<Boolean>

    companion object {
        const val NAME = "prepareLintJarForPublish"
    }

    @TaskAction
    fun prepare() {
        Workers.preferWorkers(projectName, path, workerExecutor, enableGradleWorkers.get()).use {
            it.submit(
                PublishLintJarWorkerRunnable::class.java, PublishLintJarRequest(
                    files = lintChecks.files,
                    outputLintJar = outputLintJar.get().asFile
                )
            )
        }
    }

    class CreationAction(private val scope: GlobalScope) : TaskCreationAction<PrepareLintJarForPublish>() {
        override val name = NAME
        override val type = PrepareLintJarForPublish::class.java

        override fun handleProvider(taskProvider: TaskProvider<out PrepareLintJarForPublish>) {
            super.handleProvider(taskProvider)
            scope.artifacts.producesFile(
                InternalArtifactType.LINT_PUBLISH_JAR,
                taskProvider,
                PrepareLintJarForPublish::outputLintJar,
                FN_LINT_JAR
            )
        }

        override fun configure(task: PrepareLintJarForPublish) {
            task.lintChecks = scope.publishedCustomLintChecks
            task.enableGradleWorkers.set(scope.projectOptions[BooleanOption.ENABLE_GRADLE_WORKERS])
        }
    }
}