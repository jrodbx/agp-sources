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

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.gradle.options.StringOption
import com.google.common.collect.Lists
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.ParameterizedType
import java.util.ServiceLoader
import java.util.function.BiConsumer

const val ASM_API_VERSION_FOR_INSTRUMENTATION = Opcodes.ASM9

fun getParamsImplClass(factoryClass: Class<out AsmClassVisitorFactory<*>>): Class<*> {
    return (factoryClass.genericInterfaces[0] as ParameterizedType).actualTypeArguments[0] as Class<*>
}

/**
 * Loads the bytecode transform function from jars that are passed through
 * [StringOption.IDE_ANDROID_CUSTOM_CLASS_TRANSFORMS].
 *
 * The supplied [jarFile] must follow the following convention:
 *
 * 1) Expose a service of type BiConsumer<InputStream, OutputStream>. This function will be used
 * invoked to transform a .class file.
 * 2) To output additional classes as a result of the output, they will be looked for as resources
 * in the given jar. All jars found in the "dependencies" resource directory will be output.
 */
fun loadTransformFunction(
        jarFile: File,
        jarFileClassLoader: ClassLoader
): BiConsumer<InputStream, OutputStream> {
    val serviceLoader = ServiceLoader.load(BiConsumer::class.java, jarFileClassLoader)
    val functions = Lists.newArrayList(serviceLoader.iterator())
    check(functions.isNotEmpty()) {
        "Transform jar ${jarFile.absolutePath} does not provide a BiConsumer to apply"
    }
    check(functions.size <= 1) {
        "Transform jar ${jarFile.absolutePath} than one BiConsumer to apply"
    }

    val uncheckedFunction = functions[0]
    // Validate the generic arguments are valid:
    val types = uncheckedFunction.javaClass.genericInterfaces
    for (type in types) {
        if (type is ParameterizedType) {
            val args = type.actualTypeArguments
            if (type.rawType == BiConsumer::class.java && args.size == 2 &&
                    args[0] == InputStream::class.java && args[1] == OutputStream::class.java) {
                return uncheckedFunction as BiConsumer<InputStream, OutputStream>
            }
        }
    }
    throw IllegalStateException(
            "Transform jar ${jarFile.absolutePath} must provide a BiConsumer<InputStream, OutputStream>"
    )
}

fun getJavaMajorVersionOfCompiledClass(byteCode: ByteArray): Int {
    // first 4 bytes -> java magic value, next two bytes for the minor version and the next two
    // bytes for the major version
    val offset = 6
    return ((byteCode[offset].toInt() shl 8) or (byteCode[offset + 1].toInt()))
}
