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
package com.android.ide.common.gradle.model;

import com.android.annotations.NonNull;
import com.android.builder.model.NativeLibrary;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Creates a deep copy of a {@link NativeLibrary}. */
public final class IdeNativeLibrary implements NativeLibrary, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @NonNull private final String myName;
    @NonNull private final String myAbi;
    @NonNull private final String myToolchainName;
    @NonNull private final List<File> myCIncludeDirs;
    @NonNull private final List<File> myCppIncludeDirs;
    @NonNull private final List<File> myCSystemIncludeDirs;
    @NonNull private final List<File> myCppSystemIncludeDirs;
    @NonNull private final List<String> myCDefines;
    @NonNull private final List<String> myCppDefines;
    @NonNull private final List<String> myCCompilerFlags;
    @NonNull private final List<String> myCppCompilerFlags;
    @NonNull private final List<File> myDebuggableLibraryFolders;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    public IdeNativeLibrary() {
        myName = "";
        myAbi = "";
        myToolchainName = "";
        myCIncludeDirs = Collections.emptyList();
        myCppCompilerFlags = Collections.emptyList();
        myCSystemIncludeDirs = Collections.emptyList();
        myCppIncludeDirs = Collections.emptyList();
        myCppSystemIncludeDirs = Collections.emptyList();
        myCDefines = Collections.emptyList();
        myCppDefines = Collections.emptyList();
        myCCompilerFlags = Collections.emptyList();
        myDebuggableLibraryFolders = Collections.emptyList();

        myHashCode = 0;
    }

    public IdeNativeLibrary(@NonNull NativeLibrary library) {
        myName = library.getName();
        myAbi = library.getAbi();
        myToolchainName = library.getToolchainName();
        myCIncludeDirs = ImmutableList.copyOf(library.getCIncludeDirs());
        myCppIncludeDirs = ImmutableList.copyOf(library.getCppIncludeDirs());
        myCSystemIncludeDirs = ImmutableList.copyOf(library.getCSystemIncludeDirs());
        myCppSystemIncludeDirs = ImmutableList.copyOf(library.getCppSystemIncludeDirs());
        myCDefines = ImmutableList.copyOf(library.getCDefines());
        myCppDefines = ImmutableList.copyOf(library.getCppDefines());
        myCCompilerFlags = ImmutableList.copyOf(library.getCCompilerFlags());
        myCppCompilerFlags = ImmutableList.copyOf(library.getCppCompilerFlags());
        myDebuggableLibraryFolders = ImmutableList.copyOf(library.getDebuggableLibraryFolders());

        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public String getAbi() {
        return myAbi;
    }

    @Override
    @NonNull
    public String getToolchainName() {
        return myToolchainName;
    }

    @Override
    @NonNull
    public List<File> getCIncludeDirs() {
        return myCIncludeDirs;
    }

    @Override
    @NonNull
    public List<File> getCppIncludeDirs() {
        return myCppIncludeDirs;
    }

    @Override
    @NonNull
    public List<File> getCSystemIncludeDirs() {
        return myCSystemIncludeDirs;
    }

    @Override
    @NonNull
    public List<File> getCppSystemIncludeDirs() {
        return myCppSystemIncludeDirs;
    }

    @Override
    @NonNull
    public List<String> getCDefines() {
        return myCDefines;
    }

    @Override
    @NonNull
    public List<String> getCppDefines() {
        return myCppDefines;
    }

    @Override
    @NonNull
    public List<String> getCCompilerFlags() {
        return myCCompilerFlags;
    }

    @Override
    @NonNull
    public List<String> getCppCompilerFlags() {
        return myCppCompilerFlags;
    }

    @Override
    @NonNull
    public List<File> getDebuggableLibraryFolders() {
        return myDebuggableLibraryFolders;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeNativeLibrary)) {
            return false;
        }
        IdeNativeLibrary library = (IdeNativeLibrary) o;
        return Objects.equals(myName, library.myName)
                && Objects.equals(myAbi, library.myAbi)
                && Objects.equals(myToolchainName, library.myToolchainName)
                && Objects.equals(myCIncludeDirs, library.myCIncludeDirs)
                && Objects.equals(myCppIncludeDirs, library.myCppIncludeDirs)
                && Objects.equals(myCSystemIncludeDirs, library.myCSystemIncludeDirs)
                && Objects.equals(myCppSystemIncludeDirs, library.myCppSystemIncludeDirs)
                && Objects.equals(myCDefines, library.myCDefines)
                && Objects.equals(myCppDefines, library.myCppDefines)
                && Objects.equals(myCCompilerFlags, library.myCCompilerFlags)
                && Objects.equals(myCppCompilerFlags, library.myCppCompilerFlags)
                && Objects.equals(myDebuggableLibraryFolders, library.myDebuggableLibraryFolders);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(
                myName,
                myAbi,
                myToolchainName,
                myCIncludeDirs,
                myCppIncludeDirs,
                myCSystemIncludeDirs,
                myCppSystemIncludeDirs,
                myCDefines,
                myCppDefines,
                myCCompilerFlags,
                myCppCompilerFlags,
                myDebuggableLibraryFolders);
    }

    @Override
    public String toString() {
        return "IdeNativeLibrary{"
                + "myName='"
                + myName
                + '\''
                + ", myAbi='"
                + myAbi
                + '\''
                + ", myToolchainName='"
                + myToolchainName
                + '\''
                + ", myCIncludeDirs="
                + myCIncludeDirs
                + ", myCppIncludeDirs="
                + myCppIncludeDirs
                + ", myCSystemIncludeDirs="
                + myCSystemIncludeDirs
                + ", myCppSystemIncludeDirs="
                + myCppSystemIncludeDirs
                + ", myCDefines="
                + myCDefines
                + ", myCppDefines="
                + myCppDefines
                + ", myCCompilerFlags="
                + myCCompilerFlags
                + ", myCppCompilerFlags="
                + myCppCompilerFlags
                + ", myDebuggableLibraryFolders="
                + myDebuggableLibraryFolders
                + "}";
    }
}
