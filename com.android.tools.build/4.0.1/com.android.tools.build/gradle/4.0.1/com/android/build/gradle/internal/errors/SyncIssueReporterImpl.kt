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
import com.android.build.gradle.options.SyncOptions.EvaluationMode
import com.android.builder.errors.EvalIssueException
import com.android.builder.model.SyncIssue
import com.google.common.base.MoreObjects
import com.google.common.collect.ImmutableList
import com.google.common.collect.Maps
import org.gradle.api.logging.Logger
import javax.annotation.concurrent.GuardedBy

class SyncIssueReporterImpl(
        private val mode: EvaluationMode,
        private val logger: Logger)
    : SyncIssueReporter() {

    @GuardedBy("this")
    private val _syncIssues = Maps.newHashMap<SyncIssueKey, SyncIssue>()

    @GuardedBy("this")
    private var handlerLocked = false

    @get:Synchronized
    override val syncIssues: ImmutableList<SyncIssue>
        get() = ImmutableList.copyOf(_syncIssues.values)

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
                logger.warn("WARNING: " + exception.message)
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
