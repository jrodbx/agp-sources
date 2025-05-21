/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.errors

import com.android.annotations.concurrency.Immutable
import com.android.build.gradle.internal.ide.SyncIssueImpl
import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.build.gradle.options.SyncOptions.ErrorFormatMode
import com.android.build.gradle.options.SyncOptions.EvaluationMode
import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.IssueReporter
import com.android.builder.model.SyncIssue
import com.android.ide.common.blame.Message
import com.google.common.base.MoreObjects
import com.google.common.collect.ImmutableList
import com.google.common.collect.Maps
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import javax.annotation.concurrent.GuardedBy

class SyncIssueReporterImpl(
    private val mode: EvaluationMode,
    errorFormatMode: ErrorFormatMode,
    logger: Logger
) : SyncIssueReporter() {

    @GuardedBy("this")
    private val _syncIssues = Maps.newHashMap<SyncIssueKey, SyncIssue>()

    @GuardedBy("this")
    private var handlerLocked = false

    private val messageReceiverImpl = MessageReceiverImpl(errorFormatMode, logger)

    override fun isInStandardEvaluationMode(): Boolean {
        return mode == EvaluationMode.STANDARD
    }

    @get:Synchronized
    override val syncIssues: ImmutableList<SyncIssue>
        get() = ImmutableList.copyOf(_syncIssues.values)

    @Synchronized
    private fun getAllIssuesAndClear(): ImmutableList<SyncIssue> {
        val issues = syncIssues
        _syncIssues.clear()
        return issues
    }

    private fun reportRemainingIssues() {
        lockHandler()
        val issues = getAllIssuesAndClear()
        var syncErrorToThrow: EvalIssueException? = null
        for (issue in issues) {
            when (issue.severity) {
                SyncIssue.SEVERITY_WARNING -> messageReceiverImpl.receiveMessage(Message(Message.Kind.WARNING, issue.message))
                SyncIssue.SEVERITY_ERROR -> {
                    val exception = EvalIssueException(issue.message, issue.data, issue.multiLineMessage)
                    if (syncErrorToThrow == null) {
                        syncErrorToThrow = exception
                    } else {
                        syncErrorToThrow.addSuppressed(exception)
                    }
                }
                else -> throw IllegalStateException("unexpected issue severity for $issue")
            }
        }
        if (syncErrorToThrow != null) {
            throw syncErrorToThrow
        }
    }

    @Synchronized
    override fun hasIssue(type: Type): Boolean {
        return _syncIssues.values.any { issue -> issue.type == type.type }
    }

    @Synchronized
    override fun reportIssue(
            type: Type,
            severity: Severity,
            exception: EvalIssueException) {
        val issue = SyncIssueImpl(type, severity, exception)
        when (mode) {
            EvaluationMode.STANDARD -> {
                if (severity.severity != SyncIssue.SEVERITY_WARNING) {
                    throw exception
                }
                messageReceiverImpl.receiveMessage(Message(Message.Kind.WARNING, exception.message))
            }

            EvaluationMode.IDE -> {
                if (handlerLocked) {
                    throw IllegalStateException("Issue registered after handler locked.", exception)
                }
                _syncIssues[syncIssueKeyFrom(issue)] = issue
            }
            else -> throw RuntimeException("Unknown SyncIssue type")
        }
    }

    @Synchronized
    override fun lockHandler() {
        handlerLocked = true
    }

    /**
     * Global, build-scope, sync issue reporter. This instance can be used from build services that
     * need to report sync issues, such as sdk build service.
     *
     * IMPORTANT: In order to avoid duplication of global build-wide sync issues, callers must
     * invoke [getAllIssuesAndClear] method. This will return list of global sync issues only once,
     * and any subsequent invocation will return an empty list.
     */
    abstract class GlobalSyncIssueService : BuildService<GlobalSyncIssueService.Parameters>,
        IssueReporter(), AutoCloseable {
        interface Parameters : BuildServiceParameters {
            val mode: Property<EvaluationMode>
            val errorFormatMode: Property<ErrorFormatMode>
        }

        private val reporter = SyncIssueReporterImpl(
            parameters.mode.get(),
            parameters.errorFormatMode.get(),
            Logging.getLogger(GlobalSyncIssueService::class.java)
        )

        /**
         * Returns all reported sync issues for the first invocation of the method. This is to avoid
         * duplication of sync issues across project when this is queried from the model builder.
         */
        fun getAllIssuesAndClear(): ImmutableList<SyncIssue> {
            return reporter.getAllIssuesAndClear()
        }

        override fun reportIssue(type: Type, severity: Severity, exception: EvalIssueException) {
            reporter.reportIssue(type, severity, exception)
        }

        override fun hasIssue(type: Type): Boolean = reporter.hasIssue(type)

        override fun close() {
            reporter.reportRemainingIssues()
        }

        class RegistrationAction(
                project: Project,
                private val evaluationMode: EvaluationMode,
                private val errorFormatMode: ErrorFormatMode) :
                ServiceRegistrationAction<GlobalSyncIssueService, Parameters>(
                        project,
                        GlobalSyncIssueService::class.java
                ) {

            override fun configure(parameters: Parameters) {
                parameters.mode.set(evaluationMode)
                parameters.errorFormatMode.set(errorFormatMode)
            }
        }
    }
}

/**
 * Creates a key from a SyncIssue to use in a map.
 */
private fun syncIssueKeyFrom(syncIssue: SyncIssue): SyncIssueKey {
    // If data is not available we use the message part to disambiguate between issues with the
    // same type.
    return SyncIssueKey(syncIssue.type, syncIssue.data ?: syncIssue.message)
}

@Immutable
internal data class SyncIssueKey constructor(
        private val type: Int,
        private val data: String) {

    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
                .add("type", type)
                .add("data", data)
                .toString()
    }
}
