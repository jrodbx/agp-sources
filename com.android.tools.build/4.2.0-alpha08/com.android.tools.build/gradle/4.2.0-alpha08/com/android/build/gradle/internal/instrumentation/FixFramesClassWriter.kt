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

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter

/** ASM class writer that uses [ClassesHierarchyResolver] to resolve types. */
class FixFramesClassWriter : ClassWriter {

    val classesHierarchyResolver: ClassesHierarchyResolver

    constructor(flags: Int, classesHierarchyResolver: ClassesHierarchyResolver) : super(flags) {
        this.classesHierarchyResolver = classesHierarchyResolver
    }

    constructor(
        classReader: ClassReader,
        flags: Int,
        classesHierarchyResolver: ClassesHierarchyResolver
    ) : super(classReader, flags) {
        this.classesHierarchyResolver = classesHierarchyResolver
    }

    private fun isAssignableFrom(
            type: String,
            otherType: String,
            otherTypeInterfaces: List<String>,
            otherTypeSuperClasses: List<String>
    ): Boolean {
        return type == otherType ||
                otherTypeInterfaces.contains(type) ||
                otherTypeSuperClasses.contains(type)
    }

    override fun getCommonSuperClass(firstType: String, secondType: String): String {
        val firstTypeInterfaces =
            classesHierarchyResolver.getAllInterfacesInInternalForm(firstType, true)
        val firstTypeSuperClasses =
            classesHierarchyResolver.getAllSuperClassesInInternalForm(firstType, true)
        val secondTypeInterfaces =
            classesHierarchyResolver.getAllInterfacesInInternalForm(secondType, true)
        val secondTypeSuperClasses =
            classesHierarchyResolver.getAllSuperClassesInInternalForm(secondType, true)

        if (isAssignableFrom(firstType, secondType, secondTypeInterfaces, secondTypeSuperClasses)) {
            return firstType
        }

        if (isAssignableFrom(secondType, firstType, firstTypeInterfaces, firstTypeSuperClasses)) {
            return secondType
        }

        firstTypeSuperClasses.forEach { firstTypeSuperClass ->
            if (isAssignableFrom(
                    firstTypeSuperClass,
                    secondType,
                    secondTypeInterfaces,
                    secondTypeSuperClasses
                )
            ) {
                return firstTypeSuperClass
            }
        }

        throw RuntimeException("Unable to find common super type for $firstType and $secondType.")
    }
}
