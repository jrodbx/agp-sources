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
package com.android.ddmlib.internal;

import com.android.ddmlib.JdwpTraffic;
import java.nio.ByteBuffer;
import org.jetbrains.annotations.NotNull;

public class DefaultJdwpTrafficResponse implements com.android.ddmlib.JdwpTrafficResponse {

    private final ByteBuffer upStream;
    private final ByteBuffer downStream;

    public DefaultJdwpTrafficResponse(ByteBuffer upStream, ByteBuffer downStream) {
        this.upStream = upStream;
        this.downStream = downStream;
    }

    @Override
    @NotNull
    public JdwpTraffic getEdict() {
        return new DefaultJdwpTraffic(upStream, downStream);
    }

    @Override
    @NotNull
    public JdwpTraffic getJournal() {
        return new DefaultJdwpTraffic(null, null);
    }
}
