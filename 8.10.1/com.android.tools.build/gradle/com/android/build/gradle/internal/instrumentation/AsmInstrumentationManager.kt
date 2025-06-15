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
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.gradle.internal.matcher.GlobPathMatcherFactory
import com.android.builder.dexing.ClassFileInput.CLASS_MATCHER
import com.android.utils.FileUtils
import com.google.common.io.ByteStreams
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLClassLoader
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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
 * @param apiVersion the asm api version.
 * @param classesHierarchyResolver used to derive information about classes hierarchy without having
 *                                 to load the actual classes.
 * @param framesComputationMode the frame computation mode that will be applied to the bytecode of
 *                              the instrumented classes.
 * @param excludes the set of patterns to exclude from instrumentation
 * @param profilingTransforms list of paths to the profiler jars injected by the IDE to transform
 *                            classes.
 */
class AsmInstrumentationManager(
    private val visitors: List<AsmClassVisitorFactory<*>>,
    private val apiVersion: Int,
    private val classesHierarchyResolver: ClassesHierarchyResolver,
    private val issueHandler: InstrumentationIssueHandler,
    private val framesComputationMode: FramesComputationMode,
    excludes: Set<String>,
    profilingTransforms: List<String> = emptyList()
): Closeable {
    private val profilingTransformsClassLoaders = mutableListOf<URLClassLoader>()
    private val excludesMatchers = excludes.map {
        GlobPathMatcherFactory.create(FileUtils.toSystemIndependentPath(it))
    }

    private val profilingTransforms = profilingTransforms.map {
        val jarFile = File(it)
        val classLoader = URLClassLoader(arrayOf(jarFile.toURI().toURL()))
        profilingTransformsClassLoaders.add(classLoader)
        loadTransformFunction(jarFile, classLoader)
    }

    private fun getClassWriterFlags(containsJsrOrRetInstruction: Boolean): Int =
        when (framesComputationMode) {
            FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS,
            FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_CLASSES -> {
                if (containsJsrOrRetInstruction) {
                    ClassWriter.COMPUTE_MAXS
                } else {
                    ClassWriter.COMPUTE_FRAMES
                }
            }
            else -> 0
        }

    private fun getClassReaderFlags(containsJsrOrRetInstruction: Boolean): Int {
        if (containsJsrOrRetInstruction) {
            return ClassReader.EXPAND_FRAMES
        }
        return when (framesComputationMode) {
            FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_CLASSES,
            FramesComputationMode.COMPUTE_FRAMES_FOR_ALL_CLASSES ->
                ClassReader.SKIP_FRAMES
            else -> ClassReader.EXPAND_FRAMES
        }
    }

    override fun close() {
        profilingTransformsClassLoaders.forEach(URLClassLoader::close)
    }

    private fun includeFileInInstrumentation(relativePath: String): Boolean {
        val pathWithoutExtension by lazy {
            Paths.get(FileUtils.toSystemIndependentPath(relativePath).removeSuffix(DOT_CLASS))
        }
        return CLASS_MATCHER.test(relativePath) && excludesMatchers.none {
            it.matches(pathWithoutExtension)
        }
    }

    fun instrumentClassesFromDirectoryToDirectory(inputDir: File, outputDir: File) {
        val inputPath = inputDir.toPath()
        Files.walkFileTree(inputPath, object : SimpleFileVisitor<Path>() {

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val relativePath = inputPath.relativize(file)
                val fileName = relativePath.fileName.toString()
                val outputFile = outputDir.resolve(relativePath.toString())
                outputFile.parentFile.mkdirs()

                if (includeFileInInstrumentation(relativePath.toString())) {
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

    fun instrumentModifiedFile(inputFile: File, outputFile: File, relativePath: String) {
        outputFile.parentFile.mkdirs()
        if (includeFileInInstrumentation(relativePath)) {
            instrumentClassToDir(
                packageName = relativePath.removeSuffix("/${inputFile.name}").replace('/', '.'),
                className = inputFile.name.removeSuffix(DOT_CLASS),
                classFile = inputFile,
                outputFile = outputFile
            )
        } else {
            FileUtils.copyFile(inputFile, outputFile)
        }
    }

    private fun performProfilingTransformations(inputStream: InputStream): ByteArray {
        var bytes = ByteStreams.toByteArray(inputStream)
        ByteArrayOutputStream().use { outputStream ->
            profilingTransforms.forEach { transform ->
                ByteArrayInputStream(bytes).use { inputStream ->
                    transform.accept(inputStream, outputStream)
                }
                bytes = outputStream.toByteArray()
                outputStream.reset()
            }
        }
        return bytes
    }

    private fun doInstrumentByteCode(
        classContext: ClassContext,
        byteCode: ByteArray,
        visitors: List<AsmClassVisitorFactory<*>>,
        containsJsrOrRetInstruction: Boolean
    ): ByteArray {
        val classReader = ClassReader(byteCode)
        val classWriter =
            FixFramesClassWriter(
                classReader,
                getClassWriterFlags(containsJsrOrRetInstruction),
                classesHierarchyResolver,
                issueHandler
            )
        var nextVisitor: ClassVisitor = classWriter

        if (framesComputationMode == FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_CLASSES) {
            nextVisitor = MaxsInvalidatingClassVisitor(apiVersion, classWriter)
        }

        val originalVisitor = nextVisitor

        visitors.forEach { entry ->
            nextVisitor = entry.createClassVisitor(classContext, nextVisitor)
        }

        // No external visitor will instrument this class
        if (nextVisitor == originalVisitor) {
            return byteCode
        }

        classReader.accept(nextVisitor, getClassReaderFlags(containsJsrOrRetInstruction))
        return classWriter.toByteArray()
    }

    private fun doInstrumentClass(
        packageName: String,
        className: String,
        classInputStream: () -> InputStream
    ): ByteArray? {
        val classFullName = "$packageName.$className"
        val classInternalName = classFullName.replace('.', '/')

        val classData = ClassDataLazyImpl(
            classFullName,
            { classesHierarchyResolver.getAnnotations(classInternalName) },
            { classesHierarchyResolver.getAllInterfaces(classInternalName) },
            { classesHierarchyResolver.getAllSuperClasses(classInternalName) }
        )

        // Reversing the visitors as they will be chained from the end, and so the visiting
        // order will be the same as the registration order
        val filteredVisitors = visitors.filter { entry ->
            entry.isInstrumentable(classData)
        }.reversed()

        return when {
            filteredVisitors.isNotEmpty() -> {
                classInputStream.invoke().use {
                    val classContext = ClassContextImpl(classData, classesHierarchyResolver)
                    val byteCode = performProfilingTransformations(it)
                    val javaVersion = getJavaMajorVersionOfCompiledClass(byteCode)
                    try {
                        return@use doInstrumentByteCode(
                            classContext,
                            byteCode,
                            filteredVisitors,
                            // Don't compute frames for bytecode compiled by a version older than
                            // java 6
                            containsJsrOrRetInstruction = javaVersion < Opcodes.V1_6
                        )
                    } catch (e: Exception) {
                        // A class file whose version number is 6 or above must be verified using
                        // the type checking rules which means frames will be verified and so need
                        // to be computed.
                        // If, and only if, a class file's version number equals 6, then if the
                        // type checking fails, a Java Virtual Machine implementation may choose to
                        // attempt to perform verification by type inference instead which can
                        // happen when JSR/RET instructions exist in the class file.
                        // We need to try first to compute the frames assuming the JVM will use type
                        // checking for verification, and if it fails redo the instrumentation as in
                        // that case the JVM will use type inference for verification.
                        if (e.message == "JSR/RET are not supported with computeFrames option" &&
                                javaVersion == Opcodes.V1_6) {
                            try {
                                return@use doInstrumentByteCode(
                                    classContext,
                                    byteCode,
                                    filteredVisitors,
                                    containsJsrOrRetInstruction = true
                                )
                            } catch (e: Exception) {
                                throw RuntimeException(
                                    "Error occurred while instrumenting class $classFullName",
                                    e
                                )
                            }
                        } else {
                            throw RuntimeException(
                                "Error occurred while instrumenting class $classFullName",
                                e
                            )
                        }
                    }
                }
            }
            profilingTransforms.isNotEmpty() -> {
                classInputStream.invoke().use {
                    performProfilingTransformations(it)
                }
            }
            else -> {
                null
            }
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
        if (!includeFileInInstrumentation(entryName)) {
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

    /**
     * A class visitor that visits all methods with [MethodVisitorDelegator].
     *
     * Because the flag [ClassReader.SKIP_FRAMES] doesn't skip the maxs, [ClassWriter] will
     * recalculate all frames from scratch, but will only recalculate the maxs of the visited
     * methods. This class visitor visits all methods in order to force [ClassWriter] to calculate
     * the maxs for all methods when the frame computation mode is
     * [FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_CLASSES].
     */
    private class MaxsInvalidatingClassVisitor(apiVersion: Int, classVisitor: ClassVisitor) :
        ClassVisitor(apiVersion, classVisitor) {

        override fun visitMethod(
            access: Int,
            name: String?,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor {
            val delegate = super.visitMethod(access, name, descriptor, signature, exceptions)
            return MethodVisitorDelegator(api, delegate)
        }
    }

    private class MethodVisitorDelegator(apiVersion: Int, methodVisitor: MethodVisitor) :
        MethodVisitor(apiVersion, methodVisitor)
}
