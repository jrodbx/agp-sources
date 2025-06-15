/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.logging

import com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.BUG
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.ERROR
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.INFO
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.LIFECYCLE
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.UNRECOGNIZED
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.WARN
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.build.gradle.internal.cxx.string.StringEncoder
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.builder.errors.IssueReporter
import com.android.builder.errors.IssueReporter.Type.EXTERNAL_NATIVE_BUILD_CONFIGURATION
import com.google.protobuf.GeneratedMessageV3
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.logging.Logging
import java.io.File

/**
 * A logging environment that will report errors and warnings to an [IssueReporter].
 * Messages are also logger to a standard [org.gradle.api.logging.Logger].
 */
class IssueReporterLoggingEnvironment private constructor(
    private val issueReporter: IssueReporter,
    rootBuildGradleFolder: File,
    private val cxxFolder: File?,
    allowStructuredLogging: Boolean,
    private val metrics: GradleBuildVariant.Builder?
) : PassThroughRecordingLoggingEnvironment() {
    private val structuredLogEncoder : CxxStructuredLogEncoder?
    init {
        // Structured log is only written if user has manually created a folder
        // for it to go into.
        val structuredLogFolder = getCxxStructuredLogFolder(rootBuildGradleFolder)
        structuredLogEncoder = if (allowStructuredLogging && structuredLogFolder.isDirectory) {
            val log = structuredLogFolder.resolve(
                "log_${System.currentTimeMillis()}_${Thread.currentThread().id}.bin")
            CxxStructuredLogEncoder(log)
        } else {
            null
        }
    }

    constructor(
        issueReporter: IssueReporter,
        rootBuildGradleFolder: File,
        cxxFolder: File?,
        allowStructuredLogging: Boolean
        ) : this(issueReporter, rootBuildGradleFolder, cxxFolder, allowStructuredLogging, null)

    constructor(
        issueReporter: IssueReporter,
        analyticsService: AnalyticsService,
        variant: CxxVariantModel,
        allowStructuredLogging: Boolean = true
    ) : this(
        issueReporter,
        variant.module.project.rootBuildGradleFolder,
        variant.module.cxxFolder,
        allowStructuredLogging,
        analyticsService.getVariantBuilder(
            variant.module.gradleModulePathName,
            variant.variantName
        )
    )

    private val logger = Logging.getLogger(IssueReporterLoggingEnvironment::class.java)

    override fun log(message: LoggingMessage) {
        logStructured { strings ->
            val encoded = EncodedLoggingMessage.newBuilder()
                .setLevel(message.level)
                .setMessageId(strings.encode(message.message))
                .setDiagnosticCode(message.diagnosticCode)
            if (message.file != null) {
                encoded.fileId = strings.encode(message.file)
            }
            if (message.tag != null) {
                encoded.tagId = strings.encode(message.tag)
            }
            encoded.build()
        }
        when (message.level) {
            INFO -> logger.info(message.text())
            LIFECYCLE -> logger.lifecycle(message.text())
            WARN -> {
                metrics?.addCxxDiagnosticCodes(message.diagnosticCode)
                issueReporter.reportWarning(
                    EXTERNAL_NATIVE_BUILD_CONFIGURATION,
                    message.text()
                )
                logger.warn(message.text())
            }
            ERROR -> {
                metrics?.addCxxDiagnosticCodes(message.diagnosticCode)
                issueReporter.reportError(
                    EXTERNAL_NATIVE_BUILD_CONFIGURATION,
                    message.text()
                )
                logger.error(message.text())
            }
            BUG -> {
                metrics?.addCxxDiagnosticCodes(message.diagnosticCode)
                val sb = StringBuilder(message.text() + " ")
                sb.append("Please refer to bug https://issuetracker.google.com/${message.diagnosticCode} for more information. \n")
                if (cxxFolder != null) {
                    sb.append("If possible, please also attach a zipped copy of $cxxFolder to the bug to assist in diagnosing the issue. \n")
                }
                sb.append("The current Android Gradle Plugin Version is $ANDROID_GRADLE_PLUGIN_VERSION.\n")
                issueReporter.reportError(
                    EXTERNAL_NATIVE_BUILD_CONFIGURATION,
                    sb.toString()
                )
                logger.error(sb.toString())
            }
            UNRECOGNIZED -> {}
        }
    }

    override fun logStructured(message: (StringEncoder) -> GeneratedMessageV3) {
        structuredLogEncoder?.write(message(structuredLogEncoder))
    }

    override fun close() {
        try {
            if (structuredLogEncoder != null) {
                infoln("Closing '${structuredLogEncoder.file}'")
                structuredLogEncoder.close()
            }
        } finally {
            super.close()
        }
    }
}

/**
 * Given a root project folder, return the structured log folder.
 */
fun getCxxStructuredLogFolder(rootBuildGradleFolder : File) : File {
    return rootBuildGradleFolder.resolve(".cxx/structured-log")
}
