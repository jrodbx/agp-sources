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
package com.android.build.gradle

import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.dsl.LintOptions
import com.android.build.gradle.internal.plugins.BasePlugin
import com.android.build.gradle.internal.tasks.LintStandaloneTask
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.TaskProvider

/**
 * Plugin for running lint **without** the Android Gradle plugin, such as in a pure Kotlin
 * project.
 */
open class LintPlugin : Plugin<Project> {
    private lateinit var project: Project
    private var lintOptions: LintOptions? = null
    override fun apply(project: Project) {
        // We run by default in headless mode, so the JVM doesn't steal focus.
        System.setProperty("java.awt.headless", "true")
        this.project = project
        createExtension(project)
        BasePlugin.createLintClasspathConfiguration(project)
        withJavaPlugin(
            Action {
                getJavaPluginConvention()?.let { javaConvention ->
                    val customLintChecksConfig = TaskManager.createCustomLintChecksConfig(project)
                    val projectName = project.name
                    val task = registerTask(
                        "lint",
                        "Run Android Lint analysis on project `$projectName`",
                        project,
                        javaConvention,
                        customLintChecksConfig
                    )
                    // Make the check task depend on the lint
                    project.tasks.named(JavaBasePlugin.CHECK_TASK_NAME)
                        .configure { t: Task -> t.dependsOn(task) }
                    val lintVital = registerTask(
                        "lintVital",
                        "Runs lint on just the fatal issues in the project `$projectName`",
                        project,
                        javaConvention,
                        customLintChecksConfig
                    )
                    lintVital.configure { it.fatalOnly = true }
                    registerTask(
                        "lintFix",
                        "Runs lint on `$projectName` and applies any safe suggestions to " +
                            "the source code.",
                        project,
                        javaConvention,
                        customLintChecksConfig
                    ).configure {
                        it.autoFix = true
                        it.group = "cleanup"
                    }
                }
            })
    }

    private fun createExtension(project: Project) {
        lintOptions = project.extensions.create("lintOptions", LintOptions::class.java)
    }

    private fun withJavaPlugin(action: Action<Plugin<*>>) {
        project.plugins.withType(JavaBasePlugin::class.java, action)
    }

    private fun registerTask(
        taskName: String,
        description: String,
        project: Project,
        javaConvention: JavaPluginConvention,
        customLintChecksConfig: Configuration
    ): TaskProvider<LintStandaloneTask> {
        return project.tasks.register(taskName, LintStandaloneTask::class.java) { task ->
            task.group = JavaBasePlugin.VERIFICATION_GROUP
            task.description = description
            task.reportDir = javaConvention.testResultsDir
            task.lintOptions = lintOptions
            task.lintChecks = customLintChecksConfig
            task.outputs.upToDateWhen { false }
        }
    }

    private fun getJavaPluginConvention(): JavaPluginConvention? {
        val convention = project.convention
        val javaConvention = convention.findPlugin(JavaPluginConvention::class.java)
        if (javaConvention == null) {
            project.logger.warn("Cannot apply lint if the java or kotlin Gradle plugins " +
                "have also been applied")
            return null
        }
        return javaConvention
    }
}