/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.ide.common.process;

import com.android.annotations.NonNull;

/**
 * An exception thrown when running a build command.
 */
public class BuildCommandException extends ProcessException {
    private static final String BUILD_COMMAND_FAILED = "Build command failed.";

    public BuildCommandException(String message) {
        super(message.replaceAll("\r\n", "\n"));
    }

    /**
     * Computes the error message to display for this error
     */
    @NonNull
    @Override
    public String getMessage() {
        return String.format("%s\n%s", BUILD_COMMAND_FAILED, super.getMessage());
    }

    @NonNull
    @Override
    public String toString() {
        return getMessage();
    }
}
