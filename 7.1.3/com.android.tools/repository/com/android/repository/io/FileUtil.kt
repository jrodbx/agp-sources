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

package com.android.repository.io

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Recursively determine the total size of files in a directory.
 *
 * @return The total size in bytes.
 * @throws IOException If there's a problem reading a file.
 *
 */
@Throws(IOException::class)
fun Path.recursiveSize(): Long {
  var size: Long = 0
  Files.walkFileTree(this, object : SimpleFileVisitor<Path>() {
    @Throws(IOException::class)
    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      size += attrs.size()
      return FileVisitResult.CONTINUE
    }
  })
  return size
}