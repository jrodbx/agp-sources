/*
 * Copyright 2017 The Android Open Source Project
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

package com.android.tools.build.jetifier.processor.transform.bytecode

import com.android.tools.build.jetifier.processor.archive.ArchiveFile
import com.android.tools.build.jetifier.processor.transform.TransformationContext
import com.android.tools.build.jetifier.processor.transform.Transformer
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter

/**
 * The [Transformer] responsible for java byte code refactoring.
 */
class ByteCodeTransformer internal constructor(
    private val context: TransformationContext
) : Transformer {
    // Does not yet support single bytecode file transformation, file has to be within archive.
    override fun canTransform(file: ArchiveFile) = file.isClassFile() && !file.isSingleFile

    override fun runTransform(file: ArchiveFile) {
        val reader = ClassReader(file.data)
        val writer = ClassWriter(0 /* flags */)

        val remapper = CoreRemapperImpl(context, writer)
        try {
            reader.accept(remapper.classRemapper, 0 /* flags */)
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw InvalidByteCodeException("Error processing '${file.relativePath}' bytecode.", e)
        }

        if (!remapper.changesDone) {
            file.setNewDataSilently(writer.toByteArray())
        } else {
            file.setNewData(writer.toByteArray())
        }

        file.updateRelativePath(remapper.rewritePath(file.relativePath))
    }
}

/**
 * Thrown when rewriting a library with bytecode that can't be processed via ASM.
 */
// Happens for instance in b/140747218
class InvalidByteCodeException(
    message: String,
    exception: Throwable
) : Exception(message, exception)