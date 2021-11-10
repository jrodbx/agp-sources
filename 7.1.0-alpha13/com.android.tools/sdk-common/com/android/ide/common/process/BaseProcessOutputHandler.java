/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.ide.common.process;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Charsets;
import com.google.common.io.FileBackedOutputStream;
import com.google.common.io.LineProcessor;

import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Partial implementation of ProcessOutputHandler that creates a ProcessOutput that caches the
 * output in a ByteArrayOutputStream.
 *
 * This does not do anything with it, since it does not implement
 * {@link ProcessOutputHandler#handleOutput(ProcessOutput)}
 */
public abstract class BaseProcessOutputHandler implements ProcessOutputHandler {

    public BaseProcessOutputHandler() {
    }

    @NonNull
    @Override
    public ProcessOutput createOutput() {
        return new BaseProcessOutput();
    }

    public static final class BaseProcessOutput implements ProcessOutput {
        private final FileBackedOutputStream mStandardOutput =
                new FileBackedOutputStream(1024*10, true);
        private final FileBackedOutputStream mErrorOutput =
                new FileBackedOutputStream(1024*10, true);
        private final AtomicBoolean mClosed = new AtomicBoolean(false);

        @NonNull
        @Override
        public FileBackedOutputStream getStandardOutput() {
            return mStandardOutput;
        }

        @NonNull
        @Override
        public FileBackedOutputStream getErrorOutput() {
            return mErrorOutput;
        }

        @Override
        public synchronized void close() throws IOException {
            if (!mClosed.get()) {
                mStandardOutput.close();
                mErrorOutput.close();
                mClosed.set(true);
            }
        }

        @Nullable
        public <T> T processErrorOutputLines(@NonNull LineProcessor<T> lineProcessor)
                throws ProcessException {
            return processOutputStreamLines(mErrorOutput, lineProcessor);
        }


        /**
         * Process each output line using an implementation of {@link LineProcessor}.
         * @param lineProcessor a processor for a line of output.
         * @param <T> the expected result from the line processor (once it has processed part of or
         *           the entire output of the process)
         * @return the result if any.
         * @throws ProcessException
         */
        @Nullable
        public <T> T processStandardOutputLines(@NonNull LineProcessor<T> lineProcessor)
                throws ProcessException {
            return processOutputStreamLines(mStandardOutput, lineProcessor);
        }

        @Nullable
        private <T> T processOutputStreamLines(@NonNull FileBackedOutputStream outputStream,
                LineProcessor<T> lineProcessor) throws ProcessException {
            if (!mClosed.get()) {
                throw new ProcessException("Output and Error streams not closed");
            }
            try {
                return outputStream.asByteSource()
                        .asCharSource(Charsets.UTF_8).readLines(lineProcessor);
            } catch (IOException e) {
                throw new ProcessException(e);
            }
        }

        public Reader getStandardOutputAsReader() throws IOException {
            return mStandardOutput.asByteSource().asCharSource(Charsets.UTF_8).openBufferedStream();
        }

        /**
         * Return the process output as a String. This should be used with caution as depending on
         * the process output, the resulting String can be of significant size.
         * @return the process output.
         * @throws ProcessException
         */
        @NonNull
        public String getStandardOutputAsString() throws ProcessException {
            return getString(mStandardOutput);
        }

        /**
         * Return the process error output as a String. This should be used with caution as
         * depending on the process output, the resulting String can be of significant size.
         * @return the process error output.
         * @throws ProcessException
         */
        @NonNull
        public String getErrorOutputAsString() throws ProcessException {
            return getString(mErrorOutput);
        }

        private String getString(@NonNull FileBackedOutputStream stream) throws ProcessException {
            if (!mClosed.get()) {
                throw new ProcessException("Output and Error streams not closed");
            }
            try {
                return stream.asByteSource().asCharSource(Charsets.UTF_8).read();
            } catch (IOException e) {
                throw new ProcessException(e);
            }
        }
    }
}
