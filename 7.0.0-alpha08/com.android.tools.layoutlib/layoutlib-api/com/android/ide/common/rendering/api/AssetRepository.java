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
package com.android.ide.common.rendering.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Represents the interface for dealing with assets.
 */
public class AssetRepository {
    /**
     * Returns whether the IDE supports assets. This is used to determine if error messages should
     * be thrown.
     */
    public boolean isSupported() {
        return false;
    }

    @Nullable
    public InputStream openAsset(@NonNull String path, int mode) throws IOException {
        return null;
    }

    @Nullable
    public InputStream openNonAsset(int cookie, @NonNull String path, int mode) throws IOException {
        return null;
    }

    /**
     * Checks if the given path points to a file resource.
     */
    public boolean isFileResource(@NonNull String path) {
        return new File(path).isFile();
    }
}
