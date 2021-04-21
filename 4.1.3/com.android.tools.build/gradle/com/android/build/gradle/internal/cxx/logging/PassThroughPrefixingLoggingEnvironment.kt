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

import java.io.File

/**
 * [PassThroughDeduplicatingLoggingEnvironment] that attach a filename and/or tag string to the
 * message. The point of this is to issue errors that have an associated filename that the user
 * can click on in Android Studio.
 */
class PassThroughPrefixingLoggingEnvironment(
    val file : File? = null,
    val tag : String? = null,
    val treatWarningsAndErrorsAsInfo : Boolean = false)
    : PassThroughDeduplicatingLoggingEnvironment() {
    override fun log(message: LoggingMessage) {
        val newFile = message.file ?: file
        val newTag = message.tag ?: tag
        val newLevel =
            if (treatWarningsAndErrorsAsInfo) LoggingLevel.INFO else message.level
        super.log(message.copy(
            level = newLevel,
            file = newFile,
            tag = newTag
        ))
    }
}
