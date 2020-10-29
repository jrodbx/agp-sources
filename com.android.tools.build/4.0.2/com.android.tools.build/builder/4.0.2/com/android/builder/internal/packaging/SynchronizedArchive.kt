/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.builder.internal.packaging

import com.android.zipflinger.Archive
import com.android.zipflinger.BytesSource
import com.android.zipflinger.ZipSource

/** A synchronized wrapper around an Archive instance */
class SynchronizedArchive(private val archive: Archive) : Archive {

    override fun add(source: BytesSource) {
        synchronized(archive) {
            archive.add(source)
        }
    }

    override fun add(sources: ZipSource) {
        synchronized(archive) {
            archive.add(sources)
        }
    }

    override fun delete(name: String) {
        synchronized(archive) {
            archive.delete(name)
        }
    }

    override fun close() {
        synchronized(archive) {
            archive.close()
        }
    }
}