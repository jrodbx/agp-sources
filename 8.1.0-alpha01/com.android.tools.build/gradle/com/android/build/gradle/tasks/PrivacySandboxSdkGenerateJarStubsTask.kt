/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.StringOption
import com.android.buildanalyzer.common.TaskCategory
import com.android.utils.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * Task generates empty jar containing all classes to be included in a privacy sandbox sdk.
 */
@BuildAnalyzer(primaryTaskCategory = TaskCategory.METADATA)
@DisableCachingByDefault
abstract class PrivacySandboxSdkGenerateJarStubsTask : DefaultTask() {

    @get:Classpath
    abstract val mergedClasses: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val mergedJavaResources: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val apiPackager: ConfigurableFileCollection

    // Classes and java resource dirs require merging, this input is used as a temporary dir.
    @get:Internal
    abstract val sourceDirectory: DirectoryProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun doTaskAction() {
        val sourcesDir = sourceDirectory.get().asFile
        FileUtils.cleanOutputDir(sourcesDir)

        FileUtils.copyDirectory(mergedClasses.singleFile, sourcesDir)

        ZipFile(mergedJavaResources.get().asFile).use { zip ->
            zip.entries().asIterator().forEach { entry ->
                val entryBytes = zip.getInputStream(entry).readAllBytes()
                val candidateFile = FileUtils.join(sourcesDir, entry.name)
                FileUtils.createFile(candidateFile, "")
                candidateFile.writeBytes(entryBytes)
            }
        }

        FileUtils.deleteIfExists(outputJar.get().asFile)
        val outJar = outputJar.get().asFile
        val apiPackager = apiPackager.files.map { it.toURI().toURL() }.toTypedArray()
        if (apiPackager.isEmpty()) {
            throw RuntimeException("No libraries specified for packaging sandbox APIs.")
        }
        URLClassLoader(apiPackager).use {
            PrivacySandboxApiPackager(it).packageSdkDescriptors(
                    sourcesDir.toPath(), outJar.toPath()
            )
        }
    }

    class CreationAction(val creationConfig: PrivacySandboxSdkVariantScope) :
            TaskCreationAction<PrivacySandboxSdkGenerateJarStubsTask>() {

        override val name: String
            get() = "privacySandboxClassesJarStubs"

        override val type: Class<PrivacySandboxSdkGenerateJarStubsTask>
            get() = PrivacySandboxSdkGenerateJarStubsTask::class.java

        override fun handleProvider(
                taskProvider: TaskProvider<PrivacySandboxSdkGenerateJarStubsTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    PrivacySandboxSdkGenerateJarStubsTask::outputJar
            ).withName(privacySandboxSdkStubJarFilename)
                    .on(PrivacySandboxSdkInternalArtifactType.STUB_JAR)

            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    PrivacySandboxSdkGenerateJarStubsTask::sourceDirectory
            ).on(PrivacySandboxSdkInternalArtifactType.API_PACKAGER_SOURCES)
        }

        override fun configure(task: PrivacySandboxSdkGenerateJarStubsTask) {
            val apiPackagerCoordinates = creationConfig.services.projectOptions
                    .get(StringOption.ANDROID_PRIVACY_SANDBOX_SDK_API_PACKAGER)?.split(",")
                    ?: listOf(PLAY_SDK_API_PACKAGER_ARTIFACT)
            val apiPackager = creationConfig.services.configurations.detachedConfiguration()
            val apiPackagerDeps = apiPackagerCoordinates.map {
                creationConfig.services.dependencies.create(it)
            }
            apiPackager.dependencies.addAll(apiPackagerDeps)

            task.apiPackager.setFrom(apiPackager.files)
            task.mergedClasses.fromDisallowChanges(
                    creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_CLASSES)
            )
            task.mergedJavaResources.setDisallowChanges(
                    creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_JAVA_RES)
            )
        }
    }

    /* For invoking the sandbox-apipackager via reflection. */
    class PrivacySandboxApiPackager(val classLoader: URLClassLoader) {
        private val apiPackagerPackage = "androidx.privacysandbox.tools.apipackager"
        private val privacySandboxApiPackagerClass =
                classLoader.loadClass("$apiPackagerPackage.PrivacySandboxApiPackager")
        private val privacySandboxSdkPackager = privacySandboxApiPackagerClass
                .getConstructor()
                .newInstance()
        private val packageSdkDescriptorsMethod = privacySandboxApiPackagerClass
                .getMethod("packageSdkDescriptors", Path::class.java, Path::class.java)
        fun packageSdkDescriptors(sdkClasspath: Path, outputPath: Path) {
            packageSdkDescriptorsMethod
                    .invoke(privacySandboxSdkPackager, sdkClasspath, outputPath)
        }
    }

    companion object {

        /** Name of jar file containing api description that is packaged in an ASAR file. */
        const val privacySandboxSdkStubJarFilename: String = "sdk-interface-descriptors.jar"

        private const val PLAY_SDK_API_PACKAGER_ARTIFACT = "androidx.privacysandbox.tools:tools-apipackager:1.0.0-SNAPSHOT"
    }
}

