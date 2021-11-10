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

package com.android.build.gradle.internal.testing.utp

import com.google.testing.platform.proto.api.core.PlatformErrorProto
import com.google.testing.platform.proto.api.core.TestCaseProto
import com.google.testing.platform.proto.api.core.TestStatusProto
import com.google.testing.platform.proto.api.core.TestStatusProto.TestStatus
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult

/**
 * Merges multiple test suite results into a single test suite result proto message.
 */
class UtpTestSuiteResultMerger {
    private val builder: TestSuiteResult.Builder = TestSuiteResult.newBuilder()

    /**
     * Returns the merged test suite result.
     */
    val result: TestSuiteResult
        get() = builder.build()

    /**
     * Merges a given test suite result.
     */
    fun merge(testSuiteResult: TestSuiteResult) {
        mergeTestSuiteMetaData(testSuiteResult.testSuiteMetaData)
        mergeTestStatus(testSuiteResult.testStatus)
        builder.addAllTestResult(testSuiteResult.testResultList)
        mergePlatformError(testSuiteResult.platformError)
        builder.addAllOutputArtifact(testSuiteResult.outputArtifactList)
        builder.addAllIssue(testSuiteResult.issueList)
    }

    private fun mergePlatformError(platformError: PlatformErrorProto.PlatformError) {
        if (!builder.platformError.hasErrorDetail() && platformError.hasErrorDetail()) {
            builder.platformErrorBuilder.errorDetail = platformError.errorDetail
        }
    }

    private fun mergeTestSuiteMetaData(metadata: TestSuiteResultProto.TestSuiteMetaData) {
        metadata.testSuiteName.let {
            if (it.isNotBlank()) {
                builder.testSuiteMetaDataBuilder.testSuiteName = it
            }
        }
        builder.testSuiteMetaDataBuilder.scheduledTestCaseCount += metadata.scheduledTestCaseCount
    }

    private fun mergeTestStatus(testStatus: TestStatus) {
        builder.testStatus = when(builder.testStatus) {
            TestStatus.TEST_STATUS_UNSPECIFIED,
            TestStatus.UNRECOGNIZED,
            TestStatus.SKIPPED,
            TestStatus.IGNORED,
            TestStatus.STARTED -> {
                testStatus
            }
            TestStatus.PASSED -> {
                when(testStatus) {
                    TestStatus.TEST_STATUS_UNSPECIFIED,
                    TestStatus.UNRECOGNIZED,
                    TestStatus.SKIPPED,
                    TestStatus.IGNORED,
                    TestStatus.STARTED-> {
                        builder.testStatus
                    }
                    else -> {
                        testStatus
                    }
                }
            }
            TestStatus.FAILED -> {
                builder.testStatus
            }
            TestStatus.ERROR,
            TestStatus.ABORTED,
            TestStatus.CANCELLED -> {
                testStatus
            }
        }
    }
}
