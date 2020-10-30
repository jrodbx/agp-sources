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

import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ARTIFACT_TYPE
import com.android.build.gradle.tasks.LintBaseTask.LINT_CLASS_PATH
import com.android.builder.model.LintOptions
import com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION
import com.android.repository.Revision
import com.android.tools.lint.gradle.api.LintExecutionRequest
import com.android.tools.lint.gradle.api.ReflectiveLintRunner
import com.android.tools.lint.gradle.api.VariantInputs
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import java.io.File
import java.io.IOException

/**
 * Task for running lint <b>without</b> the Android Gradle plugin, such as in a pure Kotlin
 * project.
 */
open class LintStandaloneTask : DefaultTask() {
    @get:OutputDirectory
    var reportDir: File? = null

    @get:Input
    var fatalOnly: Boolean = false

    @get:Internal("Temporary to suppress warnings (bug 135900510), may need more investigation")
    var lintOptions: LintOptions? = null

    @get:Optional
    @get:Classpath
    var lintChecks: Configuration? = null

    @get:Input
    var autoFix: Boolean = false

    /** This resolves the dependency of the lintChecks configuration */
    private fun computeLocalChecks(): FileCollection {
        val configuration = lintChecks ?: return project.files()
        val attributes = Action { container: AttributeContainer ->
            // Query for JAR instead of PROCESSED_JAR as this task is executed without the AGP
            container.attribute(ARTIFACT_TYPE, AndroidArtifacts.ArtifactType.JAR.type)
        }

        return configuration
                .incoming
                .artifactView { config -> config.attributes(attributes) }
                .artifacts
                .artifactFiles
    }

    @TaskAction
    @Throws(IOException::class, InterruptedException::class)
    fun run() {
        val lintClassPath = project.configurations.getByName(LINT_CLASS_PATH)
        if (lintClassPath != null) {
            val request = object : LintExecutionRequest() {
                override fun getKotlinSourceFolders(
                    variantName: String,
                    project: Project?
                ): List<File> = emptyList()
                override val project: Project = this@LintStandaloneTask.project
                override val reportsDir: File? = this@LintStandaloneTask.reportDir
                override val lintOptions: LintOptions? = this@LintStandaloneTask.lintOptions
                override val gradlePluginVersion: String = ANDROID_GRADLE_PLUGIN_VERSION
                override val isFatalOnly: Boolean = this@LintStandaloneTask.fatalOnly

                override fun warn(message: String, vararg args: Any) {
                    Logging.getLogger(LintStandaloneTask::class.java).warn(message, args)
                }

                override fun getVariantInputs(variantName: String): VariantInputs? {
                    return object : VariantInputs {
                        override val name: String = ""
                        override val ruleJars: FileCollection = computeLocalChecks()
                        override val mergedManifest: File? = null
                        override val manifestMergeReport: File? = null
                    }
                }

                // Android specific : doesn't apply here

                override val buildToolsRevision: Revision? = null
                override val variantName: String? = null
                override val sdkHome: File? = null
                override val toolingRegistry: ToolingModelBuilderRegistry? = null

            }
            ReflectiveLintRunner().runLint(project.gradle, request, lintClassPath.files)
        }
    }
}