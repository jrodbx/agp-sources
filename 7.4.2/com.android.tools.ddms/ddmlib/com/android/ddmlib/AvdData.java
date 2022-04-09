/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.ddmlib;

import com.android.annotations.Nullable;

import java.util.Objects;

/**
 * Avd data returned from [IDevice.getAvdData].
 */
public class AvdData {

    @Nullable
    private final String name;

    @Nullable
    private final String path;

    public AvdData(@Nullable String name, @Nullable String path) {
        this.name = name;
        this.path = path;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, path);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AvdData)) {
            return false;
        }
        AvdData other = (AvdData)obj;
        return Objects.equals(name, other.name) &&
               Objects.equals(path, other.path);
    }

    /**
     * The name of the AVD or null if unavailable or this is a physical device.
     */
    @Nullable
    public String getName() {
        return name;
    }

    /**
     * The path of the AVD or null if unavailable or this is a physical device.
     *
     * The path is the absolute path to the virtual device in the file system. The path is operating
     * system dependent; it will have / name separators on Linux and \ separators on Windows.
     */
    @Nullable
    public String getPath() {
        return path;
    }
}
