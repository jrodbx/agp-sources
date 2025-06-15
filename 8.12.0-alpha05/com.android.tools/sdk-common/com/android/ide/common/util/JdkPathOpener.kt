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

import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException

/**
 * Implementation of [PathOpener] that understands the standard filesystems provided by the JDK.
 * API consumers normally do not need to reference this directly, since it is included as part of
 * [FileSystemRegistry] by default.
 */
object JdkPathOpener : PathOpener {
    override fun recognizes(path: PathString): Boolean = path.toPath() != null

    override fun isRegularFile(path: PathString): Boolean =
        path.toPath()?.let { Files.isRegularFile(it) } ?: false

    override fun open(path: PathString): InputStream {
        try {
            return path.toPath()?.let { Files.newInputStream(it) }
                ?: throw FileNotFoundException(path.toString())
        } catch (e: NoSuchFileException) {
            // Translate the nio exception type to an io exception type, as promised by the
            // contract on the base class method.
            throw FileNotFoundException(path.toString())
        }
    }

    override fun isDirectory(path: PathString): Boolean =
        path.toPath()?.let { Files.isDirectory(it) } ?: false
}
