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
import java.util.List;

/**
 * Receiver for profile spans which record events asynchronously.
 *
 * <p>Implemented by {@link ProcessProfileWriter}. This interface exists for clarity and
 * testability.
 *
 * <p>When an event starts the client should call {@link #allocateRecordId()} which allocates an
 * unique id for that event, which should be stored in {@link
 * GradleBuildProfileSpan.Builder#setId(long)}. After the event is finished it should be stored
 * using {@link #writeRecord(String, String, GradleBuildProfileSpan.Builder, List)}.
 *
 * <p>Uses:
 *
 * <ul>
 *   <li>{@link ThreadRecorder} provides a synchronous recording utilities on top of this class.
 *   <li>{@code RecordingBuildListener} is a listener for gradle events that generates records.
 * </ul>
 */
public interface ProfileRecordWriter {

    /** Allocate an unique ID for a profile record. Thread safe */
    long allocateRecordId();

    /** Append a span record to the build profile. Thread safe. */
    void writeRecord(
            @NonNull String project,
            @Nullable String variant,
            @NonNull final GradleBuildProfileSpan.Builder executionRecord,
            @NonNull List<GradleBuildProfileSpan> taskExecutionPhases);
}
