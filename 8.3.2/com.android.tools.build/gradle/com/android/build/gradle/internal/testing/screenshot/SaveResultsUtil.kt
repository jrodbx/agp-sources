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

import com.android.SdkConstants
import org.kxml2.io.KXmlSerializer
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Path

const val ACTUAL = "actual"
const val CLASSNAME = "classname"
const val COLON = ":"
const val DIFF = "diff"
const val ERRORS = "errors"
const val FAILURE = "failure"
const val FAILURES = "failures"
const val FEATURE = "http://xmlpull.org/v1/doc/features.html#indent-output"
const val GOLDEN = "golden"
const val IMAGES = "images"
const val MESSAGE = "message"
const val NAME = "name"
const val PATH = "path"
const val PERIOD = "."
const val PROPERTIES = "properties"
const val PROPERTY = "property"
const val SKIPPED = "skipped"
const val SUCCESS = "success"
const val TESTCASE = "testcase"
const val TESTS = "tests"
const val TESTSUITE = "testsuite"
const val VALUE = "value"
const val ZERO = "zero"

val NAMESPACE = null

fun saveResults(
    previewResults: List<PreviewResult>,
    outputLocation: Path,
    xmlProperties: List<String>? = null,
): Path {
    val path = outputLocation.resolve("TEST-response.xml")
    val stream = createOutputResultStream(path.toAbsolutePath().toString())
    val serializer = KXmlSerializer()
    serializer.setOutput(stream, SdkConstants.UTF_8)
    serializer.startDocument(SdkConstants.UTF_8, null)
    serializer.setFeature(
        FEATURE, true
    )
    printTestResults(serializer, previewResults, xmlProperties)
    serializer.endDocument()
    return path

}

@Throws(IOException::class)
private fun printTestResults(
    serializer: KXmlSerializer,
    previewResults: List<PreviewResult>,
    xmlProperties: List<String>?
) {
    serializer.startTag(NAMESPACE, TESTSUITE)
    val runName = previewResults.first().previewName
    val name = runName.substring(0, runName.lastIndexOf(PERIOD))
    if (name.isNotEmpty()) {
        serializer.attribute(NAMESPACE, NAME, name)
    }
    serializer.attribute(NAMESPACE, TESTS, previewResults.size.toString()
    )
    serializer.attribute(
        NAMESPACE, FAILURES,
        previewResults.filter { it.responseCode == 1 || it.responseCode == 2 }.size.toString()
    )
    serializer.attribute(NAMESPACE, ERRORS, ZERO) //errors are not read by html report generator
    serializer.attribute(NAMESPACE, SKIPPED, ZERO)
    serializer.startTag(NAMESPACE, PROPERTIES)
    for ((key, value) in getPropertiesAttributes(xmlProperties).entries) {
        serializer.startTag(NAMESPACE, PROPERTY)
        serializer.attribute(NAMESPACE, NAME, key)
        serializer.attribute(NAMESPACE, VALUE, value)
        serializer.endTag(NAMESPACE, PROPERTY)
    }
    serializer.endTag(NAMESPACE, PROPERTIES)
    for (previewResult in previewResults) {
        printTest(serializer, previewResult)
    }
    serializer.endTag(NAMESPACE, TESTSUITE)
}

@Throws(IOException::class)
private fun printTest(serializer: KXmlSerializer, result: PreviewResult) {
    serializer.startTag(NAMESPACE, TESTCASE)
    val lastPeriod = result.previewName.lastIndexOf(".")
    serializer.attribute(NAMESPACE, NAME, result.previewName.substring(lastPeriod+ 1))
    serializer.attribute(NAMESPACE, CLASSNAME, result.previewName.substring(0, lastPeriod))
    when (result.responseCode) {
        0 -> printImages(serializer, SUCCESS, result.message!!, result)
        1 -> printImages(serializer, FAILURE, result.message!!, result)
        2 -> printImages(serializer, FAILURE, result.message!!, result) //errors are not read by html report generator
    }

    serializer.endTag(NAMESPACE, TESTCASE)
}


private fun printImages(
    serializer: KXmlSerializer,
    tag: String,
    stack: String,
    result: PreviewResult
) {
    serializer.startTag(NAMESPACE, tag)
    serializer.text(stack)
    serializer.endTag(NAMESPACE, tag)
    serializer.startTag(NAMESPACE, IMAGES)
    serializer.startTag(NAMESPACE, GOLDEN)
    result.goldenImage!!.path?.let { serializer.attribute(NAMESPACE, PATH, it.toString()) }
    result.goldenImage.message?.let { serializer.attribute(NAMESPACE, MESSAGE, it) }
    serializer.endTag(NAMESPACE, GOLDEN)
    if (result.actualImage != null) {
        serializer.startTag(NAMESPACE, ACTUAL)
        result.actualImage.path?.let { serializer.attribute(NAMESPACE, PATH, it.toString()) }
        result.actualImage.message?.let { serializer.attribute(NAMESPACE, MESSAGE, it) }
        serializer.endTag(NAMESPACE, ACTUAL)
    }
    if (result.diffImage != null) {
        serializer.startTag(NAMESPACE, DIFF)
        result.diffImage.path?.let { serializer.attribute(NAMESPACE, PATH, it.toString()) }
        result.diffImage.message?.let { serializer.attribute(NAMESPACE, MESSAGE, it) }
        serializer.endTag(NAMESPACE, DIFF)
    }

    serializer.endTag(NAMESPACE, IMAGES)
}

private fun getPropertiesAttributes(xmlProperties: List<String>?): Map<String, String> {
    if (xmlProperties == null)
        return mapOf()
    val propertyMap = mutableMapOf<String, String>()
    for (p in xmlProperties) {
        val pair = p.split(COLON)
        propertyMap[pair[0]] = pair[1]
    }
    return propertyMap
}

/**
 * Creates the output stream to use for test results. Exposed for mocking.
 */
@Throws(IOException::class)
private fun createOutputResultStream(reportFilePath: String): OutputStream {
    val reportFile = File(reportFilePath)
    return BufferedOutputStream(FileOutputStream(reportFile))
}
