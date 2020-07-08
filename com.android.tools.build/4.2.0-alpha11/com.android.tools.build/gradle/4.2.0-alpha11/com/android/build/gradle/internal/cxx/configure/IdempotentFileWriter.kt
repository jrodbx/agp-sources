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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.cxx.logging.infoln
import java.io.File

/**
 * For updating files without changing timestamps when the content hasn't changed.
 * If the same file would be written twice, only writes the last.
 */
class IdempotentFileWriter {
    private val map : MutableMap<String, String> = mutableMapOf()

    /**
     * Add a file and it's content to write.
     */
    fun addFile(path : String, content : String) {
        map[path] = content
    }

    /**
     * Write all files that have changed. Skip the ones that haven't.
     */
    fun write() : List<String> {
        val written = mutableListOf<String>()
        for (path in map.keys) {
            val file = File(path)
            val content = map[path]!!
            if (file.isFile) {
                val originalContent = file.readText()
                if (originalContent == content) {
                    // Content wouldn't change so don't write.
                    infoln("Not writing $file because there was no change")
                    continue
                }
            }
            infoln("Writing $file")
            file.parentFile.mkdirs()
            file.writeText(content)
            written.add(path)
        }
        return written
    }
}