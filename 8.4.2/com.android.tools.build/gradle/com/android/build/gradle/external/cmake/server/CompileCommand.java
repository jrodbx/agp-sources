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
 * Represents a compilation of a single source file in the project. It contains the exact compiler
 * calls for the given translation unit in the project in machine-readable form. More info:
 * https://cmake.org/cmake/help/v3.5/variable/CMAKE_EXPORT_COMPILE_COMMANDS.html
 */
public class CompileCommand {
    public final String directory;
    public final String command;
    public final String file;

    public CompileCommand() {
        this.directory = null;
        this.command = null;
        this.file = null;
    }
}
