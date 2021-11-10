/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants.DOT_CLASS
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.instrumentation.ClassesHierarchyResolver
import com.android.build.gradle.internal.instrumentation.FixFramesClassWriter
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.services.ClassesHierarchyBuildService
import com.android.builder.utils.isValidZipEntryName
import com.android.utils.FileUtils
import com.google.common.io.ByteStreams
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.workers.WorkerExecutor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.InvalidPathException
import java.nio.file.attribute.FileTime
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * When running Desugar, unit tests on instrumented classes or packaging instrumented classes, we
 * need to make sure stack frames information is valid in the class files.
 * This is due to fact that classes may be loaded in the JVM, and if stack frame information is
 * invalid for bytecode 1.7 and above, [VerifyError] is thrown. Also, if stack frames are
 * broken, ASM might be unable to read those classes.
 *
 * This delegate uses ASM to recalculate the stack frames information. In order to obtain new stack
 * frames, types need to be resolved, which is done using [ClassesHierarchyResolver].
 */
class FixStackFramesDelegate(
    val classesDir: File,
    val jarsDir: File,
    val bootClasspath: Set<File>,
    val referencedClasses: Set<File>,
    val classesOutDir: File,
    val jarsOutDir: File,
    val workers: WorkerExecutor,
    val task: AndroidVariantTask,
    val classesHierarchyBuildServiceProvider: Provider<ClassesHierarchyBuildService>
) {
    companion object {
        private val logger = LoggerWrapper.getLogger(FixStackFramesDelegate::class.java)
        private val zeroFileTime: FileTime = FileTime.fromMillis(0)

        fun transformJar(
            inputJar: File,
            outputJar: File,
            classesHierarchyResolver: ClassesHierarchyResolver
        ) {
            ZipFile(inputJar).use { inputZip ->
                ZipOutputStream(outputJar.outputStream().buffered()).use { outputZip ->
                    val inEntries = inputZip.entries()
                    while (inEntries.hasMoreElements()) {
                        val entry = inEntries.nextElement()
                        if (!isValidZipEntryName(entry)) {
                            throw InvalidPathException(
                                entry.name,
                                "Entry name contains invalid characters"
                            )
                        }
                        val outEntry = ZipEntry(entry.name)
                        val newEntryContent = inputZip.getInputStream(entry).buffered().use {
                            if (entry.name.endsWith(DOT_CLASS)) {
                                getFixedClass(it, classesHierarchyResolver)
                            } else {
                                ByteStreams.toByteArray(it)
                            }
                        }

                        val crc32 = CRC32()
                        crc32.update(newEntryContent)
                        outEntry.crc = crc32.value
                        outEntry.method = ZipEntry.STORED
                        outEntry.size = newEntryContent.size.toLong()
                        outEntry.compressedSize = newEntryContent.size.toLong()
                        outEntry.lastAccessTime = zeroFileTime
                        outEntry.lastModifiedTime = zeroFileTime
                        outEntry.creationTime = zeroFileTime

                        outputZip.putNextEntry(outEntry)
                        outputZip.write(newEntryContent)
                        outputZip.closeEntry()
                    }
                }
            }
        }

        private fun getFixedClass(
            originalFile: InputStream,
            classesHierarchyResolver: ClassesHierarchyResolver
        ): ByteArray {
            val bytes = ByteStreams.toByteArray(originalFile)
            return try {
                val classReader = ClassReader(bytes)
                val classWriter =
                    FixFramesClassWriter(ClassWriter.COMPUTE_FRAMES, classesHierarchyResolver)
                classReader.accept(classWriter, ClassReader.SKIP_FRAMES)
                classWriter.toByteArray()
            } catch (t: Throwable) {
                // we could not fix it, just copy the original and log the exception
                logger.verbose(t.message!!)
                bytes
            }
        }
    }

    private fun processJars(
        changedInput: Map<File, ChangeType>
    ) {
        changedInput.forEach { (inputJar, changeType) ->
            val outputJar = File(jarsOutDir, inputJar.name)

            FileUtils.deleteIfExists(outputJar)

            if (changeType == ChangeType.ADDED || changeType == ChangeType.MODIFIED) {
                workers.noIsolation().submit(FixJarStackFramesRunnable::class.java) { params ->
                    params.initializeFromAndroidVariantTask(task)
                    params.inputJar.set(inputJar)
                    params.outputJar.set(outputJar)
                    params.classesHierarchyBuildService.set(
                        classesHierarchyBuildServiceProvider
                    )
                    params.classpath.set(
                        listOf(
                            bootClasspath,
                            jarsDir.listFiles()!!.toSet(),
                            referencedClasses
                        ).flatten()
                    )
                }
            }
        }
    }

    private fun processClasses(
        changedInput: Map<File, ChangeType>
    ) {
        changedInput.filterValues { it == ChangeType.REMOVED }.forEach { (inputFile, _) ->
            FileUtils.deleteIfExists(classesOutDir.resolve(inputFile.relativeTo(classesDir)))
        }

        val filesToProcess = changedInput.filterValues {
            it == ChangeType.ADDED || it == ChangeType.MODIFIED
        }.keys

        if (filesToProcess.isNotEmpty()) {
            workers.noIsolation()
                .submit(FixClassesStackFramesRunnable::class.java) { params ->
                    params.initializeFromAndroidVariantTask(task)
                    params.inputDir.set(classesDir)
                    params.inputFiles.set(filesToProcess)
                    params.outputDir.set(classesOutDir)
                    params.classesHierarchyBuildService.set(
                        classesHierarchyBuildServiceProvider
                    )
                    params.classpath.set(
                        listOf(
                            bootClasspath,
                            listOf(classesDir),
                            referencedClasses
                        ).flatten()
                    )
                }
        }
    }

    fun doFullRun() {
        FileUtils.cleanOutputDir(classesOutDir)
        FileUtils.cleanOutputDir(jarsOutDir)

        processJars(jarsDir.listFiles()!!.map { it to ChangeType.ADDED }.toMap())
        processClasses(FileUtils.getAllFiles(classesDir).toMap { ChangeType.ADDED })
    }

    fun doIncrementalRun(
        jarChanges: Iterable<FileChange>,
        classesChanges: Iterable<FileChange>
    ) {
        processJars(jarChanges.associate { it.file to it.changeType })
        processClasses(classesChanges.associate { it.file to it.changeType })
    }

    abstract class JarParams : ProfileAwareWorkAction.Parameters() {
        abstract val inputJar: Property<File>

        abstract val outputJar: Property<File>

        abstract val classesHierarchyBuildService: Property<ClassesHierarchyBuildService>

        abstract val classpath: ListProperty<File>
    }

    abstract class ClassesParams : ProfileAwareWorkAction.Parameters() {
        abstract val inputFiles: ListProperty<File>

        abstract val inputDir: Property<File>

        abstract val outputDir: Property<File>

        abstract val classesHierarchyBuildService: Property<ClassesHierarchyBuildService>

        abstract val classpath: ListProperty<File>
    }

    abstract class FixJarStackFramesRunnable : ProfileAwareWorkAction<JarParams>() {

        override fun run() {
            transformJar(
                parameters.inputJar.get(),
                parameters.outputJar.get(),
                parameters.classesHierarchyBuildService.get()
                    .getClassesHierarchyResolverBuilder()
                    .addDependenciesSources(parameters.classpath.get())
                    .build()
            )
        }
    }

    abstract class FixClassesStackFramesRunnable : ProfileAwareWorkAction<ClassesParams>() {

        override fun run() {
            val classesHierarchyResolver =
                parameters.classesHierarchyBuildService.get()
                    .getClassesHierarchyResolverBuilder()
                    .addDependenciesSources(parameters.classpath.get())
                    .build()

            parameters.inputFiles.get().forEach { inputFile ->
                val outputFile =
                    parameters.outputDir.get()
                        .resolve(inputFile.relativeTo(parameters.inputDir.get()))

                FileUtils.deleteIfExists(outputFile)
                outputFile.parentFile.mkdirs()

                if (inputFile.name.endsWith(DOT_CLASS)) {
                    FileOutputStream(outputFile).buffered().use { outputStream ->
                        inputFile.inputStream().buffered().use { inputStream ->
                            outputStream.write(getFixedClass(inputStream, classesHierarchyResolver))
                        }
                    }
                } else {
                    FileUtils.copyFile(inputFile, outputFile)
                }
            }
        }
    }
}
