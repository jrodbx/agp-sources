/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.InstrumentedTestCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.test.report.ReportType
import com.android.build.gradle.internal.test.report.TestReport
import com.android.build.gradle.internal.testing.screenshot.ImageDetails
import com.android.build.gradle.internal.testing.screenshot.ImageDiffer
import com.android.build.gradle.internal.testing.screenshot.PERIOD
import com.android.build.gradle.internal.testing.screenshot.PreviewResult
import com.android.build.gradle.internal.testing.screenshot.Verify
import com.android.build.gradle.internal.testing.screenshot.ResponseProcessor
import com.android.build.gradle.internal.testing.screenshot.saveResults
import com.android.build.gradle.internal.testing.screenshot.toPreviewResponse
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.core.ComponentType
import com.android.utils.FileUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File
import org.gradle.api.tasks.VerificationTask
import java.nio.file.StandardCopyOption
import javax.imageio.ImageIO

/**
 * Runs screenshot tests of a variant.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class PreviewScreenshotValidationTask : NonIncrementalTask(), VerificationTask {

    @Internal
    override lateinit var variantName: String

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val goldenImageDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val renderTaskOutputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val imageOutputDir: DirectoryProperty

    override fun doTaskAction() {
        val response = ResponseProcessor(renderTaskOutputDir.get().asFile.toPath()).process()

        var verificationFailures = 0
        val resultsToSave = mutableListOf<PreviewResult>()
        for (previewResult in response.previewResults) {
            val imageComparison = compareImages(previewResult)
            resultsToSave.add(imageComparison)
            if (imageComparison.responseCode != 0) {
                verificationFailures++
            }
        }

        val xmlFilePath = saveResults(resultsToSave, imageOutputDir.asFile.get().toPath())
        val reportDir = imageOutputDir.get().asFile
        val report = TestReport(
            ReportType.SINGLE_FLAVOR,
            reportDir,
            reportDir
        )
        report.generateScreenshotTestReport(false)
        val xmlFile = xmlFilePath.toFile()
        xmlFile.delete()
        if (verificationFailures > 0) {
            val reportUrl = File(reportDir, "index.html").toURI().toASCIIString()
            val message = "There were failing tests. See the report at: $reportUrl"
            throw GradleException(message)
        }
    }

    private fun compareImages(previewResult: PreviewResult): PreviewResult {
        val imageDiffer = ImageDiffer.MSSIMMatcher()
        // TODO(b/296430073) Support custom image difference threshold from DSL or task argument
        val screenshotName = previewResult.previewName.substring(
            previewResult.previewName.lastIndexOf(PERIOD) + 1)
        val screenshotNamePng = "$screenshotName.png"
        var goldenPath = goldenImageDir.asFile.get().toPath().resolve(screenshotNamePng)
        var goldenMessage: String? = null
        val actualPath = imageOutputDir.asFile.get().toPath().resolve(screenshotName + "_actual.png")
        var diffPath = imageOutputDir.asFile.get().toPath().resolve(screenshotName + "_diff.png")
        var diffMessage: String? = null
        var code = 0
        val verifier =
            Verify(imageDiffer, diffPath)

        //If the CLI tool could not render the preview, return the preview result with the
        //code and message along with golden path if it exists
        if (previewResult.responseCode != 0 ) {
            if (!goldenPath.toFile().exists()) {
                goldenPath = null
                goldenMessage = "Reference image missing"
            }

            return previewResult.copy(
                responseCode = previewResult.responseCode,
                previewName = previewResult.previewName,
                message = previewResult.message,
                goldenImage = ImageDetails(goldenPath, goldenMessage),
                actualImage = ImageDetails(null, previewResult.message)
            )

        }
        // copy rendered image from intermediate dir to output dir
        FileUtils.copyFile(renderTaskOutputDir.asFile.get().toPath().resolve(screenshotName + "_actual.png").toFile(), actualPath.toFile())

        val result =
            verifier.assertMatchGolden(
                goldenPath,
                ImageIO.read(actualPath.toFile())
            )
        when (result) {
            is Verify.AnalysisResult.Failed -> {
                code = 1
            }
            is Verify.AnalysisResult.Passed -> {
                if (result.imageDiff.highlights == null) {
                    diffPath = null
                    diffMessage = "Images match!"
                }
            }
            is Verify.AnalysisResult.MissingGolden -> {
                goldenPath = null
                diffPath = null
                goldenMessage = "Golden image missing"
                diffMessage = "No diff available"
                code = 1
            }
            is Verify.AnalysisResult.SizeMismatch -> {
                diffMessage = result.message
                diffPath = null
                code = 1
            }
        }
        return result.toPreviewResponse(code, previewResult.previewName,
            ImageDetails(goldenPath, goldenMessage),
            ImageDetails(actualPath, null),
            ImageDetails(diffPath, diffMessage))
    }

    class CreationAction(
            private val androidTestCreationConfig: AndroidTestCreationConfig,
            private val imageOutputDir: File,
            private val goldenImageDir: File,
    ) :
        VariantTaskCreationAction<
                PreviewScreenshotValidationTask,
                InstrumentedTestCreationConfig
                >(androidTestCreationConfig) {

        override val name = computeTaskName(ComponentType.PREVIEW_SCREENSHOT_PREFIX)
        override val type = PreviewScreenshotValidationTask::class.java

        override fun configure(task: PreviewScreenshotValidationTask) {
            val testedConfig = (creationConfig as? AndroidTestCreationConfig)?.mainVariant
            task.variantName = testedConfig?.name ?: creationConfig.name

            val testedVariant = androidTestCreationConfig.mainVariant
            task.description = "Run screenshot tests for the " + testedVariant.name + " build."

            task.group = JavaBasePlugin.VERIFICATION_GROUP

            task.goldenImageDir.set(goldenImageDir)
            task.goldenImageDir.disallowChanges()

            task.imageOutputDir.set(imageOutputDir)
            task.imageOutputDir.disallowChanges()

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.SCREENSHOTS_RENDERED, task.renderTaskOutputDir
            )


        }
    }
}
