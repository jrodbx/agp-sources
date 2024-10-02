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

import java.io.File
import java.io.Serializable

/** A group of class files, which is split into a number of [ClassBucket]'s. */
sealed class ClassBucketGroup(val numOfBuckets: Int) : Serializable {

    /** Returns the roots of the class files, which could be directories or jars. */
    abstract fun getRoots(): List<File>

    companion object {
        private const val serialVersionUID = 1L
    }
}

/** A group of all class files in some root directories. */
class DirectoryBucketGroup(

    /** The root directories of the class files. */
    private val rootDirs: List<File>,

    /** The number of buckets that this group is split into. */
    numOfBuckets: Int

) : ClassBucketGroup(numOfBuckets), Serializable {

    override fun getRoots() = rootDirs

    companion object {
        private const val serialVersionUID = 1L
    }
}

/** A group of all class files in a jar. */
class JarBucketGroup(

    /** The jar file. It may have been removed. */
    val jarFile: File,

    /** The number of buckets that this group is split into. */
    numOfBuckets: Int

) : ClassBucketGroup(numOfBuckets), Serializable {

    init {
        check(isJarFile(jarFile))
    }

    override fun getRoots() = listOf(jarFile)

    companion object {
        private const val serialVersionUID = 1L
    }
}