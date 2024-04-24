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

package com.android.builder.dexing;

import static com.android.builder.dexing.ClassFileInput.CLASS_MATCHER;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.google.common.base.Preconditions;
import java.io.IOException;

/**
 * A single .class file abstraction. Relative path matches the package directory structure, and
 * convenience methods to obtain the content.
 */
public interface ClassFileEntry {

    /** Returns the entry name. */
    String name();

    /** Returns the entry size in bytes. */
    long getSize() throws IOException;

    /** Return the relative path from the root of the archive/folder abstraction. */
    String getRelativePath();

    /** Return the {@link ClassFileInput} that has produced this entry */
    @NonNull
    ClassFileInput getInput();

    /**
     * Read the content into a newly allocated byte[].
     *
     * @return file content as a byte[]
     * @throws IOException failed to read the file.
     */
    byte[] readAllBytes() throws IOException;

    /**
     * Read the content of the file into an existing byte[]
     *
     * @param bytes the bytes to read the content of the file into.
     * @return the number of bytes read.
     * @throws IOException failed to read the file or the buffer was too small.
     */
    int readAllBytes(byte[] bytes) throws IOException;

    /**
     * Takes the specified .class file, and changes its extension to .dex. It fails if invoked with
     * a file name that does not end in .class.
     */
    @NonNull
    static String withDexExtension(@NonNull String classFilePath) {
        Preconditions.checkState(
                CLASS_MATCHER.test(classFilePath),
                "Dex archives: setting .DEX extension only for .CLASS files");
        return classFilePath.substring(0, classFilePath.length() - SdkConstants.DOT_CLASS.length())
                + SdkConstants.DOT_DEX;
    }
}
