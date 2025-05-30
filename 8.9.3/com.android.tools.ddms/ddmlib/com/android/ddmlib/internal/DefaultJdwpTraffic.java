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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.JdwpTraffic;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class DefaultJdwpTraffic implements JdwpTraffic {

    final List<ByteBuffer> upStreamList = new ArrayList<>();
    final List<ByteBuffer> downStreamList = new ArrayList<>();

    public DefaultJdwpTraffic(@Nullable ByteBuffer upstream, @Nullable ByteBuffer downstream) {
        if (upstream != null) {
            upStreamList.add(upstream);
        }
        if (downstream != null) {
            downStreamList.add(downstream);
        }
    }

    @Override
    @NonNull
    public List<ByteBuffer> getToUpstream() {
        return upStreamList;
    }

    @Override
    @NonNull
    public List<ByteBuffer> getToDownstream() {
        return downStreamList;
    }
}
