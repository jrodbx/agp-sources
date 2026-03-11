/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.utils;

import com.android.annotations.NonNull;
import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;

/**
 * A {@link Reader} getting its data from a {@link CharSequence}. This light-weight implementation
 * is intended for single-thread use, doesn't have any synchronization, and does not throw {@link
 * IOException}.
 */
// This implementation is based on a package private Guava implementation (com.google.common.io),
// minus precondition checks, synchronization, IOException, plus annotations.
public final class CharSequenceReader extends Reader {
    private CharSequence seq;
    private int pos;
    private int mark;

    public CharSequenceReader(@NonNull CharSequence seq) {
        this.seq = seq;
    }

    private boolean hasRemaining() {
        return remaining() > 0;
    }

    private int remaining() {
        return seq.length() - pos;
    }

    @Override
    public int read(@NonNull CharBuffer target) {
        if (!hasRemaining()) {
            return -1;
        }
        int charsToRead = Math.min(target.remaining(), remaining());
        for (int i = 0; i < charsToRead; i++) {
            target.put(seq.charAt(pos++));
        }
        return charsToRead;
    }

    @Override
    public int read() {
        return hasRemaining() ? seq.charAt(pos++) : -1;
    }

    @Override
    public int read(@NonNull char[] cbuf, int off, int len) {
        if (!hasRemaining()) {
            return -1;
        }
        int charsToRead = Math.min(len, remaining());
        for (int i = 0; i < charsToRead; i++) {
            cbuf[off + i] = seq.charAt(pos++);
        }
        return charsToRead;
    }

    @Override
    public long skip(long n) {
        int charsToSkip = (int) Math.min(remaining(), n);
        pos += charsToSkip;
        return charsToSkip;
    }

    @Override
    public boolean ready() {
        return true;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public void mark(int readAheadLimit) {
        mark = pos;
    }

    @Override
    public void reset() {
        pos = mark;
    }

    @Override
    public void close() {
        seq = null;
    }
}
