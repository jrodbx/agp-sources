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
package com.android.ddmlib.jdwp;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket;
import com.android.ddmlib.jdwp.packets.CapabilitiesNewReply;
import com.android.ddmlib.jdwp.packets.IdSizesReply;
import com.google.common.base.Charsets;
import java.nio.ByteBuffer;

public class JdwpProtocol {

    @Nullable
    private IdSizesReply mIdSizes;

    public long readObjectId(@NonNull ByteBuffer buffer) {
        assert mIdSizes != null;
        return readId(buffer, mIdSizes.objectIDSize);
    }

    public long readRefTypeId(@NonNull ByteBuffer buffer) {
        assert mIdSizes != null;
        return readId(buffer, mIdSizes.refTypeIDSize);
    }

    public long readMethodId(@NonNull ByteBuffer buffer) {
        assert mIdSizes != null;
        return readId(buffer, mIdSizes.methodIDSize);
    }

    public long readFieldId(@NonNull ByteBuffer buffer) {
        assert mIdSizes != null;
        return readId(buffer, mIdSizes.fieldIDSize);
    }

    private long readId(@NonNull ByteBuffer buffer, int size) {
        switch (size) {
            case 1: return buffer.get();
            case 2: return buffer.getShort();
            case 4: return buffer.getInt();
            case 8: return buffer.getLong();
            default: throw new IllegalArgumentException("Unsupported Id size: " + size);
        }
    }

    public String readString(@NonNull ByteBuffer buffer) {
        int len = buffer.getInt();
        byte[] utf8 = new byte[len];
        buffer.get(utf8);
        return new String(utf8, Charsets.UTF_8);
    }

    public void incoming(@NonNull JdwpPacket packet, @NonNull JdwpAgent target) {
        if (packet.is(JdwpCommands.SET_VM, JdwpCommands.CMD_VM_IDSIZES)) {
            target.addReplyInterceptor(packet.getId(), new JdwpInterceptor() {
                @Override
                public JdwpPacket intercept(@NonNull JdwpAgent agent, @NonNull JdwpPacket packet) {
                    mIdSizes = new IdSizesReply();
                    mIdSizes.parse(packet.getPayload(), JdwpProtocol.this);
                    return packet;
                }
            });
        } else if (packet.is(JdwpCommands.SET_VM, JdwpCommands.CMD_VM_CAPABILITIESNEW)) {
            target.addReplyInterceptor(
                    packet.getId(),
                    new JdwpInterceptor() {
                        @Override
                        public JdwpPacket intercept(
                                @NonNull JdwpAgent agent, @NonNull JdwpPacket packet) {
                            CapabilitiesNewReply reply = new CapabilitiesNewReply();
                            reply.parse(packet.getPayload(), JdwpProtocol.this);
                            packet.setPayload(reply.getConverted());
                            return packet;
                        }
                    });
        }
    }
}
