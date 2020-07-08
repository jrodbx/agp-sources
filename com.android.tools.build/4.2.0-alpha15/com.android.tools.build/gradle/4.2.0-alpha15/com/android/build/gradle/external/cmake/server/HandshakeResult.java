/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.external.cmake.server;

/**
 * Result to the Handshake request sent to the Cmake server. More Info:
 * https://cmake.org/cmake/help/v3.8/manual/cmake-server.7.html#type-handshake
 */
public class HandshakeResult {
    public final String type;
    public final String cookie;
    public final String inReplyTo;

    private HandshakeResult() {
        this.type = null;
        this.cookie = null;
        this.inReplyTo = null;
    }
}
