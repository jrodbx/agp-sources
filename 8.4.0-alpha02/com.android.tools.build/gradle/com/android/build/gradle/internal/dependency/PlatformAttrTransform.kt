/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency

import com.google.common.collect.Lists
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Opcodes
import java.io.FileWriter
import java.util.zip.ZipFile

/**
 * Extract attr IDs from a jar file and puts it in a R.txt
 */
@CacheableTransform
abstract class PlatformAttrTransform : TransformAction<GenericTransformParameters> {
    @get:Classpath
    @get:InputArtifact
    abstract val primaryInput: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        //TODO(b/162813654) record transform execution span
        val outputFile = outputs.file("R.txt")

        val attributes = ZipFile(primaryInput.get().asFile).use { zip ->
            // return from let{} is passed as return for use{}
            zip.getEntry("android/R\$attr.class")?.let {
                val stream = zip.getInputStream(it)!! // this method does not return null.

                val customClassVisitor = CustomClassVisitor()
                ClassReader(stream).accept(customClassVisitor, 0)

                customClassVisitor.attributes
            }
        }

        if (attributes == null || attributes.isEmpty()) {
            error("Missing attr resources in android.jar, the file might be corrupted: $primaryInput")
        } else {
            FileWriter(outputFile).use { writer ->
                for ((name, value) in attributes) {
                    writer.write("int attr $name 0x${String.format("%08x", value)}\n")
                }
            }
        }
    }
}

data class AttributeValue(val name: String, val value: Int)

class CustomClassVisitor : ClassVisitor(Opcodes.ASM7) {

    val attributes: MutableList<AttributeValue> = Lists.newArrayList()

    override fun visitField(
        access: Int,
        name: String?,
        desc: String?,
        signature: String?,
        value: Any?
    ): FieldVisitor? {
        if (value is Int) {
            attributes.add(AttributeValue(name!!, value))
        } else if (desc.equals("I") && value == null) {
            // Workaround for b/188629419 - where attributes with 0 value are kept with null value
            // instead.
            attributes.add(AttributeValue(name!!, 0))
        }
        return null
    }
}
