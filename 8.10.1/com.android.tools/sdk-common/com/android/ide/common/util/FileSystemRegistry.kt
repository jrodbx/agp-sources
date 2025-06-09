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
@file:JvmName("PathStrings")
package com.android.ide.common.util

import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Singleton registry of all filesystems known to the application.
 */
object FileSystemRegistry : PathOpener {
    private val paths = arrayListOf<PathOpener>(JdkPathOpener)
    private @Volatile var reversedPaths = paths.reversed()
    private val mutex = Object()

    /**
     * Mounts the filesystems from the given [PathOpener]. Has no effect if an identical object
     * is already mounted. The newly-added opener is the highest priority opener and may override
     * previously-installed filesystems.
     */
    fun mount(opener: PathOpener) {
        synchronized (mutex) {
            if (!paths.contains(opener)) {
                paths.add(opener)
                reversedPaths = paths.reversed()
            }
        }
    }

    /**
     * Unmounts the filesystems from the given [PathOpener]. Has no effect if no such opener
     * is already mounted.
     */
    fun unmount(opener: PathOpener) {
        synchronized (mutex) {
            if (paths.remove(opener)) {
                reversedPaths = paths.reversed()
            }
        }
    }

    /**
     * Returns the [PathOpener] for the given path.
     */
    private fun openerFor(path: PathString): PathOpener?
        = reversedPaths.firstOrNull {it.recognizes(path)}

    override fun recognizes(path: PathString): Boolean
        = openerFor(path) != null

    override fun isRegularFile(path: PathString): Boolean
        = openerFor(path)?.isRegularFile(path) ?: false

    override fun open(path: PathString): InputStream
        = openerFor(path)?.open(path) ?: throw FileNotFoundException(path.toString())

    override fun isDirectory(path: PathString): Boolean
        = openerFor(path)?.isDirectory(path) ?: false
}

/**
 * Returns an [InputStream] containing the contents of the file pointed to by the given
 * [PathString]. Throws [IOException] if it is unable to open the stream. Throws
 * [FileNotFoundException] if the file does not exist in a filesystem known to the
 * [FileSystemRegistry].
 */
fun PathString.inputStream() = FileSystemRegistry.open(this)

/**
 * Returns true if the given path exists, is recognized by the [FileSystemRegistry], and is a
 * regular file (not a directory). A regular file is a thing from which an [InputStream] can be
 * obtained.
 */
fun PathString.isRegularFile() = FileSystemRegistry.isRegularFile(this)

/**
 * Returns true iff the given path is known to some filesystem in [FileSystemRegistry], exists, and
 * is a folder. Folders are things that may contain children.
 */
fun PathString.isDirectory() = FileSystemRegistry.isRegularFile(this)
