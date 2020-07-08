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
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeSettings;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class IdeNativeSettingsImpl implements IdeNativeSettings, Serializable {
    @NonNull private final String myName;
    @NonNull private final List<String> myCompilerFlags;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    public IdeNativeSettingsImpl() {
        myName = "";
        myCompilerFlags = Collections.emptyList();

        myHashCode = 0;
    }

    public IdeNativeSettingsImpl(@NonNull String name, @NonNull List<String> compilerFlags) {
        myName = name;
        myCompilerFlags = compilerFlags;

        myHashCode = calculateHashCode();
    }

    @NonNull
    @Override
    public String getName() {
        return myName;
    }

    @NonNull
    @Override
    public List<String> getCompilerFlags() {
        return myCompilerFlags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeNativeSettingsImpl)) {
            return false;
        }
        IdeNativeSettingsImpl settings = (IdeNativeSettingsImpl) o;
        return Objects.equals(myName, settings.myName)
                && Objects.equals(myCompilerFlags, settings.myCompilerFlags);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myName, myCompilerFlags);
    }

    @Override
    public String toString() {
        return "IdeNativeSettings{"
                + "myName='"
                + myName
                + '\''
                + ", myCompilerFlags="
                + myCompilerFlags
                + "}";
    }
}
