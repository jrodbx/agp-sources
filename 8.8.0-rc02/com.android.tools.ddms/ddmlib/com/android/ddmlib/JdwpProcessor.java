/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.ddmlib;

import com.android.annotations.NonNull;
import java.nio.ByteBuffer;

public interface JdwpProcessor extends AutoCloseable {

    /**
     * Allow JDWP packet toward the debugged process to be inspection/intercepted
     *
     * @param buffer JDWP packet for the debugged process
     * @return A list of packets to send to the debugged and debugger.
     */
    @NonNull
    JdwpTrafficResponse onUpstreamPacket(@NonNull ByteBuffer buffer);

    /**
     * Allow JDWP packet toward the debugger to be inspection/intercepted
     *
     * @param buffer JDWP packet for the debugger
     * @return A list of packets to send to the debugged and debugger.
     */
    @NonNull
    JdwpTrafficResponse onDownstreamPacket(@NonNull ByteBuffer buffer);
}
