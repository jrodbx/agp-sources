/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.api.testsuites

/**
 * Definition of a single property that is passed from the AGP Test task to the junit engine
 * configured for the test suite.
 */
data class TestEngineInputProperty(val name: String, val value: String) {
    companion object {

        /**
         * List of source folders to find tests in, separated by [java.io.File.separator]
         */
        const val SOURCE_FOLDERS = "com.android.junit.engine.source.folders"

        /**
         * List of binary folders to find tests in, separated by [java.io.File.separator]
         */
        const val BINARY_FOLDERS = "com.android.junit.engine.binary.folders"

        /**
         * Path to a file location to use as the logging output.
         */
        const val LOGGING_FILE = "com.android.junit.engine.logging.file"

        /**
         * Path to a file location to use to stream results back to the Test task
         */
        const val STREAMING_FILE = "com.android.junit.engine.results.streaming.file"

        /**
         * Path to the results directory
         */
        const val RESULTS_DIR = "com.android.junit.engine.results.dir"

        /**
         * Path to the coverage data directory
         */
        const val COVERAGE_DIR = "com.android.junit.engine.coverage.dir"

        /**
         * Serial IDs to deploy to
         */
        const val SERIAL_IDS = "com.android.junit.engine.serial.ids"

        /**
         * Tested application ID
         */
        const val TESTED_APPLICATION_ID = "com.android.junit.engine.tested.application.id"
    }
}
