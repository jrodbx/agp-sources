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
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.NativeSettings;
import com.google.common.base.MoreObjects;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of {@link NativeSettings}.
 */
@Immutable
public final class NativeSettingsImpl implements NativeSettings, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final String name;
    @NonNull
    private final List<String> compilerFlags;

    public NativeSettingsImpl(@NonNull String name, @NonNull List<String> compilerFlags) {
        this.name = name;
        this.compilerFlags = compilerFlags;
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @NonNull
    public List<String> getCompilerFlags() {
        return compilerFlags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NativeSettingsImpl that = (NativeSettingsImpl) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(compilerFlags, that.compilerFlags);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(name, compilerFlags);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("compilerFlags", compilerFlags)
                .toString();
    }
}
