/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.incfs.install;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Charsets;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

class IncrementalInstallSessionImpl implements AutoCloseable {
    private static final int FULL_REQUEST_SIZE = 12;
    private static final int REQUEST_SIZE = 8;
    private static final byte RESPONSE_CHUNK_HEADER_SIZE = 4;
    private static final int RESPONSE_HEADER_SIZE = 10;
    private static final int DONT_WAIT_TIME_MS = 0;
    private static final int WAIT_TIME_MS = 10;

    @NonNull private final IDeviceConnection mConnection;
    @NonNull private final IBlockTransformer mTransformer;
    @NonNull private final ILogger mLogger;
    @NonNull private final List<StreamingApk> mApks;
    private final long mResponseTimeoutNs;

    // The exception that occurred on the thread responsible for communicating with the device.
    @Nullable private volatile Exception mPendingException;

    // Whether or not the install completed successfully. Package installation can succeed before or
    // after all pages of the APKs being installed have been streamed to the device (before
    // streaming has completed).
    private volatile boolean mInstallSucceeded;

    // Whether or not every block of the APKs being installed have been streamed to the device.
    private volatile boolean mStreamingCompleted;

    // Whether or not the session has been closed using close().
    private volatile boolean mClosed;

    IncrementalInstallSessionImpl(
            @NonNull IDeviceConnection device,
            @NonNull List<StreamingApk> apks,
            long responseTimeout,
            @NonNull IBlockTransformer transformer,
            @NonNull ILogger logger) {
        mConnection = device;
        mApks = apks;
        mResponseTimeoutNs = responseTimeout;
        mTransformer = transformer;
        mLogger = logger;
    }

    void waitForInstallCompleted(long timeout, TimeUnit timeOutUnits)
            throws IOException, InterruptedException {
        waitForCondition(
                timeOutUnits.toNanos(timeout),
                WAIT_TIME_MS,
                () -> {
                    if (mPendingException != null) {
                        throw new RuntimeException(mPendingException);
                    }
                    return mInstallSucceeded || mClosed
                            ? ConditionResult.FULFILLED
                            : ConditionResult.UNFULFILLED;
                });
    }

    void waitForServingCompleted(long timeout, TimeUnit timeOutUnits)
            throws IOException, InterruptedException {
        waitForCondition(
                timeOutUnits.toNanos(timeout),
                WAIT_TIME_MS,
                () -> {
                    if (mPendingException != null) {
                        throw new RuntimeException(mPendingException);
                    }
                    return mStreamingCompleted || mClosed
                            ? ConditionResult.FULFILLED
                            : ConditionResult.UNFULFILLED;
                });
    }

    void waitForAnyCompletion(long timeout, TimeUnit timeOutUnits)
            throws IOException, InterruptedException {
        waitForCondition(
                timeOutUnits.toNanos(timeout),
                WAIT_TIME_MS,
                () -> {
                    if (mPendingException != null) {
                        throw new RuntimeException(mPendingException);
                    }
                    return mInstallSucceeded || mStreamingCompleted || mClosed
                           ? ConditionResult.FULFILLED
                           : ConditionResult.UNFULFILLED;
                });
    }

    private interface IOSupplier<T> {
        T get() throws IOException, InterruptedException;
    }

    private enum ConditionResult {
        FULFILLED,
        UNFULFILLED,
        RESET_TIMEOUT,
    }

    private void waitForCondition(
            long timeoutNs, long waitMs, IOSupplier<ConditionResult> condition)
            throws IOException, InterruptedException {
        long startNs = System.nanoTime();
        while (timeoutNs == 0 || startNs + timeoutNs >= System.nanoTime()) {
            final ConditionResult result = condition.get();
            if (result == ConditionResult.FULFILLED) {
                return;
            }
            if (waitMs > DONT_WAIT_TIME_MS) {
                Thread.sleep(waitMs);
            }
            if (result == ConditionResult.RESET_TIMEOUT) {
                startNs = System.nanoTime();
            }
        }
        throw new IOException("timeout while waiting for condition");
    }

    /** Cancels communication with the device. */
    @Override
    public void close() {
        if (mClosed) {
            return;
        }
        if (!mStreamingCompleted) {
            try {
                writeToDevice(buildCloseResponseChunk());
            } catch (Exception ignore) {
            }
        }
        try {
            mConnection.close();
        } catch (Exception ignore) {
        }
        mApks.forEach(StreamingApk::close);
        mClosed = true;
    }

    void execute(@NonNull Executor executor) {
        executor.execute(
                () -> {
                    try {
                        // Send 'OKAY' to the `install-incremental` command to start receiving block
                        // requests.
                        writeToDevice(ByteBuffer.wrap("OKAY".getBytes(Charsets.UTF_8)));
                        processDeviceData();
                    } catch (Exception e) {
                        mPendingException = e;
                    }
                });
    }

    /**
     * Continuously reads from the device and responds to block read requests until all blocks have
     * been served and install succeeds, until installation fails, or until a request is not issued
     * for {@link #mResponseTimeoutNs} nanoseconds.
     *
     * <p>See {@link MagicMatcher} for the list of magics that proceed read requests, and
     * installation status messages.
     *
     * <p>Installation can succeed before or after all of the pages have been streamed to the
     * device. Similarly, installation can fail before or after all the pages have been streamed to
     * the device. This method only terminates successfully if all of the blocks have been streamed
     * to the device and installation has completed successfully, or if {@link #close()} has been
     * called.
     *
     * @throws IOException when installation failure message has been received or an error occurs
     *     while communicating with the device
     */
    private void processDeviceData() throws IOException, InterruptedException {
        final ByteBuffer buffer = ByteBuffer.allocate(16384);
        // Switch buffer to read mode - this is what underlying code expects the buffer to be in
        // between operations.
        buffer.flip();

        final MagicMatcher magicMatcher = new MagicMatcher();
        final StringBuilder errorBuilder = new StringBuilder();

        // Wait for installation to succeed and streaming to be complete.
        waitForCondition(
                mResponseTimeoutNs,
                DONT_WAIT_TIME_MS,
                () -> {
                    if (mClosed) {
                        return ConditionResult.FULFILLED;
                    }

                    // Read only when there is not enough data for better performance.
                    if (buffer.remaining() < FULL_REQUEST_SIZE) {
                        // This allows the connection to write to the buffer and then flips it back
                        // to the read mode.
                        buffer.compact();
                        final int count = mConnection.read(buffer, WAIT_TIME_MS);
                        buffer.flip();
                        if (count < 0) {
                            throw new EOFException("EOF");
                        }
                    }

                    // Find the next incremental request, install success message, install failure
                    // message magic to know how to interpret the data following the magic.
                    final MagicMatcher.MagicType magic = magicMatcher.findMagic(buffer);
                    if (magic == null) {
                        return ConditionResult.UNFULFILLED;
                    }

                    switch (magic) {
                        case INCREMENTAL:
                            {
                                // Wait until a full request has been received from the device.
                                if (buffer.remaining() >= REQUEST_SIZE) {
                                    if (processReadData(nextRequest(buffer))) {
                                        mStreamingCompleted = true;
                                    }
                                    // The request has been processed, so move to the next magic.
                                    magicMatcher.advance();
                                }
                                break;
                            }
                        case INSTALLATION_FAILURE:
                            {
                                // Build the failure message. The contents of the failure message
                                // start with '[' (parsed in the failure magic) and end with ']'.
                                while (buffer.hasRemaining()) {
                                    final byte c = buffer.get();
                                    if (c == ']') {
                                        throw new IOException(
                                                "Installation failure: " + errorBuilder.toString());
                                    }
                                    errorBuilder.append((char) c);
                                }
                                break;
                            }
                        case INSTALLATION_SUCCESS:
                            {
                                mInstallSucceeded = true;
                                // The success message has been processed, so move to the next
                                // magic.
                                magicMatcher.advance();
                                break;
                            }
                    }
                    return (mStreamingCompleted && mInstallSucceeded)
                            ? ConditionResult.FULFILLED
                            : ConditionResult.RESET_TIMEOUT;
                });
    }

    /**
     * Returns the next {@link ReadRequest} encoded in the buffer if one exists.
     *
     * <p>A block read request has the format: [(int16) requestType][(int16) apkId][(int32)
     * blockIndex] requestType - the type of read request to preform apkId - the index of the APK in
     * the install-incremental arguments that the device is requesting to read (only useful when
     * type is {@link ReadRequest.RequestType#BLOCK_MISSING} or {@link
     * ReadRequest.RequestType#PREFETCH}). blockIndex - the index of the block of data being
     * requested (only useful when type is {@link ReadRequest.RequestType#BLOCK_MISSING} or {@link
     * ReadRequest.RequestType#PREFETCH}).
     */
    private static ReadRequest nextRequest(ByteBuffer data) {
        final short typeData = data.getShort();
        final ReadRequest.RequestType type;
        switch (typeData) {
            case 0:
                type = ReadRequest.RequestType.SERVING_COMPLETE;
                break;
            case 1:
                type = ReadRequest.RequestType.BLOCK_MISSING;
                break;
            case 2:
                type = ReadRequest.RequestType.PREFETCH;
                break;
            case 3:
                type = ReadRequest.RequestType.DESTROY;
                break;
            default:
                throw new IllegalStateException("Unknown request type " + typeData);
        }
        return new ReadRequest(type, data.getShort(), data.getInt());
    }

    private boolean processReadData(ReadRequest request) throws IOException, InterruptedException {
        mLogger.verbose("Received %s", request.toString());
        switch (request.requestType) {
            case SERVING_COMPLETE:
                return true;
            case BLOCK_MISSING:
            case PREFETCH:
                final StreamingApk apk = mApks.get(request.apkId);
                final List<PendingBlock> responses = apk.getBlockResponse(request.blockIndex);
                writeToDevice(buildResponseChunk(request.apkId, responses));
                break;
            case DESTROY:
                throw new IOException("Destroy request received");
        }
        return false;
    }

    /**
     * Builds the response to the device indicating the session is terminating incremental
     * streaming.
     */
    private static ByteBuffer buildCloseResponseChunk() {
        final ByteBuffer buffer =
                ByteBuffer.allocate(RESPONSE_CHUNK_HEADER_SIZE + RESPONSE_HEADER_SIZE);
        buffer.putInt(RESPONSE_HEADER_SIZE);
        buffer.putShort((short) -1);
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        buffer.putInt(0);
        buffer.putShort((short) 0);
        buffer.flip();
        return buffer;
    }

    /**
     * Builds the response to the device containing the page data of all the provided pending
     * blocks.
     */
    private ByteBuffer buildResponseChunk(short apkId, @NonNull List<PendingBlock> blocks)
            throws IOException {
        if (blocks.isEmpty()) {
            return ByteBuffer.allocate(0);
        }
        final byte BLOCK_KIND_DATA = 0;
        final byte BLOCK_KIND_HASH = 1;
        final byte COMPRESSION_KIND_NONE = 0;
        final byte COMPRESSION_KIND_LZ4 = 1;
        final int maxSize =
                RESPONSE_CHUNK_HEADER_SIZE
                        + (StreamingApk.INCFS_BLOCK_SIZE + RESPONSE_HEADER_SIZE) * blocks.size();
        final ByteBuffer buffer = ByteBuffer.allocate(maxSize);
        buffer.position(RESPONSE_CHUNK_HEADER_SIZE);

        int totalSize = 0;
        for (PendingBlock block : blocks) {
            block = mTransformer.transform(block);
            buffer.putShort(apkId);
            buffer.put(
                    block.getType() == PendingBlock.Type.APK_DATA
                            ? BLOCK_KIND_DATA
                            : BLOCK_KIND_HASH);
            buffer.put(
                    block.getCompression() == PendingBlock.Compression.NONE
                            ? COMPRESSION_KIND_NONE
                            : COMPRESSION_KIND_LZ4);
            buffer.putInt(block.getBlockIndex());
            buffer.putShort(block.getBlockSize());
            block.readBlockData(buffer);
            totalSize += RESPONSE_HEADER_SIZE + block.getBlockSize();
        }

        buffer.putInt(0, totalSize);
        buffer.flip();
        return buffer;
    }

    /**
     * Write until all {@code data} is written to the device, the timeout expires, or the connection
     * fails.
     */
    private void writeToDevice(final ByteBuffer data) throws IOException, InterruptedException {
        waitForCondition(
                mResponseTimeoutNs,
                DONT_WAIT_TIME_MS,
                () -> {
                    if (!data.hasRemaining()) {
                        return ConditionResult.FULFILLED;
                    }
                    if (mConnection.write(data, WAIT_TIME_MS) < 0) {
                        throw new IOException("channel EOF");
                    }
                    return ConditionResult.UNFULFILLED;
                });
    }

    private static class MagicMatcher {
        private enum MagicType {
            INCREMENTAL,
            INSTALLATION_FAILURE,
            INSTALLATION_SUCCESS,
        }

        private static class Magic {
            final MagicType type;
            final byte[] value;

            Magic(MagicType type, byte[] value) {
                this.type = type;
                this.value = value;
            }
        }

        private static final ArrayList<Magic> MAGICS = new ArrayList<>();

        static {
            MAGICS.add(new Magic(MagicType.INCREMENTAL, "INCR".getBytes(Charsets.UTF_8)));
            MAGICS.add(
                    new Magic(
                            MagicType.INSTALLATION_FAILURE, "Failure [".getBytes(Charsets.UTF_8)));
            MAGICS.add(
                    new Magic(MagicType.INSTALLATION_SUCCESS, "Success".getBytes(Charsets.UTF_8)));
        }

        private final int[] mPositions = new int[MAGICS.size()];
        private MagicType mFoundMatch = null;

        /**
         * Move to the end of the next magic. This method continues matching the magics using the
         * state of the last invocation of this method. This method continues returning the last
         * magic found until {@link #advance()} is called.
         *
         * @return true if the magic was found; otherwise, false
         */
        MagicType findMagic(ByteBuffer buffer) {
            if (mFoundMatch != null) {
                return mFoundMatch;
            }
            while (buffer.hasRemaining()) {
                final byte nextByte = buffer.get();
                for (int i = 0; i < mPositions.length; i++) {
                    final byte[] magic = MAGICS.get(i).value;
                    if (nextByte == magic[mPositions[i]]) {
                        if (++mPositions[i] == magic.length) {
                            mFoundMatch = MAGICS.get(i).type;
                            mPositions[i] = 0;
                            return mFoundMatch;
                        }
                    } else if (nextByte == magic[0]) {
                        mPositions[i] = 1;
                    } else {
                        mPositions[i] = 0;
                    }
                }
            }
            return null;
        }

        /**
         * Invoke to allow advance the result of {@link #findMagic(ByteBuffer)} to the next magic.
         */
        void advance() {
            mFoundMatch = null;
        }
    }
}
