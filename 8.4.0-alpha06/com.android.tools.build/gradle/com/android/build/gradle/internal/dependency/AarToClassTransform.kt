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

package com.android.build.gradle.internal.dependency

import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.SdkConstants.FN_API_JAR
import com.android.SdkConstants.FN_CLASSES_JAR
import com.android.SdkConstants.FN_RESOURCE_TEXT
import com.android.SdkConstants.LIBS_FOLDER
import com.android.builder.packaging.JarCreator
import com.android.builder.packaging.JarFlinger
import com.android.builder.symbols.exportToCompiledJava
import com.android.ide.common.symbols.rTxtToSymbolTable
import com.android.ide.common.xml.AndroidManifestParser
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.nio.file.Path
import java.util.zip.Deflater.NO_COMPRESSION
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * A Gradle Artifact [TransformAction] from a processed AAR to a single classes JAR file.
 */
@CacheableTransform
abstract class AarToClassTransform : TransformAction<AarToClassTransform.Params> {

    interface Params : TransformParameters {
        /**
         * If set, add a generated R class jar from the R.txt to the compile classpath jar.
         *
         * Only has effect if [forCompileUse] is also set.
         */
        @get:Input
        val generateRClassJar: Property<Boolean>

        /** If set, return the compile classpath, otherwise return the runtime classpath. */
        @get:Input
        val forCompileUse: Property<Boolean>
    }

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val inputAarFile: Provider<FileSystemLocation>

    override fun transform(transformOutputs: TransformOutputs) {

        ZipFile(inputAarFile.get().asFile).use { inputAar ->
            val useSuffix = if (parameters.forCompileUse.get()) "api" else "runtime"
            val outputFileName =
                "${inputAarFile.get().asFile.nameWithoutExtension}-$useSuffix$DOT_JAR"
            val outputJar = transformOutputs.file(outputFileName).toPath()
            mergeJars(
                outputJar,
                inputAar,
                parameters.forCompileUse.get(),
                parameters.generateRClassJar.get()
            )
        }
    }

    companion object {
        @VisibleForTesting
        internal fun mergeJars(
            outputJar: Path,
            inputAar: ZipFile,
            forCompileUse: Boolean,
            generateRClassJar: Boolean
        ) {
            val ignoreFilter = if (forCompileUse) {
                JarCreator.allIgnoringDuplicateResources()
            } else {
                JarCreator.CLASSES_ONLY
            }
            JarFlinger(outputJar, ignoreFilter).use { outputApiJar ->
                // NO_COMPRESSION because the resulting jar isn't packaged into final APK or AAR
                outputApiJar.setCompressionLevel(NO_COMPRESSION)
                if (forCompileUse) {
                    if (generateRClassJar) {
                        generateRClassJarFromRTxt(outputApiJar, inputAar)
                    }
                    val apiJAr = inputAar.getEntry(FN_API_JAR)
                    if (apiJAr != null) {
                        inputAar.copyEntryTo(apiJAr, outputApiJar)
                        return
                    }
                }

                inputAar.copyAllClassesJarsTo(outputApiJar)
            }
        }

        private const val LIBS_FOLDER_SLASH = "$LIBS_FOLDER/"

        internal fun ZipFile.copyAllClassesJarsTo(outputApiJar: JarCreator) {
            entries()
                .asSequence()
                .filter(::isClassesJar)
                .forEach { copyEntryTo(it, outputApiJar) }
        }

        private fun ZipFile.copyEntryTo(entry: ZipEntry, outputApiJar: JarCreator) {
            getInputStream(entry).use {
                outputApiJar.addJar(it)
            }
        }

        internal fun generateRClassJarFromRTxt(
            outputApiJar: JarCreator,
            inputAar: ZipFile
        ) {
            val manifest = inputAar.getEntry(FN_ANDROID_MANIFEST_XML)
            val pkg = inputAar.getInputStream(manifest).use {
                AndroidManifestParser.parse(it).`package`
            }
            val rTxt = inputAar.getEntry(FN_RESOURCE_TEXT) ?: return
            val symbols = inputAar.getInputStream(rTxt).use { rTxtToSymbolTable(it, pkg) }
            exportToCompiledJava(listOf(symbols), finalIds = false, rPackage = null) { entryPath, content ->
                outputApiJar.addEntry(entryPath, content.inputStream())
            }
        }

        private fun isClassesJar(entry: ZipEntry): Boolean {
            val name = entry.name
            return name == FN_CLASSES_JAR ||
                    (name.startsWith(LIBS_FOLDER_SLASH) && name.endsWith(DOT_JAR))
        }
    }
}
