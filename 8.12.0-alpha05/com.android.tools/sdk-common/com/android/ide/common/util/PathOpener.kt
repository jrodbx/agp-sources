/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.ide.common.util

import java.io.InputStream

/**
 * Implementations of this interface are capable of performing filesystem-style operations on
 * [PathString] objects. They do this by delegating to one or more filesystem abstractions (such as
 * the JDK's nio or IntelliJ's VirtualFileSystem).
 */
interface PathOpener {
    /**
     * Returns true if and only if this [PathOpener] instance recognizes the protocol used in the
     * given [PathString]. This must be a fast-running operation.
     */
    fun recognizes(path: PathString): Boolean

    /**
     * Returns true if the given path exists, is recognized by this [PathOpener], and is a regular
     * file (not a directory). A regular file is anything from which an [InputStream] can be
     * obtained, for example a zip file entry or physical file on disk.
     */
    fun isRegularFile(path: PathString): Boolean

    /**
     * Returns true iff the given path is recognized by this [PathOpener], exists, and is a folder.
     * Folders are things that contain children.
     *
     * Whether or not a path is considered a directory depends on the implementation of the
     * filesystem. For example, a filesystem for the jar: protocol would likely consider a folder
     * inside a zip file to be a directory.
     */
    fun isDirectory(path: PathString): Boolean

    /**
     * Returns an [InputStream] containing the contents of the file pointed to by the given
     * [PathString]. Throws [IOException] if it is unable to open the stream. Throws
     * [FileNotFoundException] if the file does not exist in a filesystem known to this [PathOpener].
     */
    fun open(path: PathString): InputStream
}
