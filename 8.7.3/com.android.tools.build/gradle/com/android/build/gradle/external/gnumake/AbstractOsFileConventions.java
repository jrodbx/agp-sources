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

package com.android.build.gradle.external.gnumake;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import java.io.File;

/**
 * Default implementation of the 'File' methods which are all pass-through. These are here to be
 * overridden in tests that work with -nB scripts from other platforms.
 */
public abstract class AbstractOsFileConventions implements OsFileConventions {

    /**
     * Return an implementation of ScriptOSFileConventions that will work when the Host OS is the
     * same as the OS that generated the ndk-build -nB script.
     */
    public static OsFileConventions createForCurrentHost() {
        return SdkConstants.PLATFORM_WINDOWS == SdkConstants.currentPlatform()
                ? new WindowsFileConventions()
                : new PosixFileConventions();
    }

    @Override
    public boolean isPathAbsolute(@NonNull String file) {
        return new File(file).isAbsolute();
    }

    @NonNull
    @Override
    public String getFileParent(@NonNull String filename) {
        return new File(filename).getParent();
    }

    @NonNull
    @Override
    public String getFileName(@NonNull String filename) {
        return new File(filename).getName();
    }

    @NonNull
    @Override
    public File toFile(@NonNull String filename) {
        return new File(filename);
    }

    @NonNull
    @Override
    public File toFile(@NonNull File parent, @NonNull String child) {
        return new File(parent, child);
    }
}
