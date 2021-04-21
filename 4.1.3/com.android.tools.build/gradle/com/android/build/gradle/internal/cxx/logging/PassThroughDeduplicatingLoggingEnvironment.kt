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

import com.android.build.gradle.internal.cxx.json.PlainFileGsonTypeAdaptor
import com.google.gson.GsonBuilder
import java.io.File

/**
 * [ThreadLoggingEnvironment] that will deduplicate messages and then forward to a parent
 * logger.
 */
open class PassThroughDeduplicatingLoggingEnvironment : ThreadLoggingEnvironment() {
    private val messages : MutableSet<LoggingMessage> = linkedSetOf() // Linked set to preserve orcer
    private val parent : LoggingEnvironment = parentLogger()

    override fun log(message: LoggingMessage) {
        if (messages.contains(message)) return
        parent.log(message)
        messages.add(message)
    }

    /**
     * true if there was atleast one error.
     */
    fun hadErrors() = messages.any { it.level == LoggingLevel.ERROR }

    /**
     * The errors that have been seen so far.
     */
    val errors get() = messages.filter { it.level == LoggingLevel.ERROR }.map { it.toString() }

    /**
     * The warnings that have been seen so far.
     */
    val warnings get() = messages.filter { it.level == LoggingLevel.WARN }.map { it.toString() }

    /**
     * The infos that have been seen so far.
     */
    val infos get() = messages.filter { it.level == LoggingLevel.INFO }.map { it.toString() }

    /**
     * Total message count so far.
     */
    val messageCount get() = messages.size

    /**
     * The logging record so far. Returns an immutable copy.
     */
    val record get() = messages.toList()


}

/**
 * Render a list of [LoggingMessage] as a JSON string.
 */
fun List<LoggingMessage>.toJsonString() = GsonBuilder()
    .registerTypeAdapter(File::class.java, PlainFileGsonTypeAdaptor())
    .setPrettyPrinting()
    .create()
    .toJson(this)!!