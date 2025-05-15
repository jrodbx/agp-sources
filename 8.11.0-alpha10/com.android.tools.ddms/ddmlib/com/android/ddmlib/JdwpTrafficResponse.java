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

public interface JdwpTrafficResponse {

    /**
     * Packet to be transmitted on the wire.
     *
     * @return Upstream and Downstream packets to be send to the debugger/debugged.
     */
    @NonNull
    JdwpTraffic getEdict();

    /**
     * Packet to be reported in tracer / inspector. See scache README.md for further explanations.
     *
     * @return Upstream and Downstream packets to be reported to tracer.
     */
    @NonNull
    JdwpTraffic getJournal();
}
