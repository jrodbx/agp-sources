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

package com.android.build.gradle.internal.testing.screenshot

import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO

class Verify(private val imageDiffer: ImageDiffer = ImageDiffer.PixelPerfect(), private val diffFilePath: Path) {
    fun assertMatchGolden(goldenPath: Path, image: BufferedImage) : AnalysisResult {

        return analyze(goldenPath, image)
    }

    private fun analyze(goldenPath: Path, actual: BufferedImage): AnalysisResult {
        var golden: BufferedImage? = null
        if (goldenPath.toFile().exists()) {
            golden = ImageIO.read(goldenPath.toFile())

        }
        if (golden == null) return AnalysisResult.MissingGolden(actual, "MISSING GOLDEN")
        if (actual.width != golden.width || actual.height != golden.height) {
            return AnalysisResult.SizeMismatch(actual, "Size Mismatch. Reference image size: ${golden.width}X${golden.height}. Rendered image size: ${actual.width}X${actual.height}", golden)
        }
        val diff = imageDiffer.diff(actual, golden)
        if (diff.highlights != null) {
            val diffFile = diffFilePath.toFile()
            ImageIO.write(diff.highlights, "png", diffFile)
        }

        if (diff is ImageDiffer.DiffResult.Different) {
            return AnalysisResult.Failed(actual, "FAILED", golden, diff)
        }
        return AnalysisResult.Passed(actual,
                "PASSED",
                golden,
                diff as ImageDiffer.DiffResult.Similar)
    }

    sealed interface AnalysisResult {
        val actual: BufferedImage
        val message: String

        data class Passed(
                override val actual: BufferedImage,
                override val message: String,
                val expected: BufferedImage,
                val imageDiff: ImageDiffer.DiffResult.Similar
        ) : AnalysisResult

        data class Failed(
                override val actual: BufferedImage,
                override val message: String,
                val expected: BufferedImage,
                val imageDiff: ImageDiffer.DiffResult.Different
        ) : AnalysisResult

        data class SizeMismatch(
                override val actual: BufferedImage,
                override val message: String,
                val expected: BufferedImage
        ) : AnalysisResult

        data class MissingGolden(
                override val actual: BufferedImage,
                override val message: String
        ) : AnalysisResult
    }
}
