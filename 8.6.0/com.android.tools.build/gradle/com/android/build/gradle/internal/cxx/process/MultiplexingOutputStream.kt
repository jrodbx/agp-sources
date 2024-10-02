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

/**
 * This class accepts write(byte[], int, int) calls and forwards them to multiple other streams.
 * It will throw an exception if write(byte) is called to prevent callers from using a slow
 * pattern.
 */
class MultiplexingOutputStream(private val streams: List<OutputStream>) : OutputStream() {

    override fun write(b: ByteArray, off: Int, len: Int) {
        for (stream in streams) {
            stream.write(b, off, len)
        }
    }

    override fun write(b: Int) {
        throw RuntimeException(
            "If single byte write is needed then a "
                    + "buffered output stream should be used to wrap "
                    + "MultiplexingOutputStream"
        )
    }

    override fun flush() {
        for (stream in streams) {
            stream.flush()
        }
    }

    override fun close() {
        for (stream in streams) {
            stream.close()
        }
    }
}