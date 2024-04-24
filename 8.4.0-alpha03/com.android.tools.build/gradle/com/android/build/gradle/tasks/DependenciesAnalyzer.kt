/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Type
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.TypePath
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import java.io.InputStream
import java.lang.reflect.Modifier.isPublic

/***
 * Class responsible for analyzing a compiled class and getting a list of external dependencies
 * used by the class. It does that by using class visitors that 'visit' the class components
 * (header, fields, annotations and method signatures) and get the class names of the super class,
 * interfaces, exceptions, field and method owners, as well as the types used in attributes,
 * method returns and method parameters.
 */
class DependenciesAnalyzer {

    private val asmVersion = Opcodes.ASM7

    private val primitives = setOf(
        "void",
        "boolean",
        "byte",
        "char",
        "short",
        "int",
        "long",
        "float",
        "double")

    /** Finds all the dependencies in a .class file */
    fun findAllDependencies(bytecode: InputStream): List<String> {
        return visitClass(bytecode).keys.toList()
    }

    /** Finds only the dependencies that the .class file exposes in its public components */
    fun findPublicDependencies(bytecode: InputStream): List<String> {
        return visitClass(bytecode).filter { it.value }.keys.toList()
    }

    private fun visitClass(bytecode: InputStream): Map<String,Boolean> {
        val classReader = ClassReader(bytecode)
        val classVisitor = DependenciesClassVisitor(classReader)
        classReader.accept(classVisitor, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)

        return classVisitor.classes
    }

    /** Class Visitor */
    inner class DependenciesClassVisitor(val reader: ClassReader): ClassVisitor(asmVersion) {
        var classes = mutableMapOf<String, Boolean>()

        init {
            collectClassDependencies()
        }

        /** Visit the class header (superclass, interfaces) and save the package names
         * of all the types found */
        override fun visit(
            version: Int,
            access: Int,
            name: String?,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?) {
            if (superName != null) {
                // superName can be null if what we are analyzing is 'java.lang.Object'
                val type = getTypeFromPackageName(superName)
                addType(type, access)
            }

            interfaces?.forEach {
                val interfaceType = getTypeFromPackageName(it)
                addType(interfaceType, access)
            }

            if (signature != null) {
                addSignature(signature, access)
            }
        }

        /** Visit all the class fields and save the package names of all the types found */
        override fun visitField(
            access: Int,
            name: String?,
            desc: String?,
            signature: String?,
            value: Any?): FieldVisitor {
            if (desc != null) {
                addTypeName(Type.getType(desc), access)
            }

            if (signature != null) {
                addTypeSignature(signature, access)
            }

            if (value != null && value is Type) {
                addType(value.className, access)
            }

            return FieldDependenciesVisitor()
        }

        /** Visit all the method definitions and save the package names of all types found */
        override fun visitMethod(
            access: Int,
            name: String?,
            desc: String?,
            signature: String?,
            exceptions: Array<out String>?): MethodVisitor {
            val methodType = Type.getMethodType(desc)

            // Return type
            val type = methodType.returnType
            addTypeName(type, access)

            // Parameter types
            methodType.argumentTypes.forEach {
                addTypeName(it, access)
            }

            if (signature != null) {
                addSignature(signature, access)
            }

            return MethodDependenciesVisitor()
        }

        /** Save types found in annotations */
        override fun visitAnnotation(desc: String?, visible: Boolean): AnnotationVisitor {
            addType(Type.getType(desc).className)
            return AnnotationDependenciesVisitor()
        }

        /** Save types found in type annotations */
        override fun visitTypeAnnotation(
            typeRef: Int,
            typePath: TypePath?,
            desc: String?,
            visible: Boolean): AnnotationVisitor {
            addType(Type.getType(desc).className)
            return AnnotationDependenciesVisitor()
        }

        /** Save types found in method bodies by looking in the constant pool table */
        private fun collectClassDependencies() {
            val charBuffer = CharArray(reader.maxStringLength)
            for (i in 1 until reader.itemCount) {
                val itemOffset = reader.getItem(i)

                if (itemOffset > 0 && reader.readByte(itemOffset - 1) == 7) {
                    // A CONSTANT_Class entry, read the class descriptor
                    val classDescriptor = reader.readUTF8(itemOffset, charBuffer)
                    val type = Type.getObjectType(classDescriptor)
                    addTypeName(type)
                }
            }
        }

        private fun getTypeFromPackageName(packageName: String): String {
            return Type.getObjectType(packageName).className
        }

        private fun addType(type: String) {
            addType(type, ACC_PRIVATE)
        }

        private fun addType(type: String, access: Int) {
            if (isPrimitiveType(type)) {
                return
            }

            val className = type.replace(".", "/").plus(SdkConstants.DOT_CLASS)

            classes[className] = isPublic(access) || classes[className] ?: false
        }

        private fun addTypeName(type: Type, access: Int) {
            var elementType = type

            while(elementType.sort == Type.ARRAY) {
                // Find types in arrays
                elementType = elementType.elementType
            }

            if (elementType.sort == Type.OBJECT) {
                addType(elementType.className, access)
            }
        }

        private fun addTypeName(type: Type) {
            addTypeName(type, ACC_PRIVATE)
        }

        private fun isPrimitiveType(type: String): Boolean {
            return primitives.contains(type)
        }

        private fun addSignature(sign: String, access: Int) {
            SignatureReader(sign).accept(DependencySignatureVisitor(access))
        }

        private fun addTypeSignature(sign: String, access: Int) {
            SignatureReader(sign).acceptType(DependencySignatureVisitor(access))
        }

        inner class FieldDependenciesVisitor: FieldVisitor(api) {
            override fun visitAnnotation(desc: String?, visible: Boolean): AnnotationVisitor {
                addType(Type.getType(desc).className)
                return AnnotationDependenciesVisitor()
            }

            override fun visitTypeAnnotation(
                typeRef: Int,
                typePath: TypePath?,
                desc: String?,
                visible: Boolean): AnnotationVisitor {
                addType(Type.getType(desc).className)
                return AnnotationDependenciesVisitor()
            }
        }

        inner class MethodDependenciesVisitor: MethodVisitor(api) {
            override fun visitLocalVariable(
                name: String?,
                desc: String?,
                signature: String?,
                start: Label?,
                end: Label?,
                index: Int) {
                if (desc != null) {
                    addTypeName(Type.getType(desc))
                }
                super.visitLocalVariable(name, desc, signature, start, end, index)
            }

            override fun visitAnnotation(desc: String?, visible: Boolean): AnnotationVisitor {
                if (desc != null) {
                    addTypeName(Type.getType(desc))
                }
                return AnnotationDependenciesVisitor()
            }

            override fun visitParameterAnnotation(
                parameter: Int,
                desc: String?,
                visible: Boolean): AnnotationVisitor {
                addTypeName(Type.getType(desc))
                return AnnotationDependenciesVisitor()
            }

            override fun visitTypeAnnotation(
                typeRef: Int,
                typePath: TypePath?,
                desc: String?,
                visible: Boolean): AnnotationVisitor {
                addTypeName(Type.getType(desc))
                return AnnotationDependenciesVisitor()
            }
        }

        inner class AnnotationDependenciesVisitor: AnnotationVisitor(api) {

            override fun visit(name: String?, value: Any?) {
                if (value is Type) {
                    addType(value.className)
                }
            }

            override fun visitArray(name: String?): AnnotationVisitor {
                return this
            }

            override fun visitAnnotation(name: String?, desc: String?): AnnotationVisitor {
                addType(Type.getType(desc).className)
                return this
            }

        }

        inner class DependencySignatureVisitor(val access: Int): SignatureVisitor(api) {
            override fun visitTypeVariable(name: String?) {
                if (name != null) {
                    addType(name, access)
                }
            }

            override fun visitArrayType(): SignatureVisitor {
                return this
            }

            override fun visitClassType(name: String?) {
                if (name != null) {
                    addType(name, access)
                }
            }

            override fun visitInnerClassType(name: String?) {
                if (name != null) {
                    addType(name, access)
                }
            }
        }
    }

}
