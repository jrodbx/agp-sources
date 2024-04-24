/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.builder.internal

import java.io.File
import java.io.IOException
import java.util.HashMap

abstract class TestManifestGenerator(
    private val outputFile: File,
    private val packageName: String,
    private val minSdkVersion: String?,
    private val targetSdkVersion: String?,
    private val testedPackageName: String,
    private val testRunnerName: String?,
) {

    companion object {
        private const val PH_PACKAGE = "#PACKAGE#"
        private const val PH_MIN_SDK_VERSION = "#MINSDKVERSION#"
        private const val PH_TARGET_SDK_VERSION = "#TARGETSDKVERSION#"
        private const val PH_TEST_RUNNER = "#TESTRUNNER#"
        private const val PH_TESTED_PACKAGE = "#TESTEDPACKAGE#"
    }

    open fun populateTemplateParameters(map: MutableMap<String, String?>) {
        map[PH_PACKAGE] = packageName
        map[PH_MIN_SDK_VERSION] = minSdkVersion ?: "1"
        map[PH_TARGET_SDK_VERSION] = targetSdkVersion ?: map[PH_MIN_SDK_VERSION]
        map[PH_TEST_RUNNER] = testRunnerName
        map[PH_TESTED_PACKAGE] = testedPackageName
    }

    abstract val templateResourceName: String

    @kotlin.jvm.Throws(IOException::class)
    fun generate() {
        val map: MutableMap<String, String?> = HashMap()
        populateTemplateParameters(map)

        val resource = TestManifestGenerator::class.java.getResource(templateResourceName)
        if (resource != null) {
            val urlConnection = resource.openConnection()
            urlConnection.useCaches = false
            val processor = TemplateProcessor(
                urlConnection.getInputStream(),
                map
            )
            processor.generate(outputFile)
        } else {
            throw RuntimeException("Cannot find template, please file a bug.")
        }
    }
}
