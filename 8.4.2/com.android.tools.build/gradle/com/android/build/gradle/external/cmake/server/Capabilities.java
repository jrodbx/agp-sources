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
 * Capabilities is a read-only attribute of global Cmake server settings. More info:
 * https://cmake.org/cmake/help/v3.7/manual/cmake-server.7.html#type-globalsettings
 */
public class Capabilities {
    public final Generator generators[];
    public final Boolean serverMode;
    public final Version version;

    private Capabilities() {
        this.generators = null;
        this.serverMode = null;
        this.version = null;
    }
}
