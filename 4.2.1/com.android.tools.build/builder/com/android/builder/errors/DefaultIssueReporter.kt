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

package com.android.builder.errors

import com.android.utils.ILogger

/**
 * Basic implementation of [IssueReporter] that throws errors and log warnings to a
 * provider [ILogger.warning]
 */
class DefaultIssueReporter(
    private val logger: ILogger
) : IssueReporter() {
    private val warnings = mutableSetOf<Type>()
    override fun reportIssue(type: Type, severity: Severity, exception: EvalIssueException) {
        if (severity == Severity.ERROR) {
            throw exception
        }

        // record warnings
        // since this reporter always throws on error, there's no need to record errors as
        // code that check for them would not be reachable.
        warnings.add(type)
        val stringBuilder = StringBuilder(exception.message)
        exception.multlineMessage?.joinTo(buffer = stringBuilder, separator = "\n")
        logger.warning(stringBuilder.toString())
    }

    override fun hasIssue(type: Type): Boolean = warnings.contains(type)
}