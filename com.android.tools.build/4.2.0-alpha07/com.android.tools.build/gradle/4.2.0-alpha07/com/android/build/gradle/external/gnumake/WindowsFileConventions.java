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

package com.android.build.gradle.external.gnumake;

import com.android.annotations.NonNull;
import com.android.utils.StringHelperWindows;
import java.util.List;

/** File conventions for Windows. */
public class WindowsFileConventions extends AbstractOsFileConventions {

    @NonNull
    @Override
    public List<String> tokenizeCommandLineToEscaped(@NonNull String commandString) {
        return StringHelperWindows.tokenizeCommandLineToEscaped(commandString);
    }

    @NonNull
    @Override
    public List<String> tokenizeCommandLineToRaw(@NonNull String commandString) {
        return StringHelperWindows.tokenizeCommandLineToRaw(commandString);
    }

    @Override
    @NonNull
    public List<String> splitCommandLine(@NonNull String commandString) {
        return StringHelperWindows.splitCommandLine(commandString);
    }
}
