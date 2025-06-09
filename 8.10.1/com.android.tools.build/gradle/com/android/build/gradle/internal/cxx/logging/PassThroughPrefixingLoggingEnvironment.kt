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

import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.INFO
import java.io.File

/**
 * [PassThroughRecordingLoggingEnvironment] that attach a filename and/or tag string to the
 * message. The point of this is to issue errors that have an associated filename that the user
 * can click on in Android Studio.
 */
class PassThroughPrefixingLoggingEnvironment(
    val file : File? = null,
    val tag : String? = null,
    val treatAllMessagesAsInfo : Boolean = false)
    : PassThroughRecordingLoggingEnvironment() {
    override fun log(message: LoggingMessage) {
        val builder = message.toBuilder()
        if (message.file.isBlank() && file != null) builder.file = file.path
        if (message.tag.isBlank() &&  tag != null) builder.tag = tag
        if (treatAllMessagesAsInfo) builder.level = INFO
        super.log(builder.build())
    }
}
