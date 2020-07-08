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
import com.android.build.gradle.internal.cxx.logging.LoggingLevel.ERROR
import com.android.build.gradle.internal.cxx.logging.LoggingLevel.INFO
import com.android.build.gradle.internal.cxx.logging.LoggingLevel.WARN
import java.io.File

data class LoggingMessage(
    val level : LoggingLevel,
    val message : String,
    val file : File? = null,
    val tag : String? = null) {
    override fun toString() = when {
        (file == null && tag == null) -> message
        (file != null && tag == null) -> "$file : C/C++ : $message"
        (file == null && tag != null) -> "C/C++ $tag : $message"
        else -> "$file : C/C++ $tag : $message"
    }
}

fun LoggingLevel.recordOf(message : String) = LoggingMessage(this, message)

fun errorRecordOf(message : String) = ERROR.recordOf(message)
fun warnRecordOf(message : String) = WARN.recordOf(message)
fun infoRecordOf(message : String) = INFO.recordOf(message)
