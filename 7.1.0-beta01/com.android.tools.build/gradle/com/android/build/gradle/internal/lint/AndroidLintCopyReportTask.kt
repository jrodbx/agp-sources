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

package com.android.build.gradle.internal.lint

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.dsl.LintOptions
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.UnsafeOutputsTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.utils.FileUtils
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.Internal
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files

/**
 *  Task to copy lint outputs to the desired user directory
 *
 * Does not declare its outputs, as multiple lint tasks may overwrite the same files.
 *
 * Does not declare its inputs because doing so would cause this task to not run when the
 * [AndroidLintTask] fails
 */
@DisableCachingByDefault
abstract class AndroidLintCopyReportTask : UnsafeOutputsTask("The lintOptions DSL has configured potentially multiple lint tasks to write to the same location, but task should not have overlapping outputs.") {

    @get:Internal
    abstract val textReportInput: RegularFileProperty

    @get:Internal
    abstract val htmlReportInput: RegularFileProperty

    @get:Internal
    abstract val xmlReportInput: RegularFileProperty

    @Suppress("SpellCheckingInspection")
    @get:Internal
    abstract val sarifReportInput: RegularFileProperty

    @get:Internal
    abstract val textReportOutput: RegularFileProperty

    @get:Internal
    abstract val htmlReportOutput: RegularFileProperty

    @get:Internal
    abstract val xmlReportOutput: RegularFileProperty

    @Suppress("SpellCheckingInspection")
    @get:Internal
    abstract val sarifReportOutput: RegularFileProperty

    override fun doTaskAction() {
        doCopy(textReportInput, textReportOutput, displayName = "text")
        doCopy(htmlReportInput, htmlReportOutput, displayName = "HTML")
        doCopy(xmlReportInput, xmlReportOutput, displayName = "XML")
        @Suppress("SpellCheckingInspection")
        doCopy(sarifReportInput, sarifReportOutput, displayName = "SARIF")
    }

    private fun doCopy(from: RegularFileProperty, to: RegularFileProperty, displayName: String) {
        val toPath = to.orNull?.asFile?.toPath() ?: return
        val inputPath = from.orNull?.asFile?.toPath() ?: return
        val logger = Logging.getLogger(AndroidLintCopyReportTask::class.java)
        if (!inputPath.toFile().isFile) {
            // Workaround for b/189877657. It would be better to annotate textReportInput and the
            // other inputs with @InputFile, but doing that causes this task to not run when the
            // AndroidLintTask fails.
            logger.warn(
                "Unable to copy the lint $displayName report because it does not exist. The lint "
                        + "$displayName report file was expected to be located at "
                        + "${inputPath.toAbsolutePath()}."
            )
            return
        }
        logger.lifecycle("Copying lint $displayName report to $toPath")
        Files.createDirectories(toPath.parent)
        FileUtils.copyFile(inputPath, toPath)
    }

    class CreationAction(variant: VariantCreationConfig) : VariantTaskCreationAction<AndroidLintCopyReportTask, VariantCreationConfig>(variant) {
        override val name: String = computeTaskName("copy", "AndroidLintReports")
        override val type: Class<AndroidLintCopyReportTask> get() = AndroidLintCopyReportTask::class.java
        override fun configure(task: AndroidLintCopyReportTask) {
            super.configure(task)
            task.registerInputs(creationConfig.artifacts, creationConfig.globalScope.extension.lintOptions)
        }
    }

    internal fun registerInputs(artifacts: ArtifactsImpl, lintOptions: LintOptions) {
        val textOutput = lintOptions.textOutput
        if (lintOptions.textReport && textOutput != null && textOutput.path != "stdout" && textOutput.path != "stderr") {
            textReportInput.set(artifacts.get(InternalArtifactType.LINT_TEXT_REPORT))
            textReportOutput.set(textOutput)
        }
        textReportInput.disallowChanges()
        textReportOutput.disallowChanges()
        if (lintOptions.htmlReport && lintOptions.htmlOutput != null) {
            htmlReportInput.set(artifacts.get(InternalArtifactType.LINT_HTML_REPORT))
            htmlReportOutput.set(lintOptions.htmlOutput)
        }
        htmlReportInput.disallowChanges()
        htmlReportOutput.disallowChanges()
        if (lintOptions.xmlReport && lintOptions.xmlOutput != null) {
            xmlReportInput.set(artifacts.get(InternalArtifactType.LINT_XML_REPORT))
            xmlReportOutput.set(lintOptions.xmlOutput)
        }
        xmlReportInput.disallowChanges()
        xmlReportOutput.disallowChanges()
        if (lintOptions.sarifReport && lintOptions.sarifOutput != null) {
            sarifReportInput.set(artifacts.get(InternalArtifactType.LINT_SARIF_REPORT))
            sarifReportOutput.set(lintOptions.sarifOutput)
        }
        sarifReportInput.disallowChanges()
        sarifReportOutput.disallowChanges()
    }

    internal fun configureForStandalone(artifacts: ArtifactsImpl, lintOptions: LintOptions) {
        registerInputs(artifacts, lintOptions)
        analyticsService.setDisallowChanges(getBuildService(project.gradle.sharedServices))
        variantName = ""
    }
}
