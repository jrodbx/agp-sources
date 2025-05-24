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
package com.android.build.gradle.internal.generators;

import com.android.build.api.variant.BuildConfigField
import com.android.builder.compiling.GeneratedCodeFileCreator
import com.google.common.base.Charsets
import com.google.common.io.Closer
import com.squareup.javawriter.JavaWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.Serializable
import java.util.EnumSet
import javax.lang.model.element.Modifier

/**
 * Class able to generate a BuildConfig class in an Android project. The BuildConfig class contains
 * constants related to the build target.
 */
class BuildConfigGenerator(buildConfigData: BuildConfigData) : GeneratedCodeFileCreator {
    private val genFolder: String = buildConfigData.outputPath.toString()
    private val namespace: String = buildConfigData.namespace
    private val fields: Map<String, BuildConfigField<out Serializable>> = buildConfigData.buildConfigFields

    /** Returns a File representing where the BuildConfig class will be.  */
    override val folderPath =
        File(
            genFolder,
            namespace.replace('.', File.separatorChar)
        )

    override val generatedFilePath = File(folderPath, BUILD_CONFIG_NAME)

    /** Generates the BuildConfig class.  */
    @Throws(IOException::class)
    override fun generate() {
        if (!folderPath.isDirectory && !folderPath.mkdirs()) {
            throw RuntimeException("Failed to create " + folderPath.absolutePath)
        }
        Closer.create().use { closer ->
            val fos =
                closer.register(FileOutputStream(generatedFilePath))
            val out = closer.register(
                OutputStreamWriter(fos, Charsets.UTF_8)
            )
            val writer = closer.register(JavaWriter(out))
            writer.emitJavadoc("Automatically generated file. DO NOT MODIFY")
                .emitPackage(namespace)
                .beginType(
                    "BuildConfig",
                    "class",
                    PUBLIC_FINAL
                )
            for ((key, value) in fields) {
                value.emit(key, writer)
            }
            writer.endType()
        }
    }

    companion object {
        const val BUILD_CONFIG_NAME = "BuildConfig.java"
        private val PUBLIC_FINAL: Set<Modifier> =
            EnumSet.of(
                Modifier.PUBLIC,
                Modifier.FINAL
            )
    }
}
