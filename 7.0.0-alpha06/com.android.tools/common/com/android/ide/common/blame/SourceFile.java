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

package com.android.ide.common.blame;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.google.common.base.Objects;
import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Represents a source file. Note: Since the same file may have different representations (eg a/b vs
 * a/../a/b), it is better to use absolute files, otherwise the equals/hash method of this class may
 * fail.
 */
@Immutable
public final class SourceFile implements Serializable {

    @NonNull
    public static final SourceFile UNKNOWN = new SourceFile();

    /** The absolute file path to the file, used for accessing the file contents. */
    @Nullable private final String mFilePath;

    /**
     * The path used as reference to the source file. If null, the mFilePath is used as the main
     * source path. If mSourcePath is set, properties should only expose the mSourcePath, unless the
     * file itself is being accessed.
     */
    @Nullable private String mSourcePath;

    /**
     * A human readable description
     *
     * <p>Usually the file name is OK for the short output, but for the manifest merger, where all
     * of the files will be named AndroidManifest.xml the variant name is more useful.
     */
    @Nullable private final String mDescription;

    @SuppressWarnings("NullableProblems")
    public SourceFile(@NonNull File sourceFile, @Nullable String description) {
        mFilePath = sourceFile.getAbsolutePath();
        mDescription = description;
    }

    public SourceFile(@SuppressWarnings("NullableProblems") @NonNull File sourceFile) {
        this(sourceFile, null);
    }

    public SourceFile(@SuppressWarnings("NullableProblems") @NonNull String description) {
        mFilePath = null;
        mDescription = description;
    }

    private SourceFile() {
        mFilePath = null;
        mDescription = null;
    }

    public void setOverrideSourcePath(@NonNull String value) {
        mSourcePath = value;
    }

    @Nullable
    public File getSourceFile() {
        if (mFilePath != null) {
            return new File(mFilePath);
        }
        return null;
    }

    @Nullable
    public String getSourcePath() {
        if (mSourcePath != null) {
            return mSourcePath;
        }
        if (mFilePath != null) {
            return Paths.get(mFilePath).toAbsolutePath().toString();
        }
        return null;
    }

    @Nullable
    public String getDescription() {
        return mDescription;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SourceFile)) {
            return false;
        }
        SourceFile other = (SourceFile) obj;

        return Objects.equal(mDescription, other.mDescription)
                && Objects.equal(getSourcePath(), other.getSourcePath());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getSourcePath(), mDescription);
    }

    @Override
    @NonNull
    public String toString() {
        return print(false /* shortFormat */);
    }

    @NonNull
    public String print(boolean shortFormat) {
        String path;
        if (mSourcePath != null) {
            path = mSourcePath;
        } else if (mFilePath != null) {
            path = mFilePath;
        } else {
            if (mDescription == null) {
                return "Unknown source file";
            }
            return mDescription;
        }
        String fileName = new File(path).getName();
        String fileDisplayName = shortFormat ? fileName : path;
        if (mDescription == null || mDescription.equals(fileName)) {
            return fileDisplayName;
        } else {
            return String.format("[%1$s] %2$s", mDescription, fileDisplayName);
        }
    }
}
