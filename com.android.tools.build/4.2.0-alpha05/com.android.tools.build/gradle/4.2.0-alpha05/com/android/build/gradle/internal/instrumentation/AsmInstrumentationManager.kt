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

import com.android.SdkConstants.DOT_CLASS
import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.utils.FileUtils
import com.google.common.io.ByteStreams
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Instruments the given classes with the given list of [visitors]. Frames are skipped and not
 * regenerated after visiting each class and should be regenerated separately if needed.
 *
 * @param visitors the list of registered [AsmClassVisitorFactoryEntry].
 * @param apiVersion the asm api version
 * @param classesHierarchyData used to derive information about classes hierarchy without having to
 *                             load the actual classes.
 */
class AsmInstrumentationManager(
    private val visitors: List<AsmClassVisitorFactory<*>>,
    private val apiVersion: Int,
    private val classesHierarchyData: ClassesHierarchyData
) {
    fun instrumentClassesFromDirectoryToDirectory(inputDir: File, outputDir: File) {
        val inputPath = inputDir.toPath()
        Files.walkFileTree(inputPath, object : SimpleFileVisitor<Path>() {

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val relativePath = inputPath.relativize(file)
                val fileName = relativePath.fileName.toString()
                val outputFile = outputDir.resolve(relativePath.toString())
                outputFile.parentFile.mkdirs()

                if (fileName.endsWith(DOT_CLASS)) {
                    instrumentClassToDir(
                        packageName = relativePath.toString()
                            .removeSuffix(File.separatorChar + fileName)
                            .replace(File.separatorChar, '.'),
                        className = fileName.removeSuffix(DOT_CLASS),
                        classFile = file.toFile(),
                        outputFile = outputFile
                    )
                } else {
                    FileUtils.copyFile(file.toFile(), outputFile)
                }
                return FileVisitResult.CONTINUE
            }
        })
    }

    fun instrumentClassesFromJarToJar(inputJarFile: File, outputJarFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputJarFile))).use { outputJar ->
            outputJar.setLevel(Deflater.NO_COMPRESSION)
            ZipFile(inputJarFile).use { inputJar ->
                val entries = inputJar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    instrumentClassToJar(entry, outputJar) {
                        inputJar.getInputStream(entry)
                    }
                }
            }
        }
    }

    fun instrumentModifiedClass(inputFile: File, outputFile: File, packageName: String) {
        outputFile.parentFile.mkdirs()

        instrumentClassToDir(
            packageName = packageName,
            className = inputFile.name.removeSuffix(DOT_CLASS),
            classFile = inputFile,
            outputFile = outputFile
        )
    }

    private fun doInstrumentClass(
        packageName: String,
        className: String,
        classInputStream: () -> InputStream
    ): ByteArray? {
        val classFullName = "$packageName.$className"
        val classInternalName = classFullName.replace('.', '/')

        val classData = ClassDataImpl(
            classFullName,
            classesHierarchyData.getAllInterfaces(classInternalName)
                .map { it.replace('/', '.') },
            classesHierarchyData.getAllSuperClasses(classInternalName)
                .map { it.replace('/', '.') }
        )

        // Reversing the visitors as they will be chained from the end, and so the visiting
        // order will be the same as the registration order
        val filteredVisitors = visitors.filter { entry ->
            entry.isInstrumentable(classData)
        }.reversed()

        return if (filteredVisitors.isNotEmpty()) {
            classInputStream.invoke().use {
                val bytes = ByteStreams.toByteArray(it)
                val classReader = ClassReader(bytes)
                val classWriter = ClassWriter(0)
                var nextVisitor: ClassVisitor = classWriter

                filteredVisitors.forEach { entry ->
                    nextVisitor = entry.createClassVisitor(classData, nextVisitor)
                }

                classReader.accept(nextVisitor, ClassReader.SKIP_FRAMES)
                classWriter.toByteArray()
            }
        } else {
            null
        }
    }

    private fun saveEntryToJar(
        entryName: String,
        byteArray: ByteArray,
        jarOutputStream: ZipOutputStream
    ) {
        val entry = ZipEntry(entryName)
        entry.time = 0
        jarOutputStream.putNextEntry(entry)
        jarOutputStream.write(byteArray)
        jarOutputStream.closeEntry()
    }

    private fun instrumentClassToJar(
        entry: ZipEntry,
        jarOutputStream: ZipOutputStream,
        classInputStream: () -> InputStream
    ) {
        val entryName = entry.name
        if (!entryName.endsWith(DOT_CLASS)) {
            classInputStream.invoke().use {
                saveEntryToJar(
                    entryName,
                    ByteStreams.toByteArray(it),
                    jarOutputStream
                )
            }
            return
        }

        val splitName = entryName.split("/")
        val packageName = splitName.subList(0, splitName.size - 1).joinToString(".")
        val className = splitName.last().removeSuffix(DOT_CLASS)

        var instrumentedByteArray = doInstrumentClass(
            packageName = packageName,
            className = className,
            classInputStream = classInputStream
        )
        if (instrumentedByteArray == null) {
            classInputStream.invoke().use {
                instrumentedByteArray = ByteStreams.toByteArray(it)
            }
        }
        saveEntryToJar(entryName, instrumentedByteArray!!, jarOutputStream)
    }

    private fun instrumentClassToDir(
        packageName: String,
        className: String,
        classFile: File,
        outputFile: File
    ) {
        val instrumentedByteArray = doInstrumentClass(packageName, className) {
            classFile.inputStream().buffered()
        }
        if (instrumentedByteArray != null) {
            FileOutputStream(outputFile).use { outputStream ->
                outputStream.write(instrumentedByteArray)
            }
        } else {
            FileUtils.copyFile(classFile, outputFile)
        }
    }
}