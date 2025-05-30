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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

/** A block of data belonging to the APK or signature file that is needed by the device. */
public class PendingBlock {
    public enum Type {
        APK_DATA,
        SIGNATURE_TREE,
    }

    public enum Compression {
        NONE,
        LZ4,
    }

    @NonNull private final Path mFilePath;
    @NonNull private final Type mType;
    private final int mBlockIndex;
    private final int mBlockCount;

    @NonNull private final StreamingApk mApk;
    private final int mBlockOffset;
    private final short mBlockSize;

    PendingBlock(
            @NonNull Path filePath,
            @NonNull Type type,
            int blockIndex,
            int blockCount,
            @NonNull StreamingApk apk,
            int blockOffset,
            short blockSize) {
        this.mFilePath = filePath;
        this.mType = type;
        this.mBlockIndex = blockIndex;
        this.mBlockCount = blockCount;
        this.mApk = apk;
        this.mBlockOffset = blockOffset;
        this.mBlockSize = blockSize;
    }

    public PendingBlock(@NonNull PendingBlock block) {
        this.mFilePath = block.mFilePath;
        this.mType = block.mType;
        this.mBlockIndex = block.mBlockIndex;
        this.mBlockCount = block.mBlockCount;
        this.mApk = block.mApk;
        this.mBlockOffset = block.mBlockOffset;
        this.mBlockSize = block.mBlockSize;
    }

    /** The path to the file in which the block resides. */
    @NonNull
    public Path getPath() {
        return mFilePath;
    }

    /** @see PendingBlock.Type */
    @NonNull
    public Type getType() {
        return mType;
    }

    /** @see PendingBlock.Compression */
    @NonNull
    public Compression getCompression() {
        return Compression.NONE;
    }

    /** The index of the data block in the file. */
    public int getBlockIndex() {
        return mBlockIndex;
    }

    /** The number of blocks in the file in which the block resides. */
    public int getFileBlockCount() {
        return mBlockCount;
    }

    /** The size in bytes of the block data. */
    public short getBlockSize() {
        return mBlockSize;
    }

    /** Reads the block data into the buffer at the current position. */
    public void readBlockData(@NonNull ByteBuffer buffer) throws IOException {
        mApk.readBlockData(buffer, mType, mBlockOffset, mBlockSize);
    }

    @Override
    public String toString() {
        return "PendingBlock{"
                + "mFilePath=" + mFilePath
                + ", mType=" + mType
                + ", mBlockIndex=" + mBlockIndex
                + ", mBlockCount=" + mBlockCount
                + ", mBlockOffset=" + mBlockOffset
                + ", mBlockSize=" + mBlockSize
                + '}';
    }
}
