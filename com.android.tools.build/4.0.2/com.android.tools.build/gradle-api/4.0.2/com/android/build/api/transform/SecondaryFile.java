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

package com.android.build.api.transform;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.io.File;
import java.util.function.Supplier;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;

/**
 * A secondary input file(s) for a {@link Transform}.
 *
 * A secondary input is part of the transform inputs and can be decorated to indicate if a change
 * to the input would trigger a non incremental {@link Transform#transform(TransformInvocation)}.
 * call.
 *
 * The collection should only contain one file.
 */
public class SecondaryFile {

    /**
     * Creates a {@link SecondaryFile} instance that, when modified, will not trigger a full,
     * non-incremental build.
     *
     * @deprecated Use {@link #incremental(FileCollection)}
     */
    @Deprecated
    public static SecondaryFile incremental(@NonNull File file) {
        return new SecondaryFile(file, true);
    }

    /**
     * Creates a {@link SecondaryFile} instance that, when modified, will not trigger a full,
     * non-incremental build.
     */
    public static SecondaryFile incremental(@NonNull FileCollection file) {
        return new SecondaryFile(file, true);
    }

    /**
     * Creates a {@link SecondaryFile} instance that, when modified, will not trigger a full
     * non-incremental build.
     *
     * @param file a supplier of file collection.
     */
    public static SecondaryFile incremental(@NonNull Supplier<FileCollection> file) {
        return new SecondaryFile(file, null, true);
    }

    /**
     * Creates a {@link SecondaryFile} instance that, when modified, will always trigger a full,
     * non-incremental build.
     *
     * @deprecated Use {@link #nonIncremental(FileCollection)}
     */
    @Deprecated
    public static SecondaryFile nonIncremental(@NonNull File file) {
        return new SecondaryFile(file, false);
    }

    /**
     * Creates a {@link SecondaryFile} instance that, when modified, will always trigger a full,
     * non-incremental build.
     */
    public static SecondaryFile nonIncremental(@NonNull FileCollection file) {
        return new SecondaryFile(file, false);
    }

    /**
     * Creates a {@link SecondaryFile} instance that, when modified, will always trigger a full
     * non-incremental build.
     *
     * @param file a supplier of file collection.
     */
    public static SecondaryFile nonIncremental(@NonNull Supplier<FileCollection> file) {
        return new SecondaryFile(file, null, false);
    }

    private final boolean supportsIncrementalBuild;
    @Nullable
    private final File secondaryInputFile;
    @Nullable private final Supplier<FileCollection> secondaryInputFileCollection;

    /**
     * @param secondaryInputFile the {@link File} this {@link SecondaryFile} will point to
     * @param supportsIncrementalBuild if true, changes to the file can be handled incrementally
     *                                 by the transform
     * @see #incremental(File)
     * @see #nonIncremental(File)
     */
    public SecondaryFile(@NonNull File secondaryInputFile,
            boolean supportsIncrementalBuild) {
        this(null, secondaryInputFile, supportsIncrementalBuild);
    }

    /**
     * @param secondaryInputFile the {@link FileCollection} this {@link SecondaryFile} will point to
     * @param supportsIncrementalBuild if true, changes to the file can be handled incrementally
     *                                 by the transform
     * @see #incremental(File)
     * @see #nonIncremental(File)
     */
    private SecondaryFile(
            @NonNull FileCollection secondaryInputFile,
            boolean supportsIncrementalBuild) {
        this(() -> secondaryInputFile, null, supportsIncrementalBuild);
    }

    private SecondaryFile(
            @Nullable Supplier<FileCollection> secondaryInputFileCollection,
            @Nullable File secondaryInputFile,
            boolean supportsIncrementalBuild) {
        this.secondaryInputFileCollection = secondaryInputFileCollection;
        this.supportsIncrementalBuild = supportsIncrementalBuild;
        this.secondaryInputFile = secondaryInputFile;
    }

    /**
     * Returns true if this secondary input changes can be handled by the receiving {@link Transform}
     * incrementally. If false, a change to the file returned by {@link #getFileCollection}
     * will trigger a non incremental build.
     * @return true when the input file changes can be handled incrementally, false otherwise.
     */
    public boolean supportsIncrementalBuild() {
        return supportsIncrementalBuild;
    }

    /**
     * Returns the {@link FileCollection} handle for this secondary input to a {@link Transform}
     *
     * If this {@link SecondaryFile} is constructed with {@link File}, the supplied {@link Project}
     * will be used to create a {@link FileCollection}.
     * @param project for creating a FileCollection when necessary.
     * @return FileCollection of this SecondaryFile
     */
    public FileCollection getFileCollection(@NonNull Project project) {
        if (secondaryInputFileCollection != null) {
            return secondaryInputFileCollection.get();
        }

        return project.files(secondaryInputFile);
    }

    /**
     * Returns the file handle for this secondary input to a Transform.
     * @return a file handle.
     *
     * @deprecated use {@link #getFileCollection}
     */
    @Deprecated
    public File getFile() {
        //noinspection VariableNotUsedInsideIf
        if (secondaryInputFileCollection != null) {
            throw new RuntimeException(
                    "Calling #getFile() on a FileCollection-based SecondaryFile."
                            + " Use #getFileCollection() instead");
        }

        return secondaryInputFile;
    }
}
