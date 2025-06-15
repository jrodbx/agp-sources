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
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;

/** Represents an APK being incrementally streamed. */
class StreamingApk implements AutoCloseable {
    static final short INCFS_BLOCK_SIZE = 4096;
    private static final int INCFS_DIGEST_SIZE = 32;
    private static final int INCFS_MAX_SIGNATURE_SIZE = 8096;
    private static final int INCFS_HASHES_PER_BLOCK = INCFS_BLOCK_SIZE / INCFS_DIGEST_SIZE;

    @NonNull private final Path mApk;
    @NonNull private final Path mSignature;
    @NonNull private final FileChannel mApkChannel;
    @NonNull private final FileChannel mSignatureChannel;
    private final long mApkSize;
    private final long mSignatureSize;

    // The offset of the Merkle tree in the signature file.
    private final int mTreeOffset;

    // Base64 representation of the signing info.
    @NonNull private final String mSignatureBase64;

    // Maps block index to whether or not the block has already been sent.
    private final int mDataBlockCount;
    private final int mTreeBlockCount;
    private final BitSet mSentDataBlocks;
    private final BitSet mSentTreeBlocks;

    @NonNull private final IBlockFilter mBlockFilter;
    @NonNull private final ILogger mLogger;

    private StreamingApk(
            @NonNull Path apk,
            @NonNull Path signature,
            @NonNull FileChannel apkChannel,
            @NonNull FileChannel signatureChannel,
            long apkSize,
            long sigSize,
            int treeOffset,
            @NonNull String signatureBase64,
            @NonNull IBlockFilter server,
            @NonNull ILogger logger) {
        mApk = apk;
        mSignature = signature;
        mApkChannel = apkChannel;
        mSignatureChannel = signatureChannel;
        mApkSize = apkSize;
        mSignatureSize = sigSize;
        mDataBlockCount = numBytesToNumBlocks(apkSize);
        mTreeBlockCount = verityTreeBlocksForFile(apkSize);
        mSentDataBlocks = new BitSet(mDataBlockCount);
        mSentTreeBlocks = new BitSet(mTreeBlockCount);
        mTreeOffset = treeOffset;
        mSignatureBase64 = signatureBase64;
        mBlockFilter = server;
        mLogger = logger;
    }

    static StreamingApk generate(
            @NonNull Path apk,
            @NonNull Path sig,
            @NonNull IBlockFilter server,
            @NonNull ILogger logger)
            throws IOException {
        final String base64Sig;
        final int treeOffset;
        try (final BufferedInputStream sigIs = new BufferedInputStream(Files.newInputStream(sig))) {
            final ByteArrayOutputStream base64SigOS = new ByteArrayOutputStream();
            final OutputStream sigBOS = Base64.getEncoder().wrap(base64SigOS);
            final int version = readInt32(sigIs, sigBOS, "Failed to read version from " + sig);

            final int hashingInfoSize =
                    readBytesWithSize(sigIs, sigBOS, "Failed to read hashing info from " + sig);

            final int signingInfoSize =
                    readBytesWithSize(sigIs, sigBOS, "Failed to read signing info from " + sig);

            final int signatureSize = 12 + hashingInfoSize + signingInfoSize;
            if (signatureSize > INCFS_MAX_SIGNATURE_SIZE) {
                throw new IllegalArgumentException(
                        "Signature is too long. Max allowed is " + INCFS_MAX_SIGNATURE_SIZE);
            }

            final int treeSize = readInt32(sigIs, null, "Failed to read tree size from " + sig);

            final int expectedTreeSize = verityTreeSizeForFile(Files.size(apk));
            if (treeSize != expectedTreeSize) {
                throw new IllegalArgumentException(
                        "Verity tree size mismatch in signature file: [was "
                                + treeSize
                                + ", expected "
                                + expectedTreeSize
                                + "]");
            }

            treeOffset = 4 + signatureSize;
            base64Sig = new String(base64SigOS.toByteArray());
        }

        FileChannel apkChannel = null;
        FileChannel signatureChannel = null;
        final long apkSize;
        final long sigSize;
        try {
            apkChannel = FileChannel.open(apk);
            signatureChannel = FileChannel.open(sig);
            apkSize = apkChannel.size();
            sigSize = signatureChannel.size();
        } catch (IOException e) {
            if (apkChannel != null) {
                apkChannel.close();
            }
            if (signatureChannel != null) {
                signatureChannel.close();
            }
            throw e;
        }
        return new StreamingApk(apk, sig, apkChannel, signatureChannel, apkSize, sigSize,
                                treeOffset, base64Sig, server, logger);
    }

    String getSignatureBase64() {
        return mSignatureBase64;
    }

    /**
     * Retrieves the list of blocks allowed by the {@link IBlockFilter} to be streamed to the device
     * in order to read the data block at {@code blockIndex}.
     */
    List<PendingBlock> getBlockResponse(int blockIndex) {
        if (blockIndex < 0 || blockIndex >= mDataBlockCount) {
            throw new IllegalArgumentException("Requested block index is outside range");
        }

        List<PendingBlock> responses = getTreeBlocksResponsesForDataBlock(blockIndex);
        if (!mSentDataBlocks.get(blockIndex)) {
            getDataPendingBlock(blockIndex).ifPresent(responses::add);
        }
        return responses;
    }

    private List<PendingBlock> getTreeBlocksResponsesForDataBlock(int blockIndex) {
        final int dataBlockCount = mDataBlockCount;
        final int totalNodeCount = mTreeBlockCount;
        final int leafNodesCount =
                (dataBlockCount + INCFS_HASHES_PER_BLOCK - 1) / INCFS_HASHES_PER_BLOCK;
        final int leafNodesOffset = totalNodeCount - leafNodesCount;

        // Leaf level, sending only 1 block.
        final int leafIndex = leafNodesOffset + blockIndex / INCFS_HASHES_PER_BLOCK;

        final ArrayList<PendingBlock> responses = new ArrayList<>();
        if (!mSentTreeBlocks.get(leafIndex)) {
            getTreePendingBlock(leafIndex).ifPresent(responses::add);
        }

        // Non-leaf, sending EVERYTHING. This should be done only once.
        if (leafNodesOffset != 0) {
            for (int i = 0; i < leafNodesOffset; ++i) {
                if (!mSentTreeBlocks.get(i)) {
                    getTreePendingBlock(i).ifPresent(responses::add);
                }
            }
        }
        return responses;
    }

    private Optional<PendingBlock> getTreePendingBlock(int treeBlockIndex) {
        final int blockOffset = mTreeOffset + treeBlockIndex * INCFS_BLOCK_SIZE;
        final short blockSize = (short) Math.min(mSignatureSize - blockOffset, INCFS_BLOCK_SIZE);
        final PendingBlock response =
                new PendingBlock(
                        mSignature,
                        PendingBlock.Type.SIGNATURE_TREE,
                        treeBlockIndex,
                        mTreeBlockCount,
                        this,
                        blockOffset,
                        blockSize);
        if (mBlockFilter.shouldServeBlock(response)) {
            mLogger.verbose("Sending %s", response);
            mSentTreeBlocks.set(treeBlockIndex, true);
            return Optional.of(response);
        }
        mLogger.verbose("Denied sending %s", response);
        return Optional.empty();
    }

    private Optional<PendingBlock> getDataPendingBlock(int index) {
        final int blockOffset = index * INCFS_BLOCK_SIZE;
        final short blockSize = (short) Math.min(mApkSize - blockOffset, INCFS_BLOCK_SIZE);
        final PendingBlock response =
                new PendingBlock(
                        mApk,
                        PendingBlock.Type.APK_DATA,
                        index,
                        mDataBlockCount,
                        this,
                        blockOffset,
                        blockSize);
        if (mBlockFilter.shouldServeBlock(response)) {
            mLogger.verbose("Sending %s", response);
            mSentDataBlocks.set(index, true);
            return Optional.of(response);
        }
        mLogger.verbose("Denied sending %s", response);
        return Optional.empty();
    }

    /**
     * Reads the block data from the data or signature file into the buffer. This method only
     * updates buffer position, not limit.
     */
    void readBlockData(
            @NonNull ByteBuffer buffer,
            @NonNull PendingBlock.Type type,
            int blockOffset,
            short blockSize)
            throws IOException {
        final FileChannel channel =
                (type == PendingBlock.Type.APK_DATA) ? mApkChannel : mSignatureChannel;

        // Set the limit to only read blockSize number of bytes.
        final int previousLimit = buffer.limit();
        buffer.limit(buffer.position() + blockSize);
        try {
            if ((short) channel.read(buffer, blockOffset) != blockSize) {
                throw new IOException(
                        "Failed to read "
                                + blockSize
                                + " bytes from "
                                + ((type == PendingBlock.Type.APK_DATA) ? mApk : mSignature));
            }
        } finally {
            buffer.limit(previousLimit);
        }
    }

    /** Returns the number of blocks for a file of the specified size in bytes. */
    private static int numBytesToNumBlocks(long fileSize) {
        return (int) (fileSize / INCFS_BLOCK_SIZE) + ((fileSize % INCFS_BLOCK_SIZE == 0) ? 0 : 1);
    }

    /**
     * Returns the expected file size of the signature file for an APK file of the given size.
     *
     * @param fileSize the size of the APK in bytes
     */
    private static int verityTreeSizeForFile(long fileSize) {
        return verityTreeBlocksForFile(fileSize) * INCFS_BLOCK_SIZE;
    }

    /**
     * Returns the number of blocks of the signature file for an APK file of the given size.
     *
     * @param fileSize the size of the APK in bytes
     */
    private static int verityTreeBlocksForFile(long fileSize) {
        if (fileSize == 0) {
            return 0;
        }

        final int hashPerBlock = INCFS_BLOCK_SIZE / INCFS_DIGEST_SIZE;
        int totalTreeBlockCount = 0;

        long hashBlockCount = 1 + (fileSize - 1) / INCFS_BLOCK_SIZE;
        while (hashBlockCount > 1) {
            hashBlockCount = (hashBlockCount + hashPerBlock - 1) / hashPerBlock;
            totalTreeBlockCount += hashBlockCount;
        }

        return totalTreeBlockCount;
    }

    private static int readInt32(InputStream is, OutputStream accumulator, String errorMessage)
            throws IOException {
        final byte[] data = new byte[4];
        if (is.read(data) != 4) {
            throw new IOException(errorMessage);
        }
        if (accumulator != null) {
            accumulator.write(data);
        }
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static int readBytesWithSize(
            InputStream is, OutputStream accumulator, String errorMessage) throws IOException {
        final int size = readInt32(is, accumulator, errorMessage);
        int length;
        int totalRead = 0;
        byte[] buffer = new byte[4096];
        while (totalRead < size && (length = is.read(buffer, 0, size - totalRead)) > 0) {
            accumulator.write(buffer, 0, length);
            totalRead += length;
        }
        if (totalRead != size) {
            throw new IOException(errorMessage);
        }
        return size;
    }

    @Override
    public void close() {
        try {
            mApkChannel.close();
        } catch (IOException ignore) {
        }
        try {
            mSignatureChannel.close();
        } catch (IOException ignore) {
        }
    }
}
