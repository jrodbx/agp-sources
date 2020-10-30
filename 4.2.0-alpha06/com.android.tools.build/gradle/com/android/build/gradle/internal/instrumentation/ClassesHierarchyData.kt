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
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.google.common.io.ByteStreams
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassReader.SKIP_CODE
import org.objectweb.asm.ClassReader.SKIP_DEBUG
import org.objectweb.asm.ClassReader.SKIP_FRAMES
import org.objectweb.asm.ClassVisitor
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Contains data about the hierarchy of classes which includes, the superclass and the interfaces of
 * each class.
 *
 * Each class is represented via its internal name.
 */
class ClassesHierarchyData(private val asmApiVersion: Int) {
    private val classesData: MutableMap<String, ClassData> = mutableMapOf()

    fun addClassesFromDir(dir: File) {
        FileUtils.getAllFiles(dir).filter { it!!.name.endsWith(DOT_CLASS) }.forEach { classFile ->
            addClass(classFile.inputStream().buffered())
        }
    }

    fun addClassesFromJar(jarFile: File) {
        ZipFile(jarFile).use { inputJar ->
            val entries = inputJar.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith(DOT_CLASS)) {
                    addClass(inputJar.getInputStream(entry))
                }
            }
        }
    }

    private fun addClass(classInputStream: InputStream) {
        classInputStream.use { inputStream ->
            val classReader = ClassReader(ByteStreams.toByteArray(inputStream))
            classReader.accept(object : ClassVisitor(asmApiVersion) {
                override fun visit(
                    version: Int,
                    access: Int,
                    name: String?,
                    signature: String?,
                    superName: String?,
                    interfaces: Array<out String>?
                ) {
                    addClass(name!!, superName, interfaces?.toList() ?: emptyList())
                }
            }, SKIP_CODE or SKIP_FRAMES or SKIP_DEBUG)
        }
    }

    @VisibleForTesting
    fun addClass(className: String, superClass: String?, interfaces: List<String>) {
        classesData[className] = ClassData(superClass, interfaces)
    }

    /**
     * Returns a list of the superclasses of a certain class in the order of the class hierarchy.
     *
     * Example:
     *
     * A extends B
     * B extends C
     *
     * when invoking getAllSuperClasses(A)
     *
     * the method will return {B, C, java/lang/Object}
     */
    fun getAllSuperClasses(className: String): List<String> {
        return doGetAllSuperClasses(className).reversed()
    }

    /**
     * Returns a list of the interfaces of a certain class sorted by name.
     *
     * Example:
     *
     * A implements B
     * B implements C
     *
     * when invoking getAllInterfaces(A)
     *
     * the method will return {B, C}
     */
    fun getAllInterfaces(className: String): List<String> {
        return doGetAllInterfaces(className).sorted()
    }

    private fun doGetAllSuperClasses(className: String): MutableList<String> {
        val classData = classesData[className]!!
        if (classData.superClass == null) {
            return mutableListOf()
        }
        return doGetAllSuperClasses(classData.superClass).apply { add(classData.superClass) }
    }

    private fun doGetAllInterfaces(className: String): MutableSet<String> {
        val classData = classesData[className]!!
        return mutableSetOf<String>().apply {
            if (classData.superClass != null) {
                addAll(doGetAllInterfaces(classData.superClass))
            }
            addAll(classData.interfaces)
            classData.interfaces.forEach {
                addAll(getAllInterfaces(it))
            }
        }
    }

    private data class ClassData(val superClass: String?, val interfaces: List<String>)
}