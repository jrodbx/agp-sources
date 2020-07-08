/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.process

import com.android.ide.common.process.ProcessOutput
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * This class is needed by the gradle process executor. It provides stdout and stderr OutputStreams
 * that the will be used to capture output from a spawned process.
 */
class DefaultProcessOutput(
    val stderr : FileOutputStream,
    val stdout : FileOutputStream,
    private val outputStream : OutputStream,
    private val errorStream : OutputStream) : ProcessOutput {

    override fun getStandardOutput() = outputStream
    override fun getErrorOutput() = errorStream

    override fun close() {
        outputStream.flush()
        errorStream.flush()
        outputStream.close()
        errorStream.close()
    }
}