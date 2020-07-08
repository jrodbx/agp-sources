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

package com.android.build.gradle.internal.pipeline;

import android.databinding.tool.util.Preconditions;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.tasks.AndroidVariantTask;
import com.google.common.collect.Iterables;
import java.io.File;
import java.util.Collection;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitivity;

/**
 * A base task with stream fields that properly use Gradle's input/output annotations to return the
 * stream's content as input/output.
 */
public abstract class StreamBasedTask extends AndroidVariantTask {

    /** Registered as task input in {@link #registerConsumedAndReferencedStreamInputs()}. */
    protected Collection<TransformStream> consumedInputStreams;
    /** Registered as task input in {@link #registerConsumedAndReferencedStreamInputs()}. */
    protected Collection<TransformStream> referencedInputStreams;

    protected IntermediateStream outputStream;

    @Nullable
    @Optional
    @OutputDirectory
    public File getStreamOutputFolder() {
        if (outputStream != null) {
            return outputStream.getRootLocation();
        }

        return null;
    }

    /**
     * We register each of the streams as a separate input in order to get incremental updates per
     * stream. Relative path sensitivity is used in the context of a single stream, and all
     * incremental input updates will be provided per stream.
     *
     * <p>DO NOT change this to a method annotated with {@link org.gradle.api.tasks.InputFiles} that
     * returns {@link Iterables<org.gradle.api.file.FileTree>} which would consider all streams as a
     * single input. In that case, file change is processed relative to all file tree roots.
     * Removing a file {@code test/A.class} from stream X, and adding a new {@code test/A.class} to
     * stream Y would yield only a single CHANGE update for file {@code test/A.class}, although we
     * would expect to get DELETED and ADDED file incremental update.
     */
    protected void registerConsumedAndReferencedStreamInputs() {
        Preconditions.checkNotNull(consumedInputStreams, "Consumed input streams not set.");
        Preconditions.checkNotNull(referencedInputStreams, "Referenced input streams not set.");
        for (TransformStream stream :
                Iterables.concat(consumedInputStreams, referencedInputStreams)) {
            // This cannot be PathSensitivity.RELATIVE, as transforms currently decide where to
            // place outputs based on input names, which are lost by this input, but full file path
            // is not a terrible approximation for this.
            // See https://issuetracker.google.com/68144982
            getInputs().files(stream.getAsFileTree()).withPathSensitivity(PathSensitivity.ABSOLUTE);
        }
    }
}
