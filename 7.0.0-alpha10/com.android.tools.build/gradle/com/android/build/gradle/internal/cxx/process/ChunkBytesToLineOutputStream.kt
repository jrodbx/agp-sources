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

import java.io.OutputStream
import java.nio.charset.Charset
import java.util.Arrays

/**
 * This OutputStream receives bytes and splits them into lines which are then sent to the 'print'
 * function. This class accounts for lines that span multiple write(byte[], int, int) blocks.
 */
class ChunkBytesToLineOutputStream(
    private val logPrefix: String,
    private val print: (String) -> Unit,
    initialBufferSize : Int = 256) : OutputStream() {
    private var buffer = ByteArray(initialBufferSize)
    private var nextByteIndex = 0

    override fun write(b: ByteArray, off: Int, len: Int) {
        for (i in 0 until len) {
            val value = b[off + i].toInt()
            // The reason this doesn't double the presented linebreaks is because
            // in the \r\n case writeBufferToInfo() exits without emitting a linebreak
            // when byteCount accumulated by writeByteToBuffer() is still zero. However,
            // a single \r or \n will still emit a linebreak.
            if (value == '\r'.toInt() || value == '\n'.toInt()) {
                writeBufferToInfo()
            } else {
                writeByteToBuffer(value)
            }
        }
    }

    override fun write(b: Int) {
        throw RuntimeException("Intentionally not implemented. " +
                "Use write(byte[], int, int) for performance")
    }

    override fun close() {
        writeBufferToInfo()
    }

    private fun writeByteToBuffer(b: Int) {
        if (nextByteIndex == buffer.size) {
            buffer = buffer.copyOf(buffer.size * 2)
        }
        buffer[nextByteIndex] = b.toByte()
        nextByteIndex++
    }

    private fun writeBufferToInfo() {
        if (nextByteIndex == 0) {
            return
        }
        val line =
            String(buffer, 0, nextByteIndex, Charset.forName("UTF-8"))

        print(logPrefix + line)
        nextByteIndex = 0
    }
}

