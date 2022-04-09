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

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.internal.tasks.MergeNativeLibsTask
import com.android.utils.FileUtils
import com.google.common.io.ByteStreams
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.TransformAction

import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.regex.Pattern
import java.util.zip.ZipFile

/**
 * A [TransformAction] that extracts native libraries from a jar.
 */
@CacheableTransform
abstract class ExtractJniTransform : TransformAction<GenericTransformParameters> {

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputArtifact
    abstract val inputJar: Provider<FileSystemLocation>

    override fun transform(transformOutputs: TransformOutputs) {
        doTransform(inputJar.get().asFile, transformOutputs)
    }

    private fun doTransform(inputJar: File, transformOutputs: TransformOutputs) {
        ZipFile(inputJar).use {
            val entries =
                it.stream()
                    .filter { entry ->
                        MergeNativeLibsTask.predicate.test(entry.name.substringAfterLast('/'))
                                && JAR_JNI_PATTERN.matcher(entry.name).matches()
                    }.iterator()
            if (!entries.hasNext()) {
                return
            }
            val outputDir = transformOutputs.dir(inputJar.nameWithoutExtension)
            FileUtils.mkdirs(outputDir)
            while (entries.hasNext()) {
                val entry = entries.next()
                // omit the "lib/" entry.name prefix in the output path
                val relativePath =
                    entry.name.substringAfter('/').replace('/', File.separatorChar)
                val outFile = FileUtils.join(outputDir, relativePath)
                FileUtils.mkdirs(outFile.parentFile)
                BufferedInputStream(it.getInputStream(entry)).use { inFileStream ->
                    BufferedOutputStream(outFile.outputStream()).use { outFileStream ->
                        ByteStreams.copy(inFileStream, outFileStream)
                    }
                }
            }
        }
    }
}

val JAR_JNI_PATTERN: Pattern = Pattern.compile("lib/([^/]+)/[^/]+")
