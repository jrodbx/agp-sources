/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.ddmlib.internal;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Locale;

class ProcessorStream extends ByteArrayOutputStream {

    void append(ByteBuffer buffer) {
        write(buffer.array(), buffer.position(), buffer.remaining());
    }

    byte[] buf() {
        return buf;
    }

    // Discard [length] bytes at the beginning of the storage array [buf]
    void consume(int length) {
        if (length > count) {
            String msg = String.format(Locale.US, "Cannot consume %d (content=%d)", length, count);
            throw new IllegalStateException(msg);
        }

        System.arraycopy(buf, length, buf, 0, buf.length - length);
        count -= length;
    }
}
