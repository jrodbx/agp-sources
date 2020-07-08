/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.build.gradle.external.gnumake;


import com.android.annotations.NonNull;
import com.google.common.base.Joiner;
import java.util.List;

/**
 * A shell command with n arguments.
 */
class CommandLine {
    @NonNull public final String executable;
    @NonNull public final List<String> escapedFlags;
    @NonNull public final List<String> rawFlags;

    CommandLine(
            @NonNull String executable,
            @NonNull List<String> escapedFlags,
            @NonNull List<String> rawFlags) {
        this.executable = executable;
        this.escapedFlags = escapedFlags;
        this.rawFlags = rawFlags;
    }

    @Override
    public boolean equals(Object obj) {
        CommandLine other = (CommandLine) obj;
        return executable.equals(other.executable) && rawFlags.equals(other.rawFlags);
    }

    @Override
    public String toString() {
        return executable + " " + Joiner.on(' ').join(rawFlags);
    }
}
