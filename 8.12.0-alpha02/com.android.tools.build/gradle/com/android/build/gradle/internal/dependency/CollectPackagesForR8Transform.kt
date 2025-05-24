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

package com.android.build.gradle.internal.dependency

import com.android.SdkConstants
import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.FD_JARS
import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.SdkConstants.FN_CLASSES_JAR
import com.android.SdkConstants.FN_PACKAGE_LIST
import com.android.SdkConstants.FN_PROGUARD_TXT
import com.android.SdkConstants.LIBS_FOLDER
import com.android.build.gradle.internal.dependency.ExtractProGuardRulesTransform.Companion.getEntriesWithProguardRules
import com.android.ide.common.xml.AndroidManifestParser
import com.android.utils.PathUtils
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Transforms the external library AAR or JARs into package lists when consumer proguard rules are present, to be used
 * in the R8 task for gradual R8 shrinking.
 */
@DisableCachingByDefault
abstract class CollectPackagesForR8Transform : TransformAction<GenericTransformParameters> {

    @get:Classpath
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(transformOutputs: TransformOutputs) {
        val inputFile = inputArtifact.get().asFile

        if (inputFile.isDirectory) {
            transformExplodedAar(inputFile, transformOutputs)
        } else {
            transformJar(inputFile, transformOutputs)
        }
    }

    private fun transformExplodedAar(explodedAarDirectory: File, transformOutputs: TransformOutputs) {
        val packageNames = mutableSetOf<String>()
        val jarsDir = explodedAarDirectory.resolve(FD_JARS)
        val classesJar = jarsDir.resolve(FN_CLASSES_JAR)
        if (!classesJar.isFile) {
            return
        }
        val allJars = mutableListOf(classesJar)
        if (!explodedAarDirectory.resolve(FN_PROGUARD_TXT).isFile &&
            !containsConsumerProguardRules(classesJar, extractLegacyProguardRules = false)) {
            return
        }

        val manifestFile = explodedAarDirectory.resolve(FN_ANDROID_MANIFEST_XML)
        val packageNameFromManifest = AndroidManifestParser.parse(manifestFile.toPath()).`package`
        if (!packageNameFromManifest.isNullOrBlank()) {
            packageNames.add("$packageNameFromManifest.*")
        }

        val localJarsDir = jarsDir.resolve(LIBS_FOLDER)
        if (localJarsDir.isDirectory) {
            val localJars = localJarsDir.listFiles {
                file: File -> file.path.endsWith(DOT_JAR)
            }!!.toList()
            allJars.addAll(localJars)
        }
        allJars.forEach { jar ->
            val packageNamesFromJar = getPackageNamesFromJar(jar)
            packageNames.addAll(packageNamesFromJar)
        }

        transformOutputs.file(FN_PACKAGE_LIST).apply {
            writeText(packageNames.joinToString(separator = "\n"))
        }
    }

    private fun transformJar(jarFile: File, transformOutputs: TransformOutputs) {
        val packageNames = mutableSetOf<String>()
        if (!containsConsumerProguardRules(jarFile, extractLegacyProguardRules = true)) {
            return
        }
        packageNames.addAll(getPackageNamesFromJar(jarFile))
        transformOutputs.file(FN_PACKAGE_LIST).apply {
            writeText(packageNames.joinToString(separator = "\n"))
        }
    }

    private fun containsConsumerProguardRules(classesJar: File, extractLegacyProguardRules: Boolean): Boolean {
        ZipFile(classesJar, StandardCharsets.UTF_8).use { jarFile ->
            val entries = getEntriesWithProguardRules(jarFile, extractLegacyProguardRules)
            if (entries.hasNext()) {
                return true
            }
        }
        return false
    }

    private fun getPackageNamesFromJar(jar: File): Collection<String> {
        val packageNames = mutableSetOf<String>()
        ZipInputStream(jar.inputStream().buffered()).use { stream ->
            while (true) {
                val jarEntry = stream.nextEntry ?: break
                if (jarEntry.name.endsWith(SdkConstants.DOT_CLASS)) {
                    val entryPath = Paths.get(jarEntry.name)
                    val systemIndependentPath = PathUtils.toSystemIndependentPath(entryPath)
                    val lastSeparatorIndex = systemIndependentPath.lastIndexOf("/")
                    if (lastSeparatorIndex == -1) {
                        continue
                    }
                    val packagePath = systemIndependentPath.substring(0, lastSeparatorIndex)
                    packageNames.add(packagePath.replace("/", ".") + ".*")
                }
            }
        }
        return packageNames
    }
}
