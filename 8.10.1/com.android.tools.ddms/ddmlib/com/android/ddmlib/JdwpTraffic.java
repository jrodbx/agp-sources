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
import java.util.List;

public interface JdwpTraffic {

    /** @return List of JDWP packets to be sent to the debugged process. */
    @NonNull
    List<ByteBuffer> getToUpstream();

    /** @return List of JDWP packets to be sent to the debugger. */
    @NonNull
    List<ByteBuffer> getToDownstream();
}
