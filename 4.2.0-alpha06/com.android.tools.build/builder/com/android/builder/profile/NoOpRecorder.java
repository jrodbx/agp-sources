/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.builder.profile;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import com.google.wireless.android.sdk.stats.GradleTransformExecution;
import java.io.IOException;
import java.io.UncheckedIOException;

public final class NoOpRecorder implements Recorder {

    @Nullable
    @Override
    public <T> T record(
            @NonNull GradleBuildProfileSpan.ExecutionType executionType,
            @NonNull String projectPath,
            @Nullable String variant,
            @NonNull Block<T> block) {
        try {
            return block.call();
        } catch (Exception e) {
            block.handleException(e);
        }
        return null;
    }


    @Override
    public void record(
            @NonNull GradleBuildProfileSpan.ExecutionType executionType,
            @NonNull String projectPath,
            @Nullable String variant,
            @NonNull VoidBlock block) {
        try {
            block.call();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    @Nullable
    @Override
    public <T> T record(
            @NonNull GradleBuildProfileSpan.ExecutionType executionType,
            @Nullable GradleTransformExecution transform,
            @NonNull String projectPath,
            @Nullable String variant,
            @NonNull Block<T> block) {
        return record(executionType, projectPath, variant, block);
    }
}
