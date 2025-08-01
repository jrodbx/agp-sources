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
 * BuildFiles returned by cmakeInputs. More info:
 * https://cmake.org/cmake/help/v3.7/manual/cmake-server.7.html#type-cmakeinputs
 */
public class BuildFiles {
    public final boolean isCMake;
    public final boolean isTemporary;
    @Nullable public final String sources[];

    private BuildFiles() {
        this.isCMake = false;
        this.isTemporary = false;
        this.sources = null;
    }
}
