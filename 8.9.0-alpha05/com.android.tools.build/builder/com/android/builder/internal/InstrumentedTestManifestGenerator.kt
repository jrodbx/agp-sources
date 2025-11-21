/*
 * Copyright (C) 2012 The Android Open Source Project
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

/**
 * Generate an AndroidManifest.xml file for test projects.
 */
class InstrumentedTestManifestGenerator(
    outputFile: File,
    packageName: String,
    minSdkVersion: String?,
    targetSdkVersion: String?,
    testedPackageName: String,
    testRunnerName: String,
    private val handleProfiling: Boolean,
    private val functionalTest: Boolean
): TestManifestGenerator(outputFile, packageName, minSdkVersion, targetSdkVersion, testedPackageName, testRunnerName) {

    override fun populateTemplateParameters(map: MutableMap<String, String?>) {
        super.populateTemplateParameters(map)
        map[PH_HANDLE_PROFILING] = java.lang.Boolean.toString(handleProfiling)
        map[PH_FUNCTIONAL_TEST] = java.lang.Boolean.toString(functionalTest)
    }

    override val templateResourceName: String = TEMPLATE

    companion object {
        private const val TEMPLATE = "AndroidManifest.template"
        private const val PH_HANDLE_PROFILING = "#HANDLEPROFILING#"
        private const val PH_FUNCTIONAL_TEST = "#FUNCTIONALTEST#"
    }
}
