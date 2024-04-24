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
import com.android.build.gradle.internal.testing.screenshot.ImageDetails
import com.android.build.gradle.internal.testing.screenshot.PERIOD
import com.android.build.gradle.internal.testing.screenshot.PreviewResult
import com.android.build.gradle.internal.testing.screenshot.ResponseProcessor
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.core.ComponentType
import com.android.utils.FileUtils
import org.gradle.api.GradleException
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

/**
 * Update golden images of a variant.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class PreviewScreenshotUpdateTask : NonIncrementalTask(), VerificationTask {

    @Internal
    override lateinit var variantName: String

    @get:OutputDirectory
    abstract val goldenImageDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val renderTaskOutputDir: DirectoryProperty

    override fun doTaskAction() {
        //throw exception at the first encountered error
        val response = ResponseProcessor(renderTaskOutputDir.get().asFile.toPath()).process()

        val resultsToSave = mutableListOf<PreviewResult>()
        for (previewResult in response.previewResults) {
            saveGoldenImage(previewResult)
        }
    }

    private fun saveGoldenImage(previewResult: PreviewResult) {
        if (previewResult.responseCode != 0) {
            throw GradleException(previewResult.message)
        }
        val screenshotName = previewResult.previewName.substring(
            previewResult.previewName.lastIndexOf(PERIOD) + 1)
        val screenshotNamePng = "$screenshotName.png"
        val goldenPath = goldenImageDir.asFile.get().toPath().resolve(screenshotNamePng)
        val goldenImageDetails = ImageDetails(goldenPath, null)
        val actualPath = renderTaskOutputDir.asFile.get().toPath().resolve(screenshotName + "_actual.png")
        FileUtils.copyFile(actualPath.toFile(), goldenPath.toFile())
    }

    class CreationAction(
        private val androidTestCreationConfig: AndroidTestCreationConfig,
        private val goldenImageDir: File,
    ) :
        VariantTaskCreationAction<
                PreviewScreenshotUpdateTask,
                InstrumentedTestCreationConfig
                >(androidTestCreationConfig) {

        override val name = computeTaskName(ComponentType.PREVIEW_SCREENSHOT_UPDATE_PREFIX)
        override val type = PreviewScreenshotUpdateTask::class.java

        override fun configure(task: PreviewScreenshotUpdateTask) {
            val testedConfig = (creationConfig as? AndroidTestCreationConfig)?.mainVariant
            task.variantName = testedConfig?.name ?: creationConfig.name

            val testedVariant = androidTestCreationConfig.mainVariant
            task.description = "Update screenshots for the " + testedVariant.name + " build."

            task.group = JavaBasePlugin.VERIFICATION_GROUP

            task.goldenImageDir.set(goldenImageDir)
            task.goldenImageDir.disallowChanges()

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.SCREENSHOTS_RENDERED, task.renderTaskOutputDir
            )
        }
    }
}

