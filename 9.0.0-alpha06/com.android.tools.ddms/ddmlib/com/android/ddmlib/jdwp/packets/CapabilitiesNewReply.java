/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.ddmlib.jdwp.packets;

import com.android.annotations.NonNull;
import com.android.ddmlib.jdwp.JdwpPayload;
import com.android.ddmlib.jdwp.JdwpProtocol;
import java.nio.ByteBuffer;

/** The payload of a CapabilitiesNew reply of the JDWP protocol. */
public class CapabilitiesNewReply extends JdwpPayload {
    private ByteBuffer converted;

    public static int CAN_REDEFINE_CLASSES_IDX = 7;
    public static int CAN_REDEFINE_CLASSES_DEX_IDX = 31;

    @Override
    public void parse(@NonNull ByteBuffer buffer, @NonNull JdwpProtocol protocol) {
        // The secret 31th byte is set to true when the Android device can use RedefineClasses
        // capabilities on dex files. We will set the 7th byte (original CanRedefineClasses byte)
        // to true to let JDB knows it is ok to call RedefineClasses.
        if (buffer.get(CAN_REDEFINE_CLASSES_DEX_IDX) != 0) {
            buffer.put(CAN_REDEFINE_CLASSES_IDX, (byte) 1);
            converted = buffer;
        }

        converted = buffer;
    }

    /**
     * Convert the reply payload such that if we are talking to an android device and it has
     * RedefineClasses capabilities, the CanRedefineClasses byte is set to true. Note that that byte
     * is normally never set when talking to an Android device because it tries to prevent the
     * debugger from feed it plan old Java class files.
     *
     * @return
     */
    public ByteBuffer getConverted() {
        return converted;
    }
}
