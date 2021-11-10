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

import com.android.ddmlib.testrunner.ITestRunListener
import com.android.ddmlib.testrunner.TestIdentifier
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent.StateCase
import com.google.protobuf.Any
import com.google.protobuf.Message
import com.google.testing.platform.proto.api.core.TestCaseProto
import com.google.testing.platform.proto.api.core.TestResultProto
import com.google.testing.platform.proto.api.core.TestStatusProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto

/**
 * An adapter which converts Ddmlib's [ITestRunListener] interface into [UtpTestResultListener].
 */
class DdmlibTestResultAdapter(
        private val ddmlibTestRunName: String,
        private val ddmlibTestResultListener: ITestRunListener) : UtpTestResultListener {

    private var numTestFails: Int = 0
    private var startTimestamp: Long = 0L

    override fun onTestResultEvent(testResultEvent: GradleAndroidTestResultListenerProto.TestResultEvent) {
        when(testResultEvent.stateCase) {
            StateCase.TEST_SUITE_STARTED -> {
                startTimestamp = System.currentTimeMillis()
                val testSuite: TestSuiteResultProto.TestSuiteMetaData =
                        testResultEvent.testSuiteStarted.testSuiteMetadata.unpack()
                ddmlibTestResultListener.testRunStarted(
                        ddmlibTestRunName,
                        testSuite.scheduledTestCaseCount)
            }

            StateCase.TEST_CASE_STARTED -> {
                val testCase: TestCaseProto.TestCase =
                        testResultEvent.testCaseStarted.testCase.unpack()
                val testId = TestIdentifier(
                        "${testCase.testPackage}.${testCase.testClass}", testCase.testMethod)
                ddmlibTestResultListener.testStarted(testId)
            }

            StateCase.TEST_CASE_FINISHED -> {
                val testResult: TestResultProto.TestResult =
                        testResultEvent.testCaseFinished.testCaseResult.unpack()
                val testId = TestIdentifier(
                        "${testResult.testCase.testPackage}.${testResult.testCase.testClass}",
                        testResult.testCase.testMethod)

                when(testResult.testStatus) {
                    TestStatusProto.TestStatus.FAILED, TestStatusProto.TestStatus.ERROR -> {
                        ddmlibTestResultListener.testFailed(testId, testResult.error.stackTrace)
                        ++numTestFails
                    }
                    TestStatusProto.TestStatus.IGNORED -> {
                        ddmlibTestResultListener.testIgnored(testId)
                    }
                    else -> {}
                }
                ddmlibTestResultListener.testEnded(testId, mapOf())
            }

            StateCase.TEST_SUITE_FINISHED -> {
                if (numTestFails > 0) {
                    ddmlibTestResultListener.testRunFailed("There was $numTestFails failure(s).")
                }
                ddmlibTestResultListener.testRunEnded(
                        System.currentTimeMillis() - startTimestamp,
                        mapOf()
                )
            }

            else -> {}
        }
    }
}

private inline fun <reified T : Message> Any.unpack(): T = unpack(T::class.java)
