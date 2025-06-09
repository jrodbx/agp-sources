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
@file:JvmName("NonClosingStreams")

package com.android.io

import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream

private class NonClosingOutputStream(out: OutputStream) : FilterOutputStream(out) {
    override fun close() {
        flush()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
    }
}

/**
 * Wrap the given [OutputStream] to avoid closing the underlying output stream.
 *
 * When [close] is requested, only [flush] is called on the underlying stream.
 */
fun OutputStream.nonClosing(): OutputStream = NonClosingOutputStream(this)

/**
 * Wrap the given `InputStream` to avoid closing the underlying stream.
 *
 * When [close] is requested, nothing happens to the underlying stream.
 *
 * See [NonClosingInputStream]
 */
fun InputStream.nonClosing(): InputStream {
    return NonClosingInputStream(this).apply {
        closeBehavior = NonClosingInputStream.CloseBehavior.IGNORE
    }
}