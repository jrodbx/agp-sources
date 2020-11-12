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

package com.android.builder.dexing

import com.google.common.io.Closer
import java.io.File
import java.io.Serializable
import java.util.stream.Stream
import kotlin.math.abs

/**
 * A bucket of class files. Multiple buckets are split from a [ClassBucketGroup] using an internal
 * algorithm, and each one is identified by a bucket number.
 */
class ClassBucket(val bucketGroup: ClassBucketGroup, val bucketNumber: Int) :
    Serializable {

    /**
     * Returns a subset of the class files in this bucket, selected by the given filter.
     *
     * @param filter the filter to select a subset of the class files in this bucket
     * @param closer a [Closer] to register objects to close after the returned stream is finished
     */
    fun getClassFiles(filter: (File, String) -> Boolean, closer: Closer): Stream<ClassFileEntry> {
        var classFiles = Stream.empty<ClassFileEntry>()
        for (root in bucketGroup.getRoots()) {
            val classFileInput = ClassFileInputs.fromPath(root.toPath())
            closer.register(classFileInput)
            classFiles = Stream.concat(
                classFiles,
                classFileInput.entries { rootPath, relativePath ->
                    isMemberOfBucket(rootPath.toFile(), relativePath)
                            && filter(rootPath.toFile(), relativePath)
                })
        }
        return classFiles
    }

    /**
     * Returns `true` if the given class file located by a `rootPath` and a `relativePath` can be a
     * member of this bucket. The class file may or may not exist.
     */
    private fun isMemberOfBucket(rootPath: File, relativePath: String): Boolean {
        check(rootPath in bucketGroup.getRoots().toSet()) {
            "Unexpected rootPath: $rootPath"
        }
        check(!File(relativePath).isAbsolute) { "Unexpected absolute path: $relativePath" }
        check(isClassFile(relativePath)) { "Unexpected non-class file: $relativePath" }

        return if (bucketGroup is JarBucketGroup) {
            abs(relativePath.hashCode()) % bucketGroup.numOfBuckets == bucketNumber
        } else {
            val packagePath = File(relativePath).parent ?: ""
            abs(packagePath.hashCode()) % bucketGroup.numOfBuckets == bucketNumber
        }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}