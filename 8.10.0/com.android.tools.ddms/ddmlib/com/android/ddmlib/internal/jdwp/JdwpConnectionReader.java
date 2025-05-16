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

package com.android.ddmlib.internal.jdwp;

import static com.android.ddmlib.internal.jdwp.chunkhandler.ChunkHandler.DDMS_CMD;
import static com.android.ddmlib.internal.jdwp.chunkhandler.ChunkHandler.DDMS_CMD_SET;

import com.android.ddmlib.AdbHelper;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.JdwpHandshake;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleAppName;
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Helper class for reading data both to/from studio, as well as to/from adb. This class maintains
 * an internal buffer that will grow to accommodate the largest JdwpPacket encountered.
 *
 * With the exception of the consumeData method all methods in this class are non-destructive
 * operators. In order for the reader to move on to the next packet it the caller is responsible
 * for calling read to populate the buffer with data from the network, followed by readPacket
 * or parseCommandPacket.
 * Read packet will return a JdwpPacket with payload. consumeData(packetLength) will need to be
 * called to move the buffer onto the next packet received.
 * If a commandpacket is received a helper function in the DdmCommandPacket class is provided
 * to consumeData and shift the buffer onto the next packet.
 */
public class JdwpConnectionReader {

    @VisibleForTesting static final String JDWP_DISCONNECT = "disconnect:";
    private ByteBuffer mReadBuffer;
    private SocketChannel mSocket;

    public JdwpConnectionReader(SocketChannel channelToReadFrom, int initialBufferSize) {
        mReadBuffer = ByteBuffer.allocate(initialBufferSize);
        mSocket = channelToReadFrom;
    }

    /**
     * Reads data from the network stream into an internal buffer.
     * If the type of data is not known, the caller is responsible for testing the data on each
     * of the command packet types before assuming a JdwpPacket.
     */
    public int read() throws IOException {
        return mSocket.read(mReadBuffer);
    }

    /** @return true if the buffer at N+4 matches {@link AdbHelper.HOST_TRANSPORT} */
    public boolean isHostTransport() {
        return bufferOffsetStartsWith(4, AdbHelper.HOST_TRANSPORT);
    }

    /** @return true if the buffer at N+4 matches "jdwp:" */
    public boolean isJdwpPid() {
        return bufferOffsetStartsWith(4, "jdwp:");
    }

    /** @return true if the buffer at N+4 matches {@link JDWP_DISCONNECT} */
    public boolean isDisconnect() {
        return bufferOffsetStartsWith(4, JDWP_DISCONNECT);
    }

    /** @return true if the buffer matches {@JdwpHandshake} */
    public boolean isHandshake() {
        return JdwpHandshake.findHandshake(mReadBuffer) == JdwpHandshake.HANDSHAKE_GOOD;
    }

    /**
     * Checks the top packet in the buffer and returns true if that packet is a DDMS packet with a
     * payload packet type of APNM
     *
     * @return true if the packet payload is of type APNM.
     * @throws IOException
     */
    public boolean isAPNMPacket() throws IOException {
        JdwpPacket packet = readPacketHeader();
        if (packet != null) {
            if (packet.is(DDMS_CMD_SET, DDMS_CMD)) {
                ByteBuffer payload = readPacketPayloadPartial(4);
                return payload.getInt() == HandleAppName.CHUNK_APNM;
            }
        }
        return false;
    }

    /** Creates a command packet with the contents of the current buffer. */
    public DdmCommandPacket parseCommandPacket() {
        return new DdmCommandPacket(mReadBuffer);
    }

    /**
     * Consumes "length" data from the buffer, this is required to read the next packet in the
     * buffer. This operation will throw away "length" amount of data.
     */
    public void consumeData(int length) {
        mReadBuffer.flip();
        mReadBuffer.position(length);
        mReadBuffer.compact();
    }

    /**
     * Reads a full JdwpPacket from the buffer. If the JdwpPacket has a size larger than the current
     * buffer. The buffer will grow to fit the entire packet. The new data will be then read from
     * the socket to load the full packet into memory.
     *
     * @throws IOException
     */
    public JdwpPacket readPacket() throws IOException {
        int packetLength = JdwpPacket.getPacketLength(mReadBuffer);
        if (packetLength <= 0) {
            return null;
        }

        if (packetLength > DdmPreferences.getJdwpMaxPacketSize()) {
            JdwpLoggingUtils.logPacketError("Packet size exceeds expected max", mReadBuffer);
            throw new BufferOverflowException();
        }

        // resize buffer so we can fit whole packet in memory
        if (mReadBuffer.capacity() < packetLength) {
            resizeBuffer(packetLength);
        }
        while (packetLength > mReadBuffer.position()) {
            // read more data from socket until we get full packet
            mSocket.read(mReadBuffer);
        }
        // serialize the packet in memory.
        return JdwpPacket.findPacket(mReadBuffer);
    }

    public JdwpPacket readPacketHeader() throws IOException {
        // Get packet length returns -1 if the packet length read is less than the header size.
        int packetLength = JdwpPacket.getPacketLength(mReadBuffer);
        if (packetLength <= 0) {
            return null;
        }
        while (mReadBuffer.position() < JdwpPacket.JDWP_HEADER_LEN) {
            mSocket.read(mReadBuffer);
        }
        return JdwpPacket.findPacketHeader(mReadBuffer);
    }

    public ByteBuffer readPacketPayloadPartial(int size) throws IOException {
        if (size <= 0) {
            return ByteBuffer.allocate(0);
        }
        while (mReadBuffer.position() < JdwpPacket.JDWP_HEADER_LEN + size) {
            mSocket.read(mReadBuffer);
        }
        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.put(mReadBuffer.array(), JdwpPacket.JDWP_HEADER_LEN, size);
        buffer.flip();
        return buffer;
    }

    private void resizeBuffer(int requestedSize) {
        ByteBuffer newBuffer = ByteBuffer.allocate(requestedSize);
        // copy entire buffer to new buffer
        int currPosition = mReadBuffer.position();
        mReadBuffer.position(0);
        newBuffer.put(mReadBuffer); // leaves "position" at end of copied
        newBuffer.position(currPosition);
        mReadBuffer = newBuffer;
    }

    public void consumePacket() throws IOException {
        JdwpPacket packet = readPacket();
        if (packet != null) {
            packet.consume();
        }
    }

    private boolean bufferOffsetStartsWith(int offset, String match) {
        for (int j = 0, i = offset; j < match.length() && i < mReadBuffer.position(); i++, j++) {
            if (match.charAt(j) != mReadBuffer.get(i)) {
                return false;
            }
        }
        return true;
    }
}
