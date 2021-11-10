/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.tasks.JacocoTask
import com.android.build.gradle.tasks.toSerializable
import com.android.builder.files.RelativeFile
import com.android.builder.files.RelativeFiles
import com.android.builder.files.SerializableChange
import com.android.builder.files.SerializableFileChanges
import com.android.ide.common.resources.FileStatus
import com.android.utils.FileUtils
import com.google.common.io.ByteStreams
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.jetbrains.kotlin.gradle.internal.ensureParentDirsCreated
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.UncheckedIOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject

@CacheableTransform
abstract class JacocoTransform : TransformAction<JacocoTransform.Params> {

    abstract class Params : GenericTransformParameters {

        @get:Internal
        abstract val jacocoVersion: Property<String>

        @get:Classpath
        abstract val jacocoConfiguration: ConfigurableFileCollection

        @get:Internal
        abstract val jacocoInstrumentationService: Property<JacocoInstrumentationService>
    }

    @get:Inject
    abstract val inputChanges: InputChanges

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Incremental
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(transformOutputs: TransformOutputs) {
        val logger = LoggerWrapper.getLogger(this::class.java)
        transformNonIncrementally(inputArtifact.get().asFile, transformOutputs, logger)
    }

    private fun isJarFile(candidateFile: File) =
        candidateFile.isFile && candidateFile.extension == SdkConstants.EXT_JAR

    private fun transformNonIncrementally(
        file: File,
        transformOutputs: TransformOutputs,
        logger: LoggerWrapper
    ) {
        when {
            file.isDirectory -> {
                val classOutDir = transformOutputs.dir("instrumented_classes")
                classOutDir.mkdirs()
                logger.info("Instrumenting file: ${file.absolutePath}")
                instrumentDir(file, classOutDir)
            }
            isJarFile(file) -> {
                val outputFile = transformOutputs.file(
                    "instrumented_${file.name}"
                )
                logger.info("Instrumenting jar: ${file.absolutePath}")
                instrumentJar(file, outputFile)
            }
            else -> {
                throw IOException(
                    """$file is not supported as a JacocoTransform input. "
                            "Input artifacts must be single a directory or file."""
                )
            }
        }
    }

    private fun instrumentDir(inDirs: File, outputDir: File) {
        RelativeFiles.fromDirectory(inDirs)
            .filter { relativeFile ->  relativeFile.file.extension == SdkConstants.EXT_CLASS }
            .forEach { classFile -> instrumentClassFile(classFile, outputDir) }
    }

    private fun instrumentJar(inputJar: File, outputJar: File) {
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputJar))).use {
                    instrumentedJar ->
                ZipFile(inputJar).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val entryName = entry.name
                        val data = when (getInstrumentationAction(entryName)) {
                            JacocoTask.Action.IGNORE -> continue
                            JacocoTask.Action.COPY -> {
                                ByteStreams.toByteArray(zip.getInputStream(entry))
                            }
                            JacocoTask.Action.INSTRUMENT -> {
                                parameters.jacocoInstrumentationService.get()
                                    .instrument(
                                        zip.getInputStream(entry),
                                        entryName,
                                        parameters.jacocoConfiguration,
                                        parameters.jacocoVersion.get()
                                    )
                            }
                        }
                        val nextEntry = ZipEntry(entryName)
                        // Any negative time value sets ZipEntry's xdostime to DOSTIME_BEFORE_1980
                        // constant.
                        nextEntry.time = - 1L
                        instrumentedJar.putNextEntry(nextEntry)
                        instrumentedJar.write(data)
                        instrumentedJar.closeEntry()
                    }
                }
            }
        } catch (e: IOException) {
            throw UncheckedIOException("Unable to instrument file with Jacoco: $inputJar", e)
        }
    }

    private fun instrumentClassFile(classFile: RelativeFile, outputDir: File) {
        val outputFile = outputDir.resolve(classFile.relativePath)
        outputFile.ensureParentDirsCreated()
        when (getInstrumentationAction(classFile)) {
            JacocoTask.Action.IGNORE -> return
            JacocoTask.Action.COPY -> FileUtils.copyFile(classFile.file, outputFile)
            JacocoTask.Action.INSTRUMENT -> {
                outputFile.writeBytes(classFile.file.inputStream().use {
                    parameters.jacocoInstrumentationService.get()
                        .instrument(
                            it,
                            classFile.relativePath,
                            parameters.jacocoConfiguration,
                            parameters.jacocoVersion.get()
                        )
                }
                )
            }
        }
    }

    fun getInstrumentationAction(relativeFile: RelativeFile): JacocoTask.Action {
        return getInstrumentationAction(relativeFile.relativePath)
    }

    fun getInstrumentationAction(relativePath: String): JacocoTask.Action {
        return JacocoTask.calculateAction(relativePath)
    }
}
