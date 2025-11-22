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

import com.android.annotations.Nullable;

/**
 * CmakeInputsResult is a read-only attribute that report files used by CMake as part of the build
 * system itself. More info:
 * https://cmake.org/cmake/help/v3.7/manual/cmake-server.7.html#type-cmakeinputs
 */
public class CmakeInputsResult {
    @Nullable public final BuildFiles buildFiles[];
    @Nullable public final String cmakeRootDirectory;
    @Nullable public final String sourceDirectory;
    @Nullable public final String cookie;
    @Nullable public final String inReplyTo;
    @Nullable public final String type;

    private CmakeInputsResult() {
        this.buildFiles = null;
        this.cmakeRootDirectory = null;
        this.sourceDirectory = null;
        this.cookie = null;
        this.inReplyTo = null;
        this.type = null;
    }
}
