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

import com.android.build.gradle.internal.cxx.logging.lifecycleln
import com.android.ide.common.process.ProcessOutput
import com.android.ide.common.process.ProcessOutputHandler
import com.android.utils.cxx.process.LineOutputStream
import com.android.utils.cxx.process.NativeBuildOutputClassifier
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * This class is needed by the gradle process executor. It handles stdout and stderr from a process.
 * The handleOutput function is not used because the streams are evaluated interactively as the
 * process executes.
 */
class DefaultProcessOutputHandler(
    private val stderrFile: File,
    private val stdoutFile: File,
    private val logPrefix: String,
    private val logStderr: Boolean,
    private val logStdout: Boolean,
    private val logFullStdout: Boolean,
) : ProcessOutputHandler {

    var stderr: FileOutputStream? = null
    var stdout: FileOutputStream? = null

    override fun createOutput(): ProcessOutput {
        val singleStderr = FileOutputStream(stderrFile, true)
        val singleStdout = FileOutputStream(stdoutFile, true)
        val stderrReceivers = mutableListOf<OutputStream>(singleStderr)
        val stdoutReceivers = mutableListOf<OutputStream>(singleStdout)
        if (logStderr) {
            stderrReceivers.add(ChunkBytesToLineOutputStream(logPrefix, LifecycleLineOutputStream()))
        }
        if (logStdout) {
            if (logFullStdout) {
                stdoutReceivers.add(ChunkBytesToLineOutputStream(logPrefix, LifecycleLineOutputStream()))
            } else {
                stdoutReceivers.add(ChunkBytesToLineOutputStream(logPrefix,
                    NativeBuildOutputClassifier { message ->
                        message.lines.forEach(::lifecycleln)
                    }))
            }
        }
        return DefaultProcessOutput(
            singleStderr,
            singleStdout,
            MultiplexingOutputStream(stdoutReceivers), MultiplexingOutputStream(stderrReceivers))
    }

    override fun handleOutput(processOutput: ProcessOutput) {
        if (stdout != null) {
            throw RuntimeException("Multiple calls")
        }
        val output = (processOutput as DefaultProcessOutput)
        stderr = output.stderr
        stdout = output.stdout
    }
}

class LifecycleLineOutputStream : LineOutputStream {
    override fun consume(line: String) = lifecycleln(line)
    override fun close() { }
}
