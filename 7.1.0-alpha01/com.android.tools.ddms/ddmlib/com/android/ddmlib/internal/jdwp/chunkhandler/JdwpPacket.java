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

package com.android.ddmlib.internal.jdwp.chunkhandler;

import com.android.annotations.NonNull;
import com.android.ddmlib.Log;
import com.android.ddmlib.jdwp.JdwpCommands;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;

/**
 * A JDWP packet, sitting at the start of a ByteBuffer somewhere.
 *
 * This allows us to wrap a "pointer" to the data with the results of
 * decoding the packet.
 *
 * None of the operations here are synchronized.  If multiple threads will
 * be accessing the same ByteBuffers, external sync will be required.
 *
 * Use the constructor to create an empty packet, or "findPacket()" to
 * wrap a JdwpPacket around existing data.
 */
public final class JdwpPacket {
    public static final int JDWP_HEADER_LEN = 11;

    private static final int REPLY_PACKET = 0x80;

    @NonNull private final ByteBuffer mBuffer;
    private int mLength;
    private int mId;
    private int mFlags;
    private int mCmdSet;
    private int mCmd;
    private int mErrCode;

    private static int sSerialId = 0x40000000;

    /** Create a new, empty packet, in "buf". */
    @VisibleForTesting
    public JdwpPacket(@NonNull ByteBuffer buf) {
        mBuffer = buf;
    }

    /**
     * Finish a packet created with newPacket().
     *
     * <p>This always creates a command packet, with the next serial number in sequence.
     *
     * <p>We have to take "payloadLength" as an argument because we can't see the position in the
     * "slice" returned by getPayload(). We could fish it out of the chunk header, but it's legal
     * for there to be more than one chunk in a JDWP packet.
     *
     * <p>On exit, "position" points to the end of the data.
     */
    @VisibleForTesting
    public void finishPacket(int cmdSet, int cmd, int payloadLength) {

        ByteOrder oldOrder = mBuffer.order();
        mBuffer.order(ChunkHandler.CHUNK_ORDER);

        mLength = JDWP_HEADER_LEN + payloadLength;
        mId = getNextSerial();
        mFlags = 0;
        mCmdSet = cmdSet;
        mCmd = cmd;

        mBuffer.putInt(0x00, mLength);
        mBuffer.putInt(0x04, mId);
        mBuffer.put(0x08, (byte) mFlags);
        mBuffer.put(0x09, (byte) mCmdSet);
        mBuffer.put(0x0a, (byte) mCmd);

        mBuffer.order(oldOrder);
        mBuffer.position(mLength);
    }

    /**
     * Get the next serial number.  This creates a unique serial number
     * across all connections, not just for the current connection.  This
     * is a useful property when debugging, but isn't necessary.
     *
     * We can't synchronize on an int, so we use a sync method.
     */
    private static synchronized int getNextSerial() {
        return sSerialId++;
    }

    /**
     * Return a slice of the byte buffer, positioned past the JDWP header
     * to the start of the chunk header.  The buffer's limit will be set
     * to the size of the payload if the size is known; if this is a
     * packet under construction the limit will be set to the end of the
     * buffer.
     *
     * Doesn't examine the packet at all -- works on empty buffers.
     */
    public ByteBuffer getPayload() {
        ByteBuffer buf;
        int oldPosn = mBuffer.position();

        mBuffer.position(JDWP_HEADER_LEN);
        buf = mBuffer.slice();     // goes from position to limit
        mBuffer.position(oldPosn);

        if (mLength > 0)
            buf.limit(mLength - JDWP_HEADER_LEN);
        buf.order(ChunkHandler.CHUNK_ORDER);
        return buf;
    }

    /**
     * Returns "true" if this JDWP packet is tagged as a reply.
     */
    public boolean isReply() {
        return (mFlags & REPLY_PACKET) != 0;
    }

    /** Returns "true" if this JDWP packet is a reply with a nonzero error code. */
    public boolean isError() {
        return isReply() && mErrCode != 0;
    }

    /** Returns "true" if this JDWP packet has no data. */
    public boolean isEmpty() {
        return (mLength == JDWP_HEADER_LEN);
    }

    /**
     * Return the packet's ID.  For a reply packet, this allows us to
     * match the reply with the original request.
     */
    public int getId() {
        return mId;
    }

    /**
     * Return the length of a packet.  This includes the header, so an
     * empty packet is 11 bytes long.
     */
    public int getLength() {
        return mLength;
    }

    /**
     * Write our packet to "chan".
     *
     * <p>The JDWP packet starts at offset 0 and ends at mBuffer.position().
     */
    public void write(SocketChannel chan) throws IOException {
        assert mLength > 0;

        int oldPosn = mBuffer.position();
        mBuffer.position(0);
        mBuffer.limit(mLength);

        while (mBuffer.position() != mBuffer.limit()) {
            chan.write(mBuffer);
        }
        // position should now be at end of packet
        assert mBuffer.position() == mLength;

        mBuffer.limit(mBuffer.capacity());
        mBuffer.position(oldPosn);
    }

    /**
     * "Move" the packet data out of the buffer we're sitting on and into buf at the current
     * position.
     */
    public void move(ByteBuffer buf) {
        int oldPosn = mBuffer.position();

        mBuffer.position(0);
        mBuffer.limit(mLength);
        buf.put(mBuffer);

        mBuffer.limit(mBuffer.capacity());
        mBuffer.position(oldPosn);
    }

    /** Helper function to copy the packet into a new buffer. */
    public void copy(ByteBuffer into) {
        into.put(mBuffer.array(), 0, mLength);
    }

    /** Replace the payload of the package with a buffer. The current position is unchanged. */
    public void setPayload(ByteBuffer buf) {
        if (mLength - JDWP_HEADER_LEN != buf.remaining()) {
            throw new UnsupportedOperationException("Changing payload size not supported");
        }

        int oldPosn = mBuffer.position();

        mBuffer.position(JDWP_HEADER_LEN);
        mBuffer.put(buf);
        mBuffer.position(oldPosn);
    }

    /**
     * Consume the JDWP packet.
     *
     * <p>On entry and exit, "position" is at the end of data in buffer.
     */
    public void consume() {
        /*
         * The "flip" call sets "limit" equal to the position (usually the
         * end of data) and "position" equal to zero.
         *
         * compact() copies everything from "position" and "limit" to the
         * start of the buffer, sets "position" to the end of data, and
         * sets "limit" to the capacity.
         *
         * On entry, "position" is set to the amount of data in the buffer
         * and "limit" is set to the capacity.  We want to call flip()
         * so that position..limit spans our data, advance "position" past
         * the current packet, then compact.
         */
        mBuffer.flip();
        mBuffer.position(mLength);
        mBuffer.compact();
        mLength = 0;
    }

    /**
     * When the "buf" contains JdwpPackets the first 4 bytes are the length of the packet. This
     * helper function reads the first 4 bytes and validates that the length is at least the size of
     * the JDWP header.
     *
     * @param buf a buffer assumed to contain a jdwp packet.
     * @return -1 if the length is invalid, otherwise the length of the packet.
     */
    public static int getPacketLength(ByteBuffer buf) {
        int count = buf.position();
        if (count < JDWP_HEADER_LEN) {
            return -1;
        }
        int length = buf.getInt(0x00);
        if (length < JDWP_HEADER_LEN) {
            return -1;
        }
        return length;
    }

    /**
     * Find the JDWP packet at the start of "buf". The start is known, but the length has to be
     * parsed out.
     *
     * <p>On entry, the packet data in "buf" must start at offset 0 and end at "position". "limit"
     * should be set to the buffer capacity. This method does not alter "buf"s attributes.
     *
     * <p>Returns a new JdwpPacket if a full one is found in the buffer. If not, returns null.
     * Throws an exception if the data doesn't look like a valid JDWP packet.
     */
    private static JdwpPacket findPacket(ByteBuffer buf, boolean setPayload) {
        int count = buf.position();
        int length, id, flags, cmdSet, cmd;

        if (count < JDWP_HEADER_LEN) {
            return null;
        }

        ByteOrder oldOrder = buf.order();
        buf.order(ChunkHandler.CHUNK_ORDER);

        length = buf.getInt(0x00);
        id = buf.getInt(0x04);
        flags = buf.get(0x08) & 0xff;
        cmdSet = buf.get(0x09) & 0xff;
        cmd = buf.get(0x0a) & 0xff;

        buf.order(oldOrder);

        JdwpPacket pkt;

        if (setPayload) {
            if (length < JDWP_HEADER_LEN) throw new BadPacketException();
            if (count < length) return null;

            pkt = new JdwpPacket(buf);
        } else {
            pkt = new JdwpPacket(ByteBuffer.allocate(0));
        }
        //pkt.mBuffer = buf;
        pkt.mLength = length;
        pkt.mId = id;
        pkt.mFlags = flags;

        if ((flags & REPLY_PACKET) == 0) {
            pkt.mCmdSet = cmdSet;
            pkt.mCmd = cmd;
            pkt.mErrCode = -1;
        } else {
            pkt.mCmdSet = -1;
            pkt.mCmd = -1;
            pkt.mErrCode = cmdSet | (cmd << 8);
        }

        return pkt;
    }

    public static JdwpPacket findPacket(ByteBuffer buf) {
        return findPacket(buf, true);
    }

    public static JdwpPacket findPacketHeader(ByteBuffer buf) {
        return findPacket(buf, false);
    }

    @Override
    public String toString() {
        return isReply() ? " < # " + mId : " > " + mCmdSet + "." + mCmd + " # " + mId;
    }

    public boolean is(int cmdSet, int cmd) {
        return cmdSet == mCmdSet && cmd == mCmd;
    }

    public void log(@NonNull String action) {
        if (Log.isAtLeast(Log.LogLevel.DEBUG)) {
            if (isReply()) {
                Log.d(
                        "jdwp",
                        String.format(
                                "%s: jdwp reply: id=%d, length=%d, flags=%d, error=%d",
                                action, mId, mLength, mFlags, mErrCode));
            } else {
                Log.d(
                        "jdwp",
                        String.format(
                                "%s: jdwp request: id=%d, length=%d, flags=%d, cmdSet=%s, cmd=%s",
                                action,
                                mId,
                                mLength,
                                mFlags,
                                JdwpCommands.commandSetToString(mCmdSet),
                                JdwpCommands.commandToString(mCmdSet, mCmd)));
            }
        }
    }
}

