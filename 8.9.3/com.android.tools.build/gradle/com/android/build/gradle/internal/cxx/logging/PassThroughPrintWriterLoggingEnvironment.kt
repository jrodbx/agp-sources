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

import java.io.PrintWriter

/**
 * [ThreadLoggingEnvironment] that will write lines to a [PrintWriter] and then forward to a parent
 * logger.
 */
class PassThroughPrintWriterLoggingEnvironment(
    val log : PrintWriter,
    val prefix : String)
    : PassThroughRecordingLoggingEnvironment() {
    private val parent : LoggingEnvironment = parentLogger()

    override fun log(message: LoggingMessage) {
        log.println(prefix + message.toString())
        parent.log(message)
    }

    override fun close() {
        super.close()
        log.close()
    }
}
