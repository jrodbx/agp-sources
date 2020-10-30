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

import com.android.SdkConstants
import com.android.SdkConstants.DOT_JAR
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.instrumentation.ClassesHierarchyResolver
import com.android.build.gradle.internal.instrumentation.FixFramesClassWriter
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.services.ClassesHierarchyBuildService
import com.android.builder.utils.isValidZipEntryName
import com.android.utils.FileUtils
import com.google.common.hash.Hashing
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
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.attribute.FileTime
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * When running Desugar, we need to make sure stack frames information is valid in the class files.
 * This is due to fact that Desugar may load classes in the JVM, and if stack frame information is
 * invalid for bytecode 1.7 and above, [VerifyError] is thrown. Also, if stack frames are
 * broken, ASM might be unable to read those classes.
 *
 * This delegate will load all class files from all external jars, and will use ASM to
 * recalculate the stack frames information. In order to obtain new stack frames, types need to be
 * resolved.
 *
 * The parent task requires external libraries as inputs, and all other scope types are
 * referenced. Reason is that loading a class from an external jar, might depend on loading a class
 * that could be located in any of the referenced scopes. In case we are unable to resolve types,
 * content of the original class file will be copied to the the output as we do not know upfront if
 * Desugar will actually load that type.
 */
class FixStackFramesDelegate(
    val bootClasspath: Set<File>,
    val classesToFix: Set<File>,
    val referencedClasses: Set<File>,
    val outFolder: File
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
                ZipOutputStream(
                    Files.newOutputStream(outputJar.toPath()).buffered()
                ).use { outputZip ->
                    val inEntries = inputZip.entries()
                    while (inEntries.hasMoreElements()) {
                        val entry = inEntries.nextElement()
                        if (!isValidZipEntryName(entry)) {
                            throw InvalidPathException(
                                entry.name,
                                "Entry name contains invalid characters"
                            )
                        }
                        if (!entry.name.endsWith(SdkConstants.DOT_CLASS)) {
                            continue
                        }
                        val originalFile = inputZip.getInputStream(entry).buffered()
                        val outEntry = ZipEntry(entry.name)

                        val newEntryContent = getFixedClass(originalFile, classesHierarchyResolver)

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

    private fun getUniqueName(input: File): String {
        return Hashing.sha256().hashString(input.absolutePath, StandardCharsets.UTF_8)
            .toString() + DOT_JAR
    }

    private fun processFiles(
        workers: WorkerExecutor,
        changedInput: Map<File, ChangeType>,
        task: AndroidVariantTask,
        classesHierarchyBuildServiceProvider: Provider<ClassesHierarchyBuildService>
    ) {
        changedInput.entries.forEach { entry ->
            val out = File(outFolder, getUniqueName(entry.key))

            Files.deleteIfExists(out.toPath())

            if (entry.value == ChangeType.ADDED || entry.value == ChangeType.MODIFIED) {
                workers.noIsolation()
                    .submit(FixStackFramesRunnable::class.java) { params ->
                        params.initializeFromAndroidVariantTask(task)
                        params.input.set(entry.key)
                        params.output.set(out)
                        params.classesHierarchyBuildService.set(
                            classesHierarchyBuildServiceProvider
                        )
                        params.classpath.set(
                            listOf(
                                bootClasspath,
                                classesToFix,
                                referencedClasses
                            ).flatten()
                        )
                    }
            }
        }
        // We keep waiting for all the workers to finnish so that all the work is done before
        // we remove services in Manager.close()
        workers.await()
    }

    fun doFullRun(
        workers: WorkerExecutor,
        task: AndroidVariantTask,
        classesHierarchyBuildServiceProvider: Provider<ClassesHierarchyBuildService>
    ) {
        FileUtils.cleanOutputDir(outFolder)

        val inputToProcess = classesToFix.map { it to ChangeType.ADDED }.toMap()

        processFiles(workers, inputToProcess, task, classesHierarchyBuildServiceProvider)
    }

    fun doIncrementalRun(
        workers: WorkerExecutor,
        inputChanges: Iterable<FileChange>,
        task: AndroidVariantTask,
        classesHierarchyBuildServiceProvider: Provider<ClassesHierarchyBuildService>
    ) {
        // We should only process (unzip and fix stack) existing jar input from classesToFix
        // If changedInput contains a folder or deleted jar we will still try to delete
        // corresponding output entry (if exists) but will do no processing
        val jarsToProcess = classesToFix.filter(File::isFile).toSet()

        val inputToProcess = inputChanges.filter {
            it.changeType == ChangeType.REMOVED || jarsToProcess.contains(it.file)
        }.associate { it.file to it.changeType }

        processFiles(workers, inputToProcess, task, classesHierarchyBuildServiceProvider)
    }

    abstract class Params : ProfileAwareWorkAction.Parameters() {
        abstract val input: Property<File>

        abstract val output: Property<File>

        abstract val classesHierarchyBuildService: Property<ClassesHierarchyBuildService>

        abstract val classpath: ListProperty<File>
    }

    abstract class FixStackFramesRunnable : ProfileAwareWorkAction<Params>() {

        override fun run() {
            transformJar(
                parameters.input.get(),
                parameters.output.get(),
                parameters.classesHierarchyBuildService.get()
                    .getClassesHierarchyResolverBuilder()
                    .addSources(parameters.classpath.get())
                    .build()
            )
        }
    }
}
