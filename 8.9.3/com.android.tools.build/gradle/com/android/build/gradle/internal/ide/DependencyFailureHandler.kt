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

package com.android.build.gradle.internal.ide

import com.android.builder.errors.IssueReporter
import com.android.builder.errors.IssueReporter.Type
import com.google.common.base.Splitter
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ImmutableList
import com.google.common.collect.ListMultimap
import java.util.regex.Pattern

private val pattern = Pattern.compile(".*any matches for (\\S*) .*", Pattern.DOTALL)
private val pattern2 = Pattern.compile(".*Could not find (\\S*)\\..*", Pattern.DOTALL)
private val LINE_SPLITTER = Splitter.on(System.lineSeparator())

class DependencyFailureHandler {

    private val failures: ListMultimap<String, Throwable> = ArrayListMultimap.create()

    fun addErrors(name: String, throwables: Collection<Throwable>): DependencyFailureHandler {
        throwables.forEach { t ->
            failures.put(name, t)
        }
        return this
    }

    fun registerIssues(issueReporter: IssueReporter) {
        for ((key, value) in failures.entries()) {
            processDependencyThrowable(
                value,
                { message -> checkForData(message) },
                { data, messages ->
                    if (data != null) {
                        issueReporter.reportError(
                            Type.UNRESOLVED_DEPENDENCY,
                            "Unable to resolve dependency $data",
                            data)
                    } else {
                        issueReporter.reportError(
                            Type.UNRESOLVED_DEPENDENCY,
                            "Unable to resolve dependency for '$key': ${messages[0]}",
                            null,
                            messages)
                    }
                }
            )
        }
    }
}

private fun processDependencyThrowable(
        throwable: Throwable,
        dataExtractor: (String) -> String?,
        resultConsumer: (String?, List<String>) -> Unit) {

    var cause: Throwable? = throwable

    // gather all the messages.
    val messages = mutableListOf<String>()
    var firstIndent = " > "
    var allIndent = ""

    var data: String? = null

    while (cause != null) {
        val message = cause.message
        if (message != null) {
            val lines = ImmutableList.copyOf<String>(LINE_SPLITTER.split(message))

            // check if the first line contains a data we care about
            data = dataExtractor.invoke(lines[0])

            if (data != null) {
                break
            }

            // add them to the main list
            var i = 0
            val count = lines.size
            while (i < count) {
                val line = lines[i]

                when {
                    allIndent.isEmpty() -> messages.add(line)
                    i == 0 -> messages.add(firstIndent + line)
                    else -> messages.add(allIndent + line)
                }
                i++
            }


            firstIndent = allIndent + firstIndent
            allIndent += "   "
        }

        cause = cause.cause
    }

    resultConsumer.invoke(data, messages)
}

internal fun checkForData(message: String): String? {
    var m = pattern.matcher(message)
    if (m.matches()) {
        return m.group(1)
    }

    m = pattern2.matcher(message)
    if (m.matches()) {
        return m.group(1)
    }

    return null
}