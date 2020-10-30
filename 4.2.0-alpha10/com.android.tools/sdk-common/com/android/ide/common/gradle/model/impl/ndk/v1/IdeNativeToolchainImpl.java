/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.ide.common.gradle.model.impl.ndk.v1;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeToolchain;
import java.io.File;
import java.io.Serializable;
import java.util.Objects;

public final class IdeNativeToolchainImpl implements IdeNativeToolchain, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @NonNull private final String myName;
    @Nullable private final File myCCompilerExecutable;
    @Nullable private final File myCppCompilerExecutable;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    public IdeNativeToolchainImpl() {
        myName = "";
        myCCompilerExecutable = null;
        myCppCompilerExecutable = null;

        myHashCode = 0;
    }

    public IdeNativeToolchainImpl(
            @NonNull String name,
            @Nullable File cCompilerExecutable,
            @Nullable File cppCompilerExecutable) {
        myName = name;
        myCCompilerExecutable = cCompilerExecutable;
        myCppCompilerExecutable = cppCompilerExecutable;

        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @Nullable
    public File getCCompilerExecutable() {
        return myCCompilerExecutable;
    }

    @Override
    @Nullable
    public File getCppCompilerExecutable() {
        return myCppCompilerExecutable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeNativeToolchainImpl)) {
            return false;
        }
        IdeNativeToolchainImpl toolchain = (IdeNativeToolchainImpl) o;
        return Objects.equals(myName, toolchain.myName)
                && Objects.equals(myCCompilerExecutable, toolchain.myCCompilerExecutable)
                && Objects.equals(myCppCompilerExecutable, toolchain.myCppCompilerExecutable);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myName, myCCompilerExecutable, myCppCompilerExecutable);
    }

    @Override
    public String toString() {
        return "IdeNativeToolchain{"
                + "myName='"
                + myName
                + '\''
                + ", myCCompilerExecutable="
                + myCCompilerExecutable
                + ", myCppCompilerExecutable="
                + myCppCompilerExecutable
                + "}";
    }
}
