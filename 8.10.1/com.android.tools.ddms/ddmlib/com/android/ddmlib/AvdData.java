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

    @Nullable private final String name;
    @Nullable private final Path avdFolder;

    public AvdData(@Nullable String name, @Nullable Path avdFolder) {
        this.name = name;
        this.avdFolder = avdFolder;
    }

    /** @deprecated Use {@link #AvdData(String, Path)} */
    @Deprecated
    public AvdData(@Nullable String name, @Nullable String path) {
        this.name = name;
        avdFolder = getAvdFolder(path);
    }

    @Nullable
    private static Path getAvdFolder(@Nullable String path) {
        try {
            return path == null ? null : Paths.get(path);
        } catch (InvalidPathException exception) {
            return null;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, avdFolder);
    }

    @Override
    public boolean equals(@Nullable Object object) {
        if (!(object instanceof AvdData)) {
            return false;
        }

        AvdData avd = (AvdData) object;
        return Objects.equals(name, avd.name) && Objects.equals(avdFolder, avd.avdFolder);
    }

    /** The name of the AVD, or null if unavailable or this is a physical device. */
    @Nullable
    public String getName() {
        return name;
    }

    /**
     * Returns the absolute path to the virtual device directory in the file system, or null if the
     * emulator console subcommand failed.
     */
    @Nullable
    public Path getAvdFolder() {
        return avdFolder;
    }

    /**
     * @deprecated Use {@link #getAvdFolder}
     */
    @Deprecated
    public Path getNioPath() {
        return avdFolder;
    }

    /**
     * @deprecated Use {@link #getAvdFolder}
     */
    @Deprecated
    @Nullable
    public String getPath() {
        return avdFolder == null ? null : avdFolder.toString();
    }
}
