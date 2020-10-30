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

import com.android.build.api.variant.BuildConfigField
import com.squareup.javawriter.JavaWriter
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.IOException
import java.io.Serializable
import java.util.EnumSet
import javax.lang.model.element.Modifier

@Throws(IOException::class)
fun <T: Serializable> BuildConfigField<T>.emit(name: String, writer: ClassWriter) {
    val pfsOpcodes = Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_STATIC
    when (type) {
        "boolean" ->
            writer.visitField(pfsOpcodes, name, Type.getDescriptor(Boolean::class.java), null, value).visitEnd()
        "int" ->
            writer.visitField(pfsOpcodes, name, Type.getDescriptor(Int::class.java), null, value).visitEnd()
        "long" ->
            writer.visitField(pfsOpcodes, name, Type.getDescriptor(Long::class.java), null, value).visitEnd()
        "String" ->
            writer.visitField(pfsOpcodes, name, Type.getDescriptor(String::class.java), null, value).visitEnd()
        else -> throw IllegalArgumentException(
            """BuildConfigField name: $name type: $type and value type: ${value.javaClass
                .name} cannot be emitted.""".trimMargin())
    }
}

@Throws(IOException::class)
fun <T: Serializable> BuildConfigField<T>.emit(name: String, writer: JavaWriter) {
    val publicStaticFinal = EnumSet.of(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
    if (comment != null) {
        writer.emitSingleLineComment(comment)
    }
    val valueToWrite = value
    // Hack (see IDEA-100046): We want to avoid reporting "condition is always
    // true" from the data flow inspection, so use a non-constant value.
    // However, that defeats the purpose of this flag (when not in debug mode,
    // if (BuildConfig.DEBUG && ...) will be completely removed by
    // the compiler), so as a hack we do it only for the case where debug is
    // true, which is the most likely scenario while the user is looking
    // at source code. map.put(PH_DEBUG, Boolean.toString(mDebug));
    val emitValue = if (name == "DEBUG") {
        if (value is Boolean && value == true) "Boolean.parseBoolean(\"true\")" else "false"
    } else {
        if (valueToWrite is String) {
            if (valueToWrite.length > 2 && valueToWrite.first() == '"' && valueToWrite.last() == '"') {
                valueToWrite
            } else {
                """"$valueToWrite""""
            }
        } else valueToWrite.toString()
    }

    val formattedEmitValue = if (!type.contains("string", true)) {
        val removedQuotes = emitValue.removeSurrounding('"'.toString())
        when {
            type.contains("long", true) && !removedQuotes.endsWith("L") -> {
                removedQuotes.plus("L")
            }
            type.contains("float", true) && !removedQuotes.endsWith("f") -> {
                removedQuotes.plus("f")
            }
            else -> removedQuotes
        }
    } else {
        emitValue
    }
    writer.emitField(type, name, publicStaticFinal, formattedEmitValue)
}