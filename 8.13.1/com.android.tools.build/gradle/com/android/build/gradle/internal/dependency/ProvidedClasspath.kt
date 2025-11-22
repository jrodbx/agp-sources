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

@file:JvmName("ProvidedClasspath")

package com.android.build.gradle.internal.dependency

import com.google.common.collect.Multimap
import com.google.common.collect.Multimaps
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.file.FileCollection
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

fun getProvidedClasspath(
    compileClasspath: ArtifactCollection, runtimeClasspath: ArtifactCollection
): FileCollection =
    ProvidedClasspathSubtractor(compileClasspath, runtimeClasspath).getFileCollection()

/**
 * Computes [FileCollection] containing all files in the provided classpath. Invoke
 * [getFileCollection] to compute that file collection.
 *
 * All artifacts in the compile classpath, whose component identifier is not in the runtime
 * classpath are in the provided classpath. E.g. for library projects that publish two separate
 * files to compile and runtime configurations, compile artifact will not be in the provided
 * classpath. If file collection subtraction were to be used, compile artifact would be in the
 * provided classpath, which is not what we want.
 */
private class ProvidedClasspathSubtractor(
    private val compileClasspath: ArtifactCollection,
    private val runtimeClasspath: ArtifactCollection
): Serializable {

    @Transient
    private var fileToIds = lazy { this.getFileToIds() }
    @Transient
    private var excludes = lazy { this.getExcludes() }

    fun getFileCollection(): FileCollection {
        return compileClasspath.artifactFiles.filter { f -> isSatisfiedBy(f) }
    }

    private fun isSatisfiedBy(file: File): Boolean {
        val fileIds = fileToIds.value.get(file)
        return !excludes.value.containsAll(fileIds)
    }

    private fun getFileToIds(): Multimap<File, ComponentIdentifier> {
        val info: Multimap<File, ComponentIdentifier> =
            Multimaps.newSetMultimap(
                HashMap(compileClasspath.artifacts.size)
            ) { HashSet<ComponentIdentifier>() }

        for (artifact in compileClasspath.artifacts) {
            info.put(artifact.file, artifact.id.componentIdentifier)
        }
        return info
    }

    private fun getExcludes(): Set<ComponentIdentifier> {
        val excludes = HashSet<ComponentIdentifier>(runtimeClasspath.artifacts.size)
        for (runtimeArtifacts in runtimeClasspath.artifacts) {
            excludes.add(runtimeArtifacts.id.componentIdentifier)
        }
        return excludes
    }

    private fun writeObject(objectOutputStream: ObjectOutputStream) {
        objectOutputStream.defaultWriteObject()
    }

    private fun readObject(objectInputStream: ObjectInputStream) {
        objectInputStream.defaultReadObject()
        fileToIds = lazy { this.getFileToIds() }
        excludes = lazy { this.getExcludes() }
    }
}
