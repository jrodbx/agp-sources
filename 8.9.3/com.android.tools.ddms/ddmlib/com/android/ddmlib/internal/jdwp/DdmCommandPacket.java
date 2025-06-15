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

import com.android.ddmlib.AdbHelper;
import java.nio.ByteBuffer;

/**
 * Helper class to parse a buffer with a command packet. Command packets are special non JDWP
 * packets that are used to set state and pass metadata. {@link AdbHelper.HOST_TRANSPORT} is one
 * example of a command packet used to set the device id.
 */
public class DdmCommandPacket {
    private int mLength;
    private String mCommand;

    public DdmCommandPacket(ByteBuffer buffer) {
        // Command packets are in the format of 4 ASCII numbers that represent a hex size
        // ex..."00A3"
        // Followed by the data that represents the command. The data is also a string.
        if (buffer.position() < 4) {
            mLength = -1;
            return;
        }
        try {
            mLength = Integer.parseInt(new String(buffer.array(), 0, 4), 16);
        } catch(NumberFormatException nfe) {
            mLength = -1;
            return;
        }

        if (buffer.position() < 4 + mLength) {
            mLength = -1;
            return;
        }
        mCommand = new String(buffer.array(), 4, mLength);
    }

    public int getLength() {
        return mLength;
    }

    public String getCommand() {
        return mCommand;
    }

    public int getTotalSize() {
        return mLength + 4;
    }
}
