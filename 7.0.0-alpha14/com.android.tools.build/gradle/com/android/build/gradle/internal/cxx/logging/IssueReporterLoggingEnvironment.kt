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

import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.builder.errors.IssueReporter
import com.android.builder.errors.IssueReporter.Type.EXTERNAL_NATIVE_BUILD_CONFIGURATION
import org.gradle.api.logging.Logging

/**
 * A logging environment that will report errors and warnings to an [IssueReporter].
 * Messages are also logger to a standard [org.gradle.api.logging.Logger].
 */
class IssueReporterLoggingEnvironment private constructor(
    private val issueReporter: IssueReporter,
    private val internals: CxxDiagnosticCodesTrackingInternals? = null
) : PassThroughDeduplicatingLoggingEnvironment() {

    private class CxxDiagnosticCodesTrackingInternals(
        val analyticsService: AnalyticsService,
        val cxxConfigurationModel: CxxConfigurationModel,
        val cxxDiagnosticCodes: MutableList<Int>
    )

    constructor(issueReporter: IssueReporter) : this(issueReporter, null)

    constructor(
        issueReporter: IssueReporter,
        analyticsService: AnalyticsService,
        cxxConfigurationModel: CxxConfigurationModel,
    ) : this(
        issueReporter,
        CxxDiagnosticCodesTrackingInternals(
            analyticsService,
            cxxConfigurationModel,
            mutableListOf()
        )
    )

    private val logger = Logging.getLogger(IssueReporterLoggingEnvironment::class.java)

    override fun log(message: LoggingMessage) {
        when (message.level) {
            LoggingLevel.INFO -> logger.info(message.toString())
            LoggingLevel.LIFECYCLE -> logger.lifecycle(message.toString())
            LoggingLevel.WARN -> {
                message.diagnosticCode?.let { internals?.cxxDiagnosticCodes?.add(it.warningCode) }
                issueReporter.reportWarning(
                    EXTERNAL_NATIVE_BUILD_CONFIGURATION,
                    message.toString()
                )
                logger.warn(message.toString())
            }
            LoggingLevel.ERROR -> {
                message.diagnosticCode?.let { internals?.cxxDiagnosticCodes?.add(it.errorCode) }
                issueReporter.reportError(
                    EXTERNAL_NATIVE_BUILD_CONFIGURATION,
                    message.toString()
                )
                logger.error(message.toString())
            }
        }
    }

    override fun close() {
        try {
            if (internals != null) {
                val variant = internals.cxxConfigurationModel.variant
                val builder = internals.analyticsService.getVariantBuilder(
                    variant.module.gradleModulePathName,
                    variant.variantName
                ) ?: return
                builder.addAllCxxDiagnosticCodes(internals.cxxDiagnosticCodes)
            }
        }finally {
            super.close()
        }
    }
}
