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

package com.android.utils;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.annotations.VisibleForTesting;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import org.jetbrains.annotations.NotNull;

/**
 * Provides access to system properties and environment variables. Most of the time using {@link
 * EnvironmentProvider#DIRECT} should suffice, but custom implementations may access them through
 * some other APIs.
 */
public interface EnvironmentProvider {
    @Nullable
    String getSystemProperty(@NonNull String key);

    @Nullable
    String getEnvVariable(@NonNull String key);

    @NonNull
    default FileSystem getFileSystem() {
        return FileSystems.getDefault();
    }

    @VisibleForTesting
    class DirectEnvironmentProvider implements EnvironmentProvider {
        @Nullable
        @Override
        public String getSystemProperty(@NonNull String key) {
            return System.getProperty(key);
        }

        @Nullable
        @Override
        public String getEnvVariable(@NonNull String key) {
            return System.getenv(key);
        }

        /**
         * The filesystem to be used during tests. Should probably be set only via
         * AndroidLocationsSingletonRule.
         */
        @Nullable @VisibleForTesting public FileSystem fileSystemOverrideForTests = null;

        @NotNull
        @Override
        public FileSystem getFileSystem() {
            return fileSystemOverrideForTests != null
                    ? fileSystemOverrideForTests
                    : EnvironmentProvider.super.getFileSystem();
        }
    }

    DirectEnvironmentProvider DIRECT = new DirectEnvironmentProvider();
}
