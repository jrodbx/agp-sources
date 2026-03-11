/*
 * Copyright (C) 2025 The Android Open Source Project
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

import java.io.File
import java.io.FileOutputStream
import java.util.Random
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Generate a simple class with an org.junit.Test annotated method.
 *
 * This is to trick the Gradle's Test task to always execute as it checks for annotated classes before involving the junit engine and won't
 * run the task if it cannot find a class with @Test annotated methods in it.
 *
 * We will work with Gradle to remove this limitation, making this workaround obsolete.
 */
class UniqueClassGenerator() {

  fun generateSimpleClass(location: File) {
    val className = "JourneysEntryPoint"
    val classInternalName = className.replace('.', '/')
    val fieldName = "randomNumber"
    val random = Random()
    val randomNumber = random.nextInt()

    val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    classWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER, classInternalName, null, "java/lang/Object", null)

    // Add a field to store the random number
    classWriter
      .visitField(
        Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL, // public final field
        fieldName,
        "I", // Integer type descriptor
        null,
        randomNumber, // set initial Value
      )
      .visitEnd()

    // Add default constructor
    val constructorVisitor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
    constructorVisitor.visitCode()
    constructorVisitor.visitVarInsn(Opcodes.ALOAD, 0)
    constructorVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)

    constructorVisitor.visitInsn(Opcodes.RETURN)
    constructorVisitor.visitMaxs(1, 1)
    constructorVisitor.visitEnd()

    // Test method
    val mv: MethodVisitor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "simpleTestMethod", "()V", null, null)

    // Add @Test annotation
    val av: AnnotationVisitor = mv.visitAnnotation(Type.getObjectType("org/junit/Test").descriptor, true)
    av.visitEnd()

    mv.visitCode()

    mv.visitInsn(Opcodes.RETURN)
    mv.visitMaxs(2, 1) // Adjust max stack and locals as needed
    mv.visitEnd()

    classWriter.visitEnd()

    // Write the class file to disk
    val classBytes = classWriter.toByteArray()
    location.mkdirs()
    val file = FileOutputStream(File(location, "$className.class"))
    file.write(classBytes)
    file.close()
    println("Class file $className.class generated successfully in $location.")
  }
}
