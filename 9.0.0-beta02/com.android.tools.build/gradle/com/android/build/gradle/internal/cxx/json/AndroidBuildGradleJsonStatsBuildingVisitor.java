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

package com.android.build.gradle.internal.cxx.json;

import com.android.annotations.NonNull;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;

/** Streams over android_build_gradle.json and gathers statistics */
public class AndroidBuildGradleJsonStatsBuildingVisitor
        extends AndroidBuildGradleJsonStreamingVisitor {
    @NonNull private final GradleBuildVariant.NativeBuildConfigInfo.Builder config;

    @NonNull
    private GradleBuildVariant.NativeLibraryInfo.Builder libraryInfo =
            GradleBuildVariant.NativeLibraryInfo.newBuilder();

    private int runningSourceFileCount = 0;
    private boolean sawFirstFlags = false;

    public AndroidBuildGradleJsonStatsBuildingVisitor(
            @NonNull GradleBuildVariant.NativeBuildConfigInfo.Builder config) {
        this.config = config;
    }

    @NonNull
    public GradleBuildVariant.NativeBuildConfigInfo.Builder getConfig() {
        return config;
    }

    @Override
    protected void beginLibrary(@NonNull String libraryName) {
        this.libraryInfo = GradleBuildVariant.NativeLibraryInfo.newBuilder();
        this.runningSourceFileCount = 0;
        this.sawFirstFlags = false;
    }

    @Override
    protected void endLibrary() {
        this.libraryInfo.setSourceFileCount(runningSourceFileCount);
        this.config.addLibraries(this.libraryInfo);
        super.endLibrary();
    }

    @Override
    protected void visitLibraryFileSrc(@NonNull String src) {
        super.visitLibraryFileSrc(src);
        runningSourceFileCount++;
    }

    @Override
    protected void visitLibraryFileFlags(@NonNull String flags) {
        super.visitLibraryFileFlags(flags);
        if (!sawFirstFlags) {
            // Don't analyze all files' flags. Most of the time the first flags are a good
            // enough proxy.
            this.libraryInfo.setHasGlldbFlag(flags.contains("-glldb"));
            sawFirstFlags = true;
        }
    }
}
