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

package com.android.ddmlib.jdwp.packets;

import com.android.annotations.NonNull;
import com.android.ddmlib.jdwp.JdwpPayload;
import com.android.ddmlib.jdwp.JdwpProtocol;

import java.nio.ByteBuffer;

public class IdSizesReply extends JdwpPayload {

    public int fieldIDSize;
    public int methodIDSize;
    public int objectIDSize;
    public int refTypeIDSize;
    public int frameIDSize;

    @Override
    public void parse(@NonNull ByteBuffer buffer, @NonNull JdwpProtocol protocol) {
        fieldIDSize = buffer.getInt();
        methodIDSize = buffer.getInt();
        objectIDSize = buffer.getInt();
        refTypeIDSize = buffer.getInt();
        frameIDSize = buffer.getInt();
    }
}
