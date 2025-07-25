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

package com.android.build.gradle.external.cmake.server.receiver;

/**
 * Message triggered when the Cmake server processes a request and produces an output. More info:
 * https://cmake.org/cmake/help/v3.7/manual/cmake-server.7.html#type-message
 */
public class InteractiveMessage {
    public final String type;
    public final String cookie;
    public final String inReplyTo;
    public final String message;
    public final String errorMessage;
    public final String title;

    private InteractiveMessage() {
        this.type = null;
        this.cookie = null;
        this.inReplyTo = null;
        this.message = null;
        this.errorMessage = null;
        this.title = null;
    }
}
