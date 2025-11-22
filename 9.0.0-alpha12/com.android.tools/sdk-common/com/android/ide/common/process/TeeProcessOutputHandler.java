/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.google.common.collect.ImmutableList;
import com.google.common.io.Closer;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Output handler that will forward output to multiple handlers.
 */
public class TeeProcessOutputHandler implements ProcessOutputHandler {

    /**
     * All output handlers.
     */
    @NonNull
    private final ImmutableList<ProcessOutputHandler> mHandlers;

    /**
     * Creates a new output handler.
     *
     * @param handlers the handlers; individual handlers may be {@code null} and will be
     * ignored
     */
    public TeeProcessOutputHandler(@NonNull ProcessOutputHandler... handlers) {
        ImmutableList.Builder<ProcessOutputHandler> builder = new ImmutableList.Builder<>();
        for (ProcessOutputHandler h : handlers) {
            if (h != null) {
                builder.add(h);
            }
        }

        mHandlers = builder.build();
    }

    @Override
    public void handleOutput(@NonNull ProcessOutput processOutput) throws ProcessException {
        for (int i = 0; i < mHandlers.size(); i++) {
            mHandlers.get(i).handleOutput(((TeeProcessOutput) processOutput).mOutputs.get(i));
        }
    }

    @NonNull
    @Override
    public ProcessOutput createOutput() {
        List<ProcessOutput> outputs =
                mHandlers.stream()
                        .map(ProcessOutputHandler::createOutput)
                        .collect(Collectors.toList());

        return new TeeProcessOutput(ImmutableList.copyOf(outputs));
    }

    /**
     * Process output that directs output to multiple process outputs.
     */
    private static class TeeProcessOutput implements ProcessOutput {

        /**
         * All process outputs.
         */
        @NonNull
        private final ImmutableList<ProcessOutput> mOutputs;

        /**
         * Creates a new process output.
         *
         * @param outputs output to delegate to
         */
        private TeeProcessOutput(@NonNull ImmutableList<ProcessOutput> outputs) {
            mOutputs = outputs;
        }

        /**
         * Obtains an {@code OutputStream} that writes to all output streams obtained from
         * {@code processOutputStreamExtractor}.
         *
         * @param processOutputStreamExtractor an extractor that obtains an output stream from
         * a {@code ProcessOutput}
         * @return the composite output stream
         */
        @NonNull
        private OutputStream getCompositeStream(
                @NonNull Function<ProcessOutput, OutputStream> processOutputStreamExtractor) {
            List<OutputStream> streams =
                    mOutputs.stream()
                            .map(processOutputStreamExtractor)
                            .collect(Collectors.toList());

            return new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    for (OutputStream os : streams) {
                        os.write(b);
                    }
                }

                @Override
                public void write(@NonNull byte[] b, int off, int len) throws IOException {
                    for (OutputStream os : streams) {
                        os.write(b, off, len);
                    }
                }

                @Override
                public void flush() throws IOException {
                    for (OutputStream os : streams) {
                        os.flush();
                    }
                }

                @Override
                public void close() throws IOException {
                    try (Closer c = Closer.create()) {
                        streams.forEach(c::register);
                    }
                }
            };
        }

        @NonNull
        @Override
        public OutputStream getStandardOutput() {
            return getCompositeStream(ProcessOutput::getStandardOutput);
        }

        @NonNull
        @Override
        public OutputStream getErrorOutput() {
            return getCompositeStream(ProcessOutput::getErrorOutput);
        }

        @Override
        public void close() throws IOException {
            try (Closer c = Closer.create()) {
                mOutputs.forEach(c::register);
            }
        }
    }
}
