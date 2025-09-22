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

package com.android.build.gradle.internal.test.report

import org.gradle.reporting.ReportRenderer
import java.io.File

class ImagePanelRenderer(private val isRecordGolden: Boolean): ReportRenderer<ScreenshotTestImages, SimpleHtmlWriter>() {

    val ALT_TEXT_PREFIX = "Error displaying image at "
    val COLUMN_NAME_GOLDEN = "Reference Image"
    val COLUMN_NAME_ACTUAL = "Actual Image"
    val COLUMN_NAME_DIFF = "Diff Image"

    override fun render(ssImages: ScreenshotTestImages, htmlWriter: SimpleHtmlWriter) {

        // Wrap in a <span>, to work around CSS problem in IE
        htmlWriter.startElement("span")
            .startElement("table")
            .startElement("thead")
            .startElement("tr")

        addColumn(htmlWriter, COLUMN_NAME_GOLDEN)
        if (!isRecordGolden) {
            addColumn(htmlWriter, COLUMN_NAME_ACTUAL)
            addColumn(htmlWriter, COLUMN_NAME_DIFF)
        }

        htmlWriter.endElement().endElement()
        htmlWriter.startElement("tbody").attribute("class", "grid")  // this class will render a grid like background to better show the diff between png images with and without background
            .startElement("tr")
        val texts = getTexts(ssImages)
        renderImage(htmlWriter, ssImages.golden.path, texts.goldenText)
        if (!isRecordGolden) {
            renderImage(htmlWriter, ssImages.actual!!.path, texts.actualText)
            renderImage(htmlWriter, ssImages.diff!!.path, texts.diffText)
        }
        htmlWriter.endElement().endElement().endElement().endElement()

    }

    private fun addColumn(htmlWriter: SimpleHtmlWriter, columnName: String) {
        htmlWriter.startElement("th").characters(columnName).endElement()
    }

    private fun renderImage(htmlWriter: SimpleHtmlWriter, imagePath: String, text: String) {
        if (imagePath.isNotEmpty())
            htmlWriter.startElement("td")
                .startElement("img").attribute("src", imagePath).attribute("alt", text).endElement()
                .endElement()
        else
            htmlWriter.startElement(("td")).characters(text).endElement()
    }

    private fun getTexts(ssImages: ScreenshotTestImages): ImageTexts {
        // alt texts when image was generated, but image display fails
        var goldenText = "$ALT_TEXT_PREFIX${ssImages.golden.path}"
        var actualText = "$ALT_TEXT_PREFIX${ssImages.actual?.path}"
        var diffText = "$ALT_TEXT_PREFIX${ssImages.diff?.path}"

        // text to display when image was not generated
        if (ssImages.golden.path.isEmpty())
            goldenText = ssImages.golden.message
        if (ssImages.actual != null && ssImages.actual.path.isEmpty())
            actualText = ssImages.actual.message
        if (ssImages.diff != null && ssImages.diff.path.isEmpty())
            diffText = ssImages.diff.message


        return ImageTexts(goldenText, actualText, diffText)
    }
}

data class ImageTexts(val goldenText: String, val actualText: String, val diffText: String) {

}
