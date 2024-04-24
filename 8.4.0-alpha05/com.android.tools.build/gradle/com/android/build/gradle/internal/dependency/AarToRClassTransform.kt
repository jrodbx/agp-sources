/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.build.gradle.internal.dependency.AarToClassTransform.Companion.copyAllClassesJarsTo
import com.android.build.gradle.internal.dependency.AarToClassTransform.Companion.generateRClassJarFromRTxt
import com.android.builder.packaging.JarCreator
import com.android.builder.packaging.JarFlinger
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.nio.file.Path
import java.util.zip.Deflater.NO_COMPRESSION
import java.util.zip.ZipFile

/**
 * An Artifact transform which generates R.jar based on the r.txt of an AAR.
 */
@CacheableTransform
abstract class AarToRClassTransform : TransformAction<TransformParameters.None> {

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val inputAarFile: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        ZipFile(inputAarFile.get().asFile).use { inputAar ->
            val outputFileName = "${inputAarFile.get().asFile.nameWithoutExtension}-R$DOT_JAR"
            val outputJar = outputs.file(outputFileName).toPath()
            generateRClassJar(outputJar, inputAar)
        }
    }

    private fun generateRClassJar(
        outputJar: Path,
        inputAar: ZipFile
    ) {
        JarFlinger(outputJar, JarCreator.CLASSES_ONLY).use { outputApiJar ->
            outputApiJar.setCompressionLevel(NO_COMPRESSION)
            generateRClassJarFromRTxt(outputApiJar, inputAar)
            inputAar.copyAllClassesJarsTo(outputApiJar)
        }
    }
}
