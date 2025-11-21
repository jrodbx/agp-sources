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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Avd data returned from [IDevice.getAvdData].
 */
public class AvdData {

    @Nullable
    private final String name;

    @Nullable private final Path nioPath;

    public AvdData(@Nullable String name, @Nullable Path nioPath) {
        this.name = name;
        this.nioPath = nioPath;
    }

    /** @deprecated Use {@link #AvdData(String, Path)} */
    @Deprecated
    public AvdData(@Nullable String name, @Nullable String path) {
        this.name = name;
        nioPath = initNioPath(path);
    }

    @Nullable
    private static Path initNioPath(@Nullable String path) {
        if (path == null) {
            return null;
        }

        try {
            return Paths.get(path);
        } catch (InvalidPathException exception) {
            return null;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, nioPath);
    }

    @Override
    public boolean equals(@Nullable Object object) {
        if (!(object instanceof AvdData)) {
            return false;
        }

        AvdData avd = (AvdData) object;
        return Objects.equals(name, avd.name) && Objects.equals(nioPath, avd.nioPath);
    }

    /**
     * The name of the AVD or null if unavailable or this is a physical device.
     */
    @Nullable
    public String getName() {
        return name;
    }

    /**
     * Returns the absolute path to the virtual device in the file system or null if the emulator
     * console subcommand failed
     */
    @Nullable
    public Path getNioPath() {
        return nioPath;
    }

    /**
     * The path of the AVD or null if unavailable or this is a physical device.
     *
     * <p>The path is the absolute path to the virtual device in the file system. The path is
     * operating system dependent; it will have / name separators on Linux and \ separators on
     * Windows.
     *
     * @deprecated Use {@link #getNioPath}
     */
    @Deprecated
    @Nullable
    public String getPath() {
        if (nioPath == null) {
            return null;
        }

        return nioPath.toString();
    }
}
