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

package com.android.build.gradle.internal.ide;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.NativeToolchain;
import com.google.common.base.MoreObjects;
import java.io.File;
import java.io.Serializable;
import java.util.Objects;

/**
 * Implementation of NativeToolchain that is serializable.
 */
@Immutable
public final class NativeToolchainImpl implements NativeToolchain, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final String name;
    @Nullable
    private final File cCompilerExecutable;
    @Nullable
    private final File cppCompilerExecutable;

    public NativeToolchainImpl(
            @NonNull String name,
            @Nullable File cCompilerExecutable,
            @Nullable File cppCompilerExecutable) {
        this.name = name;
        this.cCompilerExecutable = cCompilerExecutable;
        this.cppCompilerExecutable = cppCompilerExecutable;
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @Nullable
    @Override
    public File getCCompilerExecutable() {
        return cCompilerExecutable;
    }

    @Nullable
    @Override
    public File getCppCompilerExecutable() {
        return cppCompilerExecutable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NativeToolchainImpl that = (NativeToolchainImpl) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(cCompilerExecutable, that.cCompilerExecutable) &&
                Objects.equals(cppCompilerExecutable, that.cppCompilerExecutable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, cCompilerExecutable, cppCompilerExecutable);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("cCompilerExecutable", cCompilerExecutable)
                .add("cppCompilerExecutable", cppCompilerExecutable)
                .toString();
    }
}
