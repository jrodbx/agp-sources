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

import java.nio.file.Path

/*
* Result of a rendered preview that indicates if the preview was rendered successfully
*/
data class PreviewResult(
    val responseCode: Int,
    val previewName: String,
    val message: String? = null,
    val goldenImage: ImageDetails? = null,
    val actualImage: ImageDetails? = null,
    val diffImage: ImageDetails? = null

) {
}

fun Verify.AnalysisResult.toPreviewResponse(code: Int, name: String, golden: ImageDetails,
    actual: ImageDetails? = null,
    diff: ImageDetails? = null): PreviewResult{
      return PreviewResult(code, name, message, golden, actual, diff)
}

/*
* Response from [PreviewScreenshotRenderTask] that indicates if previews were rendered successfully and provides a list of [PreviewResult]s
*/
data class Response(val status: Int, val message: String, val previewResults: List<PreviewResult>) {
}

/**
 * class to encapsulate comparison images to be consumed by report generator
 * In case of missing image, the message will contain the text to be displayed instead
 */
data class ImageDetails(val path: Path?, val message: String?)
