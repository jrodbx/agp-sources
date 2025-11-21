/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.utp.plugins.deviceprovider.profile.proto.DeviceProviderProfileProto
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent
import com.google.wireless.android.sdk.stats.DeviceTestSpanProfile
import com.google.wireless.android.sdk.stats.DeviceTestSpanProfile.TestProgressResult
import com.google.wireless.android.sdk.stats.TestRun
import java.io.File
import java.time.Clock
import java.time.Instant

/**
 * Class for managing test run result profiling and providing the proto for studio metrics.
 */
class UtpRunProfile internal constructor (
    outputDirectory: File,
    private val device: DeviceTestSpanProfile.DeviceType,
    private val profileId: String,
    private val deviceLock: Pair<Instant, Instant>?,
    private val clock: Clock) {

    // Device lock only exists for Gradle Managed Device Tasks.
    private val deviceLockSpan: TimeSpan? = deviceLock?.let {
        TimeSpan(clock).apply {
            recordFromInstants(it.first, it.second)
        }
    }
    private var utpSetupStartMs: Long = 0L
    private val deviceProvisionSpan: TimeSpan = TimeSpan(clock)
    private val testRunSpan: TimeSpan = TimeSpan(clock)
    private val deviceReleaseSpan: TimeSpan = TimeSpan(clock)
    private var resultStatus: TestProgressResult = TestProgressResult.UNKNOWN_RESULT

    private val profileFile: File =
        outputDirectory.resolve("profiling/${profileId}_profile.pb")

    fun listener() = TestResultListener()

    fun recordSetupStart() {
        utpSetupStartMs = clock.instant().toEpochMilli()
    }

    fun recordUtpRunFinished(result: UtpTestRunResult) {
        recordDeviceProviderProfileProtoFromUtpOutput()

        resultStatus = when {
            result.resultsProto == null -> TestProgressResult.UNKNOWN_FAILURE
            result.resultsProto.hasPlatformError() -> TestProgressResult.UTP_INFRASTRUCTURE_FAILURE
            else -> TestProgressResult.TESTS_COMPLETED
        }
    }

    private fun recordDeviceProviderProfileProtoFromUtpOutput() {
        if (!profileFile.exists()) {
            return
        }
        profileFile.inputStream().use { file ->
            val proto = DeviceProviderProfileProto.DeviceProviderProfile.parseFrom(file)
            deviceProvisionSpan.recordFromProtoSpan(proto.deviceProvision)
            deviceReleaseSpan.recordFromProtoSpan(proto.deviceRelease)
        }
    }

    fun toDeviceTestSpanProfileProto(): DeviceTestSpanProfile =
        DeviceTestSpanProfile.newBuilder().apply {
            deviceType = device
            testKind = TestRun.TestKind.INSTRUMENTATION_TEST
            processType = DeviceTestSpanProfile.ProcessType.EXTERNAL_UTP_PROCESS
            deviceLockSpan?.let {
                deviceLockWaitStartTimeMs = it.startMs
                deviceLockWaitDurationMs = it.durationMs
            }
            // We can't instrument the setup phase of UTP so we have to derive the time from when
            // the gradle worker starts to when the device provisioning starts.
            if (utpSetupStartMs != 0L && deviceProvisionSpan.isSet()) {
                utpSetupStartTimeMs = utpSetupStartMs
                utpSetupDurationMs = deviceProvisionSpan.startMs - utpSetupStartMs
            }
            deviceProvisionSpan.let {
                utpProvideDeviceStartTimeMs = it.startMs
                utpProvideDeviceDurationMs = it.durationMs
            }
            // We can't instrument the test setup of UTP (apk install, orchestrator, etc.) so we
            // must derive the time from when the device is provisioned to when the test run starts.
            if (deviceProvisionSpan.isSet() && testRunSpan.isSet()) {
                utpTestSetupStartTimeMs = deviceProvisionSpan.endMs
                utpTestSetupDurationMs = testRunSpan.startMs - deviceProvisionSpan.endMs
            }
            testRunSpan.let {
                utpTestRunStartTimeMs = it.startMs
                utpTestRunDurationMs = it.durationMs
            }
            deviceReleaseSpan.let {
                utpTearDownStartTimeMs = it.startMs
                utpTearDownDurationMs = it.durationMs
            }
            progressResult = resultStatus
        }.build()

    private class TimeSpan(private val clock: Clock) {

        internal var startMs: Long  = 0L
            private set

        internal var endMs: Long = 0L
            private set

        fun isSet(): Boolean = startMs != 0L && endMs != 0L

        fun recordStart() {
            startMs = clock.instant().toEpochMilli()
        }

        fun recordEnd() {
            endMs = clock.instant().toEpochMilli()
        }

        fun recordFromInstants(start: Instant, end: Instant) {
            startMs = start.toEpochMilli()
            endMs = end.toEpochMilli()
        }

        fun recordFromProtoSpan(span: DeviceProviderProfileProto.TimeSpan) {
            if (span.spanBeginMs == 0L) {
                return
            }
            startMs = span.spanBeginMs
            endMs = span.spanEndMs
        }

        val durationMs: Long
            get() = endMs - startMs
    }




    inner class TestResultListener: UtpTestResultListener {
        override fun onTestResultEvent(testResultEvent: TestResultEvent) {
            when (testResultEvent.stateCase) {
                TestResultEvent.StateCase.TEST_SUITE_STARTED ->
                    this@UtpRunProfile.testRunSpan.recordStart()
                TestResultEvent.StateCase.TEST_SUITE_FINISHED ->
                    this@UtpRunProfile.testRunSpan.recordEnd()
                else -> {}
            }
        }
    }
}
