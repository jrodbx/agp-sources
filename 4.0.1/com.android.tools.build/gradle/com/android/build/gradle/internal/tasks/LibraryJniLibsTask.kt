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

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants
import com.android.SdkConstants.DOT_AAR
import com.android.SdkConstants.DOT_JAR
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.STRIPPED_NATIVE_LIBS
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.utils.isValidZipEntryName
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.utils.FileUtils
import com.google.common.base.Joiner
import com.google.common.base.Preconditions
import com.google.common.io.ByteStreams
import com.google.common.io.Files
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.Serializable
import java.nio.ByteBuffer
import java.util.regex.Pattern
import java.util.zip.ZipFile
import javax.inject.Inject

private val pattern = Pattern.compile("lib/[^/]+/[^/]+\\.so")

/**
 * A task that copies the project native libs (and optionally the native libs from local jars)
 */
@CacheableTask
abstract class LibraryJniLibsTask : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract var projectNativeLibs: Provider<Directory>
        protected set

    @get:Classpath
    @get:Optional
    abstract var localJarsNativeLibs: FileCollection?
        protected set

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    override fun doTaskAction() {
        getWorkerFacadeWithThreads(useGradleExecutor = true).use { workers ->
            LibraryJniLibsDelegate(
                projectNativeLibs.get().asFile,
                localJarsNativeLibs?.files ?: listOf(),
                outputDirectory.get().asFile,
                workers
            ).copyFiles()
        }
    }

    class LibraryJniLibsDelegate(
        private val projectNativeLibs: File,
        private val localJarsNativeLibs: Collection<File>,
        val outputDirectory: File,
        val workers: WorkerExecutorFacade
    ) {
        fun copyFiles() {
            FileUtils.cleanOutputDir(outputDirectory)
            val inputFiles = listOf(projectNativeLibs) + localJarsNativeLibs.toList()
            for (inputFile in inputFiles) {
                workers.submit(
                    LibraryJniLibsRunnable::class.java,
                    LibraryJniLibsRunnable.Params(inputFile, outputDirectory)
                )
            }
        }
    }

    class LibraryJniLibsRunnable @Inject constructor(val params: Params) : Runnable {

        class Params(val inputFile: File, val outputDirectory: File) : Serializable

        override fun run() {
            if (params.inputFile.isFile) {
                Preconditions.checkState(
                    params.inputFile.name.endsWith(DOT_JAR)
                            || params.inputFile.name.endsWith(DOT_AAR),
                    "Non-directory inputs must have .jar or .aar extension: ${params.inputFile}")
                copyFromJar(params.inputFile, params.outputDirectory)
            } else {
                copyFromFolder(params.inputFile, params.outputDirectory)
            }
        }
    }

    abstract class CreationAction(
        variantScope: VariantScope,
        val artifactType: InternalArtifactType<Directory>
    ) : VariantTaskCreationAction<LibraryJniLibsTask>(variantScope) {
        override val type = LibraryJniLibsTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out LibraryJniLibsTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesDir(
                artifactType = artifactType,
                taskProvider = taskProvider,
                productProvider = LibraryJniLibsTask::outputDirectory,
                fileName = SdkConstants.FD_JNI
            )
        }

        override fun configure(task: LibraryJniLibsTask) {
            super.configure(task)
            task.projectNativeLibs = variantScope.artifacts.getFinalProduct(STRIPPED_NATIVE_LIBS)
        }
    }

    class ProjectOnlyCreationAction(
        variantScope: VariantScope,
        artifactType: InternalArtifactType<Directory>
    ): CreationAction(variantScope, artifactType) {
        override val name: String = variantScope.getTaskName("copy", "JniLibsProjectOnly")

        override fun configure(task: LibraryJniLibsTask) {
            super.configure(task)
            task.localJarsNativeLibs = null
        }
    }

    class ProjectAndLocalJarsCreationAction(
        variantScope: VariantScope,
        artifactType: InternalArtifactType<Directory>
    ) : CreationAction(variantScope, artifactType) {
        override val name: String = variantScope.getTaskName("copy", "JniLibsProjectAndLocalJars")

        override fun configure(task: LibraryJniLibsTask) {
            super.configure(task)
            task.localJarsNativeLibs = variantScope.localPackagedJars
        }
    }
}

private fun copyFromFolder(rootDirectory: File, outputDirectory: File) {
    copyFromFolder(rootDirectory, outputDirectory, mutableListOf())
}

private fun copyFromFolder(from: File, outputDirectory: File, pathSegments: MutableList<String>) {
    val children =
        from.listFiles {
                file, name -> file.isDirectory || name.endsWith(SdkConstants.DOT_NATIVE_LIBS)
        }

    if (children != null) {
        for (child in children) {
            pathSegments.add(child.name)
            if (child.isDirectory) {
                copyFromFolder(child, outputDirectory, pathSegments)
            } else if (child.isFile) {
                if (pattern.matcher(Joiner.on('/').join(pathSegments)).matches()) {
                    // copy the file. However we do want to skip the first segment ('lib') here
                    // since the 'jni' folder is representing the same concept.
                    val to = FileUtils.join(outputDirectory, pathSegments.subList(1, 3))
                    FileUtils.mkdirs(to.parentFile)
                    FileUtils.copyFile(child, to)
                }
            }
            pathSegments.removeAt(pathSegments.size - 1)
        }
    }
}

private fun copyFromJar(jarFile: File, outputDirectory: File) {
    ZipFile(jarFile).use { zipFile ->
        val entries = zipFile.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()

            val entryPath = entry.name
            if (!pattern.matcher(entryPath).matches() || !isValidZipEntryName(entry)) {
                continue
            }

            // read the content.
            val byteBuffer = zipFile.getInputStream(entry).use {
                ByteBuffer.wrap(ByteStreams.toByteArray(it))
            }

            // get the output file and write to it.
            val to = computeFile(outputDirectory, entryPath)
            FileUtils.mkdirs(to.parentFile)
            Files.write(byteBuffer.array(), to)
        }
    }
}

/**
 * computes a file path from a root folder and a zip archive path, removing the lib/ part of the
 * path
 * @param rootFolder the root folder
 * @param path the archive path
 * @return the File
 */
private fun computeFile(rootFolder: File, path: String) =
        File(rootFolder, FileUtils.toSystemDependentPath(path.substring(4)))

