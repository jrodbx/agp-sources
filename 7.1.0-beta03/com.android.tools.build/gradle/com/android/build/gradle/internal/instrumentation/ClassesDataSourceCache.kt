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

import com.google.common.io.ByteStreams
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import java.io.Closeable
import java.io.InputStream
import java.util.Collections

/**
 * Base class for loading and caching [ClassData] from a source.
 *
 * @param sourceType indicates whether this source is a local project's source or a dependency
 *                   source.
 */
abstract class ClassesDataSourceCache(val sourceType: SourceType) : Closeable {
    private val asmApiVersion = org.objectweb.asm.Opcodes.ASM7
    private val loadedClassesData: MutableMap<String, ClassData> =
        Collections.synchronizedMap(mutableMapOf())

    private fun getClassData(classInputStream: InputStream): ClassData {
        var superclassName: String? = null
        val annotationsList = mutableListOf<String>()
        val interfacesList = mutableListOf<String>()
        classInputStream.use { inputStream ->
            val classReader = ClassReader(ByteStreams.toByteArray(inputStream))
            classReader.accept(object : ClassVisitor(asmApiVersion) {

                override fun visitAnnotation(
                    descriptor: String?,
                    visible: Boolean
                ): AnnotationVisitor? {
                    if (descriptor != "Lkotlin/Metadata;") {
                        annotationsList.add(descriptor!!.substring(1, descriptor.length - 1))
                    }
                    return null
                }

                override fun visit(
                    version: Int,
                    access: Int,
                    name: String?,
                    signature: String?,
                    superName: String?,
                    interfaces: Array<out String>?
                ) {
                    superclassName = superName
                    interfacesList.addAll(interfaces!!)
                }
            }, ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES or ClassReader.SKIP_DEBUG)
        }
        return ClassData(annotationsList, superclassName, interfacesList)
    }

    protected fun loadClassData(className: String, classInputStream: InputStream): ClassData {
        val classData = getClassData(classInputStream)
        loadedClassesData[className] = classData
        return classData
    }

    override fun close() {
        loadedClassesData.clear()
    }

    fun getClassDataIfLoaded(className: String): ClassData? {
        return loadedClassesData[className]
    }

    fun isClassLoaded(className: String): Boolean {
        return loadedClassesData.containsKey(className.replace('.', '/'))
    }

    abstract fun maybeLoadClassData(className: String): ClassData?

    data class ClassData(
        val annotations: List<String>,
        val superClass: String?,
        val interfaces: List<String>
    )

    enum class SourceType {
        PROJECT,
        DEPENDENCY,
    }
}
