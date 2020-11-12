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
import java.io.File;
import java.util.List;

/**
 * The policy for handling filenames and command-lines in the given Gnu Make -nB output.
 *
 * <p>When the current host OS is the same as the host OS that produced the script then these are
 * just pass-through methods to the underlying java File implementation or command-line tokenizer.
 *
 * <p>If the current host is different from the host that produced the -nB output then it is up to
 * the caller to provide a policy that will work.
 */
public interface OsFileConventions {
    @NonNull
    List<String> tokenizeCommandLineToEscaped(@NonNull String commandString);

    @NonNull
    List<String> tokenizeCommandLineToRaw(@NonNull String commandString);

    @NonNull
    List<String> splitCommandLine(@NonNull String commandString);

    boolean isPathAbsolute(@NonNull String file);

    @NonNull
    String getFileParent(@NonNull String filename);

    @NonNull
    String getFileName(@NonNull String filename);

    @NonNull
    File toFile(@NonNull String filename);

    @NonNull
    File toFile(@NonNull File parent, @NonNull String child);
}
