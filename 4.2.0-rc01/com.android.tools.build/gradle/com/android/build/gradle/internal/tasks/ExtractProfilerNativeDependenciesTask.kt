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

import com.android.SdkConstants
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.utils.isValidZipEntryName
import com.android.utils.FileUtils
import com.google.common.io.ByteStreams
import com.google.common.io.Files
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Task to extract native libraries needed for profilers support in the IDE. Here, only jars are
 * extracted, but the actual .so files extraction happens downstream.
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
            ).on(InternalArtifactType.PROFILERS_NATIVE_LIBS)
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
            parameters.inputJars.forEach {
                extractSingleFile(it) { name -> outputDir.resolve(name + SdkConstants.DOT_JAR) }
            }
        }
    }
}

internal fun extractSingleFile(inputJar: File, outputLocation: (String) -> File) {
    // To avoid https://bugs.openjdk.java.net/browse/JDK-7183373
    // we extract the resources directly as a zip file.
    ZipInputStream(FileInputStream(inputJar)).use { zis ->
        val pattern = Pattern.compile("dependencies/(.*)\\.jar")
        var entry: ZipEntry? = zis.nextEntry
        while (entry != null && isValidZipEntryName(entry)) {
            val matcher = pattern.matcher(entry.name)
            if (matcher.matches()) {
                val name = matcher.group(1)
                val outputJar: File = outputLocation.invoke(name)
                Files.createParentDirs(outputJar)
                FileOutputStream(outputJar).use { fos -> ByteStreams.copy(zis, fos) }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }
}