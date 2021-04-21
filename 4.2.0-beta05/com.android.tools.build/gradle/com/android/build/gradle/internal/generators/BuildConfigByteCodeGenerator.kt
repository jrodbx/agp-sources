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

package com.android.build.gradle.internal.generators

import com.android.SdkConstants
import com.android.builder.compiling.GeneratedCodeFileCreator
import com.android.builder.packaging.JarFlinger
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.Method
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.zip.Deflater.NO_COMPRESSION

/** Creates a JVM bytecode BuildConfig. */
class BuildConfigByteCodeGenerator(private val data: BuildConfigData) :
    GeneratedCodeFileCreator {

    private val fullyQualifiedBuildConfigClassName: String by lazy {
        "${data.buildConfigPackageName.replace('.', '/')}/${data.buildConfigName}"
    }

    override val folderPath: File = data.outputPath.toFile().also { it.mkdirs() }

    override val generatedFilePath: File = File(folderPath,
        "${data.buildConfigName}${SdkConstants.DOT_JAR}")

    /** Creates a JAR file within the genFolder containing a build config .class which is
     * generated based on the current class attributes.
     */
    override fun generate() = writeToJar(
            generatedFilePath.toPath(),
            """${data.buildConfigPackageName.replace('.', '/')}/${data
                    .buildConfigName}${SdkConstants.DOT_CLASS}""".trimMargin(),
            generateByteCode()
    )

    private fun generateByteCode() = generateUsingAsm().toByteArray()

    private fun generateUsingAsm(): ClassWriter {
        val cw = ClassWriter(COMPUTE_MAXS)

        // Class Signature
        cw.visit(
                V1_8,
                ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
                fullyQualifiedBuildConfigClassName,
                null,
                "java/lang/Object",
                null
        )

        // Field Attributes
        data.buildConfigFields.forEach {
            it.value.emit(it.key, cw)
        }

        val constructorMethod = Method.getMethod("void <init> ()")
        val cGen = GeneratorAdapter(ACC_PUBLIC, constructorMethod, null, null, cw)
        cGen.loadThis()
        cGen.invokeConstructor(Type.getType(Object::class.java), constructorMethod)
        cGen.returnValue()
        cGen.endMethod()

        cw.visitEnd()

        return cw
    }

    @Throws(IOException::class)
    private fun writeToJar(
            outputPath: Path, buildConfigPackage: String, bytecodeBuildConfig: ByteArray) {
        outputPath.toFile().createNewFile()
        JarFlinger(outputPath).use { jarCreator ->
            jarCreator.setCompressionLevel(NO_COMPRESSION)
            jarCreator.addEntry(buildConfigPackage, bytecodeBuildConfig.inputStream())
        }
    }
}