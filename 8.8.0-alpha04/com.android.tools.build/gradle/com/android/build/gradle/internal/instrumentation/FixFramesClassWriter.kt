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
open class FixFramesClassWriter : ClassWriter {

    private val objectClassInternalName = "java/lang/Object"

    private val issueHandler: InstrumentationIssueHandler?
    val classesHierarchyResolver: ClassesHierarchyResolver

    constructor(
        flags: Int,
        classesHierarchyResolver: ClassesHierarchyResolver
    ) : super(flags) {
        this.classesHierarchyResolver = classesHierarchyResolver
        this.issueHandler = null
    }

    constructor(
        classReader: ClassReader,
        flags: Int,
        classesHierarchyResolver: ClassesHierarchyResolver,
        issueHandler: InstrumentationIssueHandler?
    ) : super(classReader, flags) {
        this.classesHierarchyResolver = classesHierarchyResolver
        this.issueHandler = issueHandler
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

    private fun getNumberOfCharInPrefix(string: String, char: Char): Int {
        var count = 0
        for (c in string) {
            if (c != char) {
                return count
            }
            count++
        }
        return count
    }

    private fun getCommonSuperClassForArrayTypes(firstType: String, secondType: String): String {
        val firstTypeArrayNestingDepth = getNumberOfCharInPrefix(firstType, '[')
        val secondTypeArrayNestingDepth = getNumberOfCharInPrefix(secondType, '[')

        if (firstTypeArrayNestingDepth != secondTypeArrayNestingDepth) {
            return objectClassInternalName
        }

        val firstComponentType =
                firstType.substring(firstTypeArrayNestingDepth + 1, firstType.length - 1)
        val secondComponentType =
                secondType.substring(firstTypeArrayNestingDepth + 1, secondType.length - 1)

        val firstTypeInterfaces =
                classesHierarchyResolver.getAllInterfacesInInternalForm(firstComponentType)
        val firstTypeSuperClasses =
                classesHierarchyResolver.getAllSuperClassesInInternalForm(firstComponentType)
        val secondTypeInterfaces =
                classesHierarchyResolver.getAllInterfacesInInternalForm(secondComponentType)
        val secondTypeSuperClasses =
                classesHierarchyResolver.getAllSuperClassesInInternalForm(secondComponentType)

        if (isAssignableFrom(firstComponentType,
                        secondComponentType,
                        secondTypeInterfaces,
                        secondTypeSuperClasses)) {
            return firstType
        }

        if (isAssignableFrom(secondComponentType,
                        firstComponentType,
                        firstTypeInterfaces,
                        firstTypeSuperClasses)) {
            return secondType
        }

        return objectClassInternalName
    }

    override fun getCommonSuperClass(firstType: String, secondType: String): String {
        if (firstType.startsWith('[') || secondType.startsWith('[')) {
            return getCommonSuperClassForArrayTypes(firstType, secondType)
        }

        val firstTypeInterfaces =
                classesHierarchyResolver.getAllInterfacesInInternalForm(firstType)
        val firstTypeSuperClasses =
                classesHierarchyResolver.getAllSuperClassesInInternalForm(firstType)
        val secondTypeInterfaces =
                classesHierarchyResolver.getAllInterfacesInInternalForm(secondType)
        val secondTypeSuperClasses =
                classesHierarchyResolver.getAllSuperClassesInInternalForm(secondType)

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

        // Unable to find common super type, check which class is the cause of this
        issueHandler?.let {
            if (firstTypeSuperClasses.isEmpty() && firstType != objectClassInternalName) {
                issueHandler.warnAboutClassNotOnTheClasspath(firstType)
            }
            if (secondTypeSuperClasses.isEmpty() && secondType != objectClassInternalName) {
                issueHandler.warnAboutClassNotOnTheClasspath(secondType)
            }
        }

        return objectClassInternalName
    }
}
