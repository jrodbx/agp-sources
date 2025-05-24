/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.instrumentation

import com.android.SdkConstants
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.jar.JarFile

/**
 * Handles storing [ClassesDataSourceCache] objects and sharing them between different workers that
 * queries the same sources.
 */
class ClassesDataCache: Closeable {

    private val sourcesCacheMap = mutableMapOf<Any, ClassesDataSourceCache>()

    private fun getSourceFileKey(file: File): Any =
        Files.readAttributes(file.toPath(), BasicFileAttributes::class.java).fileKey()
            ?: file.canonicalPath

    fun getSourceCaches(
        sources: Map<File, ClassesDataSourceCache.SourceType>
    ): List<ClassesDataSourceCache> {
        val requested = sources.filter { it.key.exists() }
        synchronized(this) {
            return requested.map { (sourceFile, sourceType) ->
                val key = getSourceFileKey(sourceFile)
                sourcesCacheMap.computeIfAbsent(key) {
                    if (sourceFile.name.endsWith(SdkConstants.DOT_JAR)) {
                        JarCache(sourceFile, sourceType)
                    } else {
                        DirCache(sourceFile, sourceType)
                    }
                }
            }
        }
    }

    override fun close() {
        sourcesCacheMap.values.forEach(ClassesDataSourceCache::close)
        sourcesCacheMap.clear()
    }

    private class JarCache(
        file: File,
        sourceType: SourceType
    ) : ClassesDataSourceCache(sourceType) {
        private val jarFile = JarFile(file)

        override fun close() {
            super.close()
            jarFile.close()
        }

        @Synchronized
        override fun maybeLoadClassData(className: String): ClassData? {
            val classFileName = className + SdkConstants.DOT_CLASS

            jarFile.getEntry(classFileName)?.let { entry ->
                return loadClassData(className, jarFile.getInputStream(entry).buffered())
            }

            return null
        }
    }

    private class DirCache(
        private val dir: File,
        sourceType: SourceType
    ) : ClassesDataSourceCache(sourceType) {

        @Synchronized
        override fun maybeLoadClassData(className: String): ClassData? {
            val classFileName = className + SdkConstants.DOT_CLASS

            val classFile = dir.resolve(classFileName)
            if (classFile.exists()) {
                return loadClassData(className, classFile.inputStream().buffered())
            }

            return null
        }
    }
}
