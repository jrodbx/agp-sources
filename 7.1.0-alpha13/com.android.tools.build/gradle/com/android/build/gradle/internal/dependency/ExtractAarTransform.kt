/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.SdkConstants.FD_JARS
import com.android.SdkConstants.FN_CLASSES_JAR
import com.android.builder.aar.AarExtractor
import com.android.utils.FileUtils
import com.google.common.io.Files
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.work.DisableCachingByDefault
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

/** Transform that extracts an AAR file into a directory.  */
@DisableCachingByDefault
abstract class ExtractAarTransform: TransformAction<GenericTransformParameters> {

    @get:Classpath
    @get:InputArtifact
    abstract val primaryInput: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        //TODO(b/162813654) record transform execution span
        val inputFile = primaryInput.get().asFile
        val name = Files.getNameWithoutExtension(inputFile.name)
        val outputDir = outputs.dir(name)
        FileUtils.mkdirs(outputDir)
        val aarExtractor = AarExtractor()
        aarExtractor.extract(inputFile, outputDir)

        // Verify that we have a classes.jar, if we don't just create an empty one.
        val classesJar = File(File(outputDir, FD_JARS), FN_CLASSES_JAR)
        if (!classesJar.exists()) {
            try {
                Files.createParentDirs(classesJar)
                FileOutputStream(classesJar).use { out ->
                    // FileOutputStream above is the actual OS resource that will get closed,
                    // JarOutputStream writes the bytes or an empty jar in it.
                    val jarOutputStream = JarOutputStream(BufferedOutputStream(out), Manifest())
                    jarOutputStream.close()
                }
            } catch (e: IOException) {
                throw RuntimeException("Cannot create missing classes.jar", e)
            }
        }
    }
}
