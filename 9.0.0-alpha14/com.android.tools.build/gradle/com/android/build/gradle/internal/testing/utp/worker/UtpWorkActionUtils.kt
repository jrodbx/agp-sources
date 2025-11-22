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

package com.android.build.gradle.internal.testing.utp.worker

import com.android.prefs.AndroidLocationsSingleton
import com.google.testing.platform.proto.api.core.ErrorDetailProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import java.io.File

/**
 * Creates an empty temporary file for UTP in Android Preference directory.
 */
fun createUtpTempFile(fileNamePrefix: String, fileNameSuffix: String): File {
    val utpPrefRootDir = getUtpPreferenceRootDir()
    return File.createTempFile(fileNamePrefix, fileNameSuffix, utpPrefRootDir).apply {
        deleteOnExit()
    }
}

/**
 * Creates an empty temporary directory for UTP in Android Preference directory.
 */
fun createUtpTempDirectory(dirNamePrefix: String): File {
    val utpPrefRootDir = getUtpPreferenceRootDir()
    return java.nio.file.Files.createTempDirectory(
        utpPrefRootDir.toPath(), dirNamePrefix).toFile().apply {
        deleteOnExit()
    }
}

/**
 * Returns the UTP preference root directory. Typically it is "~/.android/utp". If the preference
 * directory doesn't exist, it creates and returns it.
 */
fun getUtpPreferenceRootDir(): File {
    val utpPrefRootDir = File(AndroidLocationsSingleton.prefsLocation.toFile(), "utp")
    if (!utpPrefRootDir.exists()) {
        utpPrefRootDir.mkdirs()
    }
    return utpPrefRootDir
}

private const val UNKNOWN_PLATFORM_ERROR_MESSAGE =
    "Unknown platform error occurred when running the UTP test suite. Please check logs for details."

/**
 * Finds the root cause of the Platform Error and returns the error message.
 */
fun getPlatformErrorMessage(resultsProto: TestSuiteResultProto.TestSuiteResult?): String {
    resultsProto ?: return UNKNOWN_PLATFORM_ERROR_MESSAGE
    return resultsProto.platformError.errorsList.joinToString(
        "\n", transform = ::getPlatformErrorMessage)
}

/**
 * Finds the root cause of the Platform Error and returns the error message.
 *
 * @param error the top level error detail to be analyzed.
 */
private fun getPlatformErrorMessage(
    error : ErrorDetailProto.ErrorDetail,
    errorMessageBuilder: StringBuilder = StringBuilder()) : StringBuilder {
    if (error.hasCause()) {
        if (error.summary.errorMessage.isNotBlank()) {
            errorMessageBuilder.append("${error.summary.errorMessage}\n")
        }
        getPlatformErrorMessage(error.cause, errorMessageBuilder)
    } else {
        if (error.summary.errorMessage.isNotBlank()) {
            errorMessageBuilder.append("${error.summary.errorMessage}\n")
        } else {
            errorMessageBuilder.append("$UNKNOWN_PLATFORM_ERROR_MESSAGE\n")
        }
        errorMessageBuilder.append(error.summary.stackTrace)
    }
    return errorMessageBuilder
}
