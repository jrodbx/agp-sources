/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.builder.profile

import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.CommonMetricsData
import com.android.tools.analytics.Environment
import com.android.tools.analytics.UsageTracker
import com.android.utils.ILogger
import com.android.utils.PathUtils
import com.android.utils.StdLogger
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleBuildProfile
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * [AnalyticsProfileWriter] is used to initialize analytics library, write analytics data and
 * de-initialize analytics library.
 */
class AnalyticsProfileWriter {

    private val profileFileName = DateTimeFormatter.ofPattern(
        "'profile-'yyyy-MM-dd-HH-mm-ss-SSS'.rawproto'", Locale.US)
    private val scheduledExecutorService: ScheduledExecutorService
            = Executors.newScheduledThreadPool(1)
    private val logger = StdLogger(StdLogger.Level.INFO)

    fun writeAndFinish(
        profile: GradleBuildProfile,
        events: List<AndroidStudioEvent.Builder>,
        environment: Environment,
        profileDir: File?,
        enableProfileJson: Boolean
    ) {
        if (profileDir == null) {
            scheduledExecutorService.submit {
                writeAnalytics(
                    profile,
                    events,
                    environment,
                    enableProfileJson,
                    profileDir
                )
            }
        } else {
            writeAnalytics(
                profile,
                events,
                environment,
                enableProfileJson,
                profileDir
            )
        }
    }

    private fun writeAnalytics(
        profile: GradleBuildProfile,
        events: List<AndroidStudioEvent.Builder>,
        environment: Environment,
        enableChromeTracingOutput: Boolean,
        profileDir: File?
    ) {
        synchronized(gate) {
            initializeAnalytics(logger, scheduledExecutorService, environment)

            UsageTracker.log(
                AndroidStudioEvent.newBuilder()
                    .setCategory(AndroidStudioEvent.EventCategory.GRADLE)
                    .setKind(AndroidStudioEvent.EventKind.GRADLE_BUILD_PROFILE)
                    .setGradleBuildProfile(profile)
                    .setJavaProcessStats(CommonMetricsData.javaProcessStats)
                    .setJvmDetails(CommonMetricsData.jvmDetails)
            )
            events.forEach {
                UsageTracker.log(it)
            }
            val outputFile
                    = profileDir?.toPath()?.resolve(profileFileName.format(LocalDateTime.now()))
            if (outputFile != null) {
                // Write benchmark file into build directory
                Files.createDirectories(outputFile.parent)
                BufferedOutputStream(
                    Files.newOutputStream(outputFile, StandardOpenOption.CREATE_NEW))
                    .use { outputStream -> profile.writeTo(outputStream) }
                if (enableChromeTracingOutput) {
                    ChromeTracingProfileConverter.toJson(outputFile)
                }
            }

            deInitializedAnalytics(profileDir)
        }
    }

    private fun initializeAnalytics(
        logger: ILogger,
        eventLoop: ScheduledExecutorService,
        environment: Environment
    ) {
        UsageTracker.initialize(eventLoop)
        UsageTracker.setMaxJournalTime(10, TimeUnit.MINUTES)
        UsageTracker.maxJournalSize = 1000
    }

    private fun deInitializedAnalytics(profileDir: File?) {
        UsageTracker.deinitialize()
        cleanUpExtraChromeTraceDirectory(profileDir)
        scheduledExecutorService.shutdown()
    }

    private fun cleanUpExtraChromeTraceDirectory(profileDir: File?) {
        val profileDirPath = profileDir?.toPath()
        if (profileDirPath != null) {
            // Proactively delete the folder containing extra chrome traces to be merged.
            val extraChromeTracePath = profileDirPath.resolve(
                ChromeTracingProfileConverter.EXTRA_CHROME_TRACE_DIRECTORY
            )
            try {
                PathUtils.deleteRecursivelyIfExists(extraChromeTracePath)
            } catch (e: IOException) {
                StdLogger(StdLogger.Level.WARNING).warning(
                    "Cannot extra Chrome trace directory $extraChromeTracePath. The generated" +
                            "Chrome trace file may contain stale data.",
                    e
                )
            }
        }
    }

    companion object {
        // the gate is used to ensure analytics library initialization and de-initialization won't
        // happen concurrently in different threads.
        val gate = Any()
    }
}
