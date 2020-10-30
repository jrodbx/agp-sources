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

package com.android.builder.files;

import com.android.annotations.NonNull;
import com.android.utils.FileUtils;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import java.io.File;


/**
 * Representation of a file with respect to a base directory. A {@link RelativeFile} contains
 * information on the file, the base directory and the relative path from the base directory to
 * the file. The relative path is kept in OS independent form with sub directories separated by
 * slashes.
 *
 * <p>Neither the file nor the base need to exist. They are treated as abstract paths.
 */
public class RelativeFile {

    public enum Type {
        DIRECTORY,
        JAR,
    }

    /** For {@link Type#JAR}, the jar. For {@link Type#DIRECTORY} the file within the directory. */
    @NonNull private final File file;

    /** The OS independent path from base to file, including the file name in the end. */
    @NonNull private final String relativePath;

    @NonNull public final Type type;

    /**
     * Creates a new relative file.
     *
     * @param base the base directory.
     * @param file the file, must not be the same as the base directory and must be located inside
     *     {@code base}
     */
    public RelativeFile(@NonNull File base, @NonNull File file) {
        this(
                file,
                FileUtils.toSystemIndependentPath(
                        FileUtils.relativePossiblyNonExistingPath(file, base)),
                Type.DIRECTORY);

        Preconditions.checkArgument(
                !base.equals(file), "Base must not equal file. Given: %s", base.getAbsolutePath());
    }

    /**
     * Creates a new relative file.
     *
     * @param base the base jar.
     * @param relativePath the relative path to the file.
     */
    public RelativeFile(@NonNull File base, @NonNull String relativePath) {
        this(base, relativePath, Type.JAR);
    }

    public static RelativeFile fileInDirectory(@NonNull String relativePath, @NonNull File file) {
        return new RelativeFile(file, relativePath, Type.DIRECTORY);
    }

    /**
     * Creates a new relative file.
     *
     * @param file the base jar, or the file within the directory.
     * @param relativePath the relative path to the file.
     * @param type the type of the base.
     */
    private RelativeFile(@NonNull File file, @NonNull String relativePath, @NonNull Type type) {
        Preconditions.checkArgument(!relativePath.isEmpty(), "Relative path cannot be empty");
        this.file = file;
        this.relativePath = relativePath;
        this.type = type;
    }

    /**
     * Obtains the base directory or jar.
     *
     * <p>Only applicable when {@link #getType()} == {@link Type#JAR}
     *
     * @return the base directory or jar as provided when created the object
     */
    @NonNull
    public File getBase() {
        Preconditions.checkState(getType() == Type.JAR, "Only applicable for jars");
        return file;
    }

    /**
     * Obtains the OS independent path. The general contract of the normalized relative path is that
     * by replacing the slashes by file separators in the relative path and appending it to the base
     * directory's path, the resulting path is the file's path
     *
     * @return the normalized path, separated by slashes; directories have a terminating slash
     */
    @NonNull
    public String getRelativePath() {
        return relativePath;
    }

    @NonNull
    public Type getType() {
        return type;
    }

    /**
     * Returns the actual file from the directory.
     *
     * <p>Only applicable when {@link #getType()} == {@link Type#DIRECTORY}
     *
     * @return
     */
    @NonNull
    public File getFile() {
        Preconditions.checkState(getType() == Type.DIRECTORY, "Only applicable for directories");
        return file;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(file, relativePath);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RelativeFile)) {
            return false;
        }

        RelativeFile other = (RelativeFile) obj;
        return Objects.equal(file, other.file)
                && Objects.equal(relativePath, other.relativePath)
                && Objects.equal(type, other.type);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("base", file)
                .add("path", relativePath)
                .add("type", type)
                .toString();
    }
}
