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

import com.android.build.api.instrumentation.ClassData
import java.io.File

/**
 * Resolves class hierarchy using shared [ClassesDataSourceCache] objects.
 *
 * Each class is represented via its internal name.
 */
class ClassesHierarchyResolver(classesDataCache: ClassesDataCache, sources: Set<File>) {

    private val classesDataCaches = classesDataCache.getSourceCaches(sources)

    private fun maybeLoadClassData(className: String): ClassesDataSourceCache.ClassData? {
        // Check if it's already cached
        classesDataCaches.forEach {
            it.getClassDataIfLoaded(className)?.let { return it }
        }

        // Class is not cached, try to load it
        classesDataCaches.forEach {
            it.maybeLoadClassData(className)?.let { return it }
        }

        return null
    }

    fun maybeLoadClassDataForClass(className: String): ClassData? {
        val classInternalName = className.replace('.', '/')
        val superclasses = getAllSuperClasses(classInternalName)
        if (superclasses.isEmpty() && className != Object::class.java.name) {
            return null
        }
        return ClassDataImpl(
                className = className,
                classAnnotations = getAnnotations(classInternalName),
                interfaces = getAllInterfaces(classInternalName),
                superClasses = superclasses
        )
    }

    fun getAnnotations(className: String): List<String> {
        return maybeLoadClassData(className)?.annotations?.map { it.replace('/', '.') }
            ?: emptyList()
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
     * the method will return {B, C, java.lang.Object}
     */
    fun getAllSuperClasses(className: String): List<String> {
        return getAllSuperClassesInInternalForm(className).map { it.replace('/', '.') }
    }

    fun getAllSuperClassesInInternalForm(className: String): List<String> {
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
        return getAllInterfacesInInternalForm(className).map { it.replace('/', '.') }
    }

    fun getAllInterfacesInInternalForm(className: String): List<String> {
        return doGetAllInterfaces(className).sorted()
    }

    private fun doGetAllSuperClasses(className: String): MutableList<String> {
        val classData = maybeLoadClassData(className)
        if (classData?.superClass == null) {
            return mutableListOf()
        }
        return doGetAllSuperClasses(
            classData.superClass
        ).apply { add(classData.superClass) }
    }

    private fun doGetAllInterfaces(className: String): MutableSet<String> {
        val classData = maybeLoadClassData(className)
        return mutableSetOf<String>().apply {
            if (classData?.superClass != null) {
                addAll(doGetAllInterfaces(classData.superClass))
            }
            classData?.interfaces?.forEach { interfaceClass ->
                if (add(interfaceClass)) {
                    addAll(doGetAllInterfaces(interfaceClass))
                }
            }
        }
    }

    class Builder(val classesDataCache: ClassesDataCache) {
        private val sources = mutableSetOf<File>()

        fun addSources(vararg sources: File): Builder {
            this.sources.addAll(sources)
            return this
        }

        fun addSources(sources: Iterable<File>): Builder {
            this.sources.addAll(sources)
            return this
        }

        fun build(): ClassesHierarchyResolver {
            return ClassesHierarchyResolver(classesDataCache, sources)
        }
    }
}
