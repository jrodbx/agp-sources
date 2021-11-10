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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.dependency.JAR_JNI_PATTERN
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.utils.isValidZipEntryName
import com.android.utils.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.lang.RuntimeException
import java.util.function.Predicate
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Task to extract native libraries needed for profilers support in the IDE.
 */
@CacheableTask
abstract class ExtractProfilerNativeDependenciesTask : NonIncrementalTask() {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Classpath
    abstract val inputJars: ConfigurableFileCollection

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(ExtractProfilerNativeDepsWorkerAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.inputJars.from(inputJars)
            it.outputDir.set(outputDir)
        }
    }

    class CreationAction(
        apkCreationConfig: ApkCreationConfig
    ) : VariantTaskCreationAction<ExtractProfilerNativeDependenciesTask, ApkCreationConfig>(
        apkCreationConfig
    ) {

        override val name: String
            get() = computeTaskName("extract", "ProfilerNativeDependencies")

        override val type: Class<ExtractProfilerNativeDependenciesTask>
            get() = ExtractProfilerNativeDependenciesTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<ExtractProfilerNativeDependenciesTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ExtractProfilerNativeDependenciesTask::outputDir
            ).withName("out").on(InternalArtifactType.PROFILERS_NATIVE_LIBS)
        }

        override fun configure(
            task: ExtractProfilerNativeDependenciesTask
        ) {
            super.configure(task)
            task.inputJars.from(creationConfig.advancedProfilingTransforms)
        }
    }

    abstract class ExtractProfilerNativeDepsWorkerAction: ProfileAwareWorkAction<ExtractProfilerNativeDepsWorkerAction.Parameters>() {
        abstract class Parameters : ProfileAwareWorkAction.Parameters() {
            abstract val outputDir: DirectoryProperty
            abstract val inputJars: ConfigurableFileCollection
        }

        override fun run() {
            val outputDir = parameters.outputDir.get().asFile
            FileUtils.cleanOutputDir(outputDir)
            parameters.inputJars.forEach { inputJar ->
                ZipInputStream(FileInputStream(inputJar)).use { extractNestedNativeLibs(it) }
            }
        }

        private fun extractNestedNativeLibs(zipInputStream: ZipInputStream) {
            val dependencyJarPattern = Pattern.compile("dependencies/(.*)\\.jar")
            actOnMatchingZipEntries(
                zipInputStream,
                { dependencyJarPattern.matcher(it).matches() },
                { _, dependencyJarBytes ->
                    ZipInputStream(ByteArrayInputStream(dependencyJarBytes)).use {
                        extractNativeLibs(it)
                    }
                }
            )
        }

        private fun extractNativeLibs(zipInputStream: ZipInputStream) {
            val outputDir = parameters.outputDir.get().asFile
            actOnMatchingZipEntries(
                zipInputStream,
                { MergeNativeLibsTask.predicate.test(it.substringAfterLast('/'))
                        && JAR_JNI_PATTERN.matcher(it).matches()
                },
                { zipEntry, nativeLibBytes ->
                    // omit the "lib/" entry.name prefix in the output path
                    val relativePath = zipEntry.name.substringAfter('/')
                    val osRelativePath =
                        relativePath.replace('/', File.separatorChar)
                    val outFile = FileUtils.join(outputDir, osRelativePath)
                    if (outFile.exists()) {
                        throw RuntimeException(
                            "Unexpected duplicate profiler native dependency: $relativePath"
                        )
                    }
                    FileUtils.mkdirs(outFile.parentFile)
                    outFile.writeBytes(nativeLibBytes)
                }
            )
        }

        /**
         * Iterates through the zip entries and performs the specified action with each entry whose
         * name matches the given predicate.
         *
         * This function does not open or close the [ZipInputStream]; it assumes the stream is
         * already open.
         */
        private fun actOnMatchingZipEntries(
            zis: ZipInputStream,
            predicate: Predicate<String>,
            action: (ZipEntry, ByteArray) -> Unit
        ) {
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null && isValidZipEntryName(entry)) {
                if (predicate.test(entry.name)) {
                    action(entry, zis.readBytes())
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
