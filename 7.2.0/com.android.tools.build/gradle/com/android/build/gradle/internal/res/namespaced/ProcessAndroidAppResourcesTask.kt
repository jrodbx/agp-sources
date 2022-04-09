/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.build.gradle.internal.res.namespaced

import com.android.SdkConstants
import com.android.build.gradle.internal.AndroidJarInput
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.initialize
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.services.getErrorFormatMode
import com.android.build.gradle.internal.services.registerAaptService
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File

/**
 * Task to link the resource and the AAPT2 static libraries of its dependencies.
 *
 * Currently only implemented for tests. TODO: Clean up split support so this can support splits too.
 *
 * Outputs an ap_ file that can then be merged with the rest of the app to become a functioning apk,
 * as well as the generated R classes for this app that can be compiled against.
 */
@CacheableTask
abstract class ProcessAndroidAppResourcesTask : NonIncrementalTask() {

    @get:InputFiles @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE) lateinit var aaptFriendlyManifestFileDirectory: Provider<Directory> private set
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) lateinit var manifestFileDirectory: Provider<Directory> private set
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val thisSubProjectStaticLibrary: RegularFileProperty
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) lateinit var libraryDependencies: FileCollection private set

    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) lateinit var sharedLibraryDependencies: FileCollection private set

    @get:OutputDirectory lateinit var aaptIntermediateDir: File private set
    @get:OutputDirectory abstract val rClassSource: DirectoryProperty
    @get:OutputFile abstract val resourceApUnderscoreDirectory: DirectoryProperty

    @get:Nested
    abstract val aapt2: Aapt2Input

    @get:Input
    @get:Optional
    abstract val noCompress: ListProperty<String>

    @get:Nested
    abstract val androidJarInput: AndroidJarInput

    override fun doTaskAction() {
        val staticLibraries = ImmutableList.builder<File>()
        staticLibraries.add(thisSubProjectStaticLibrary.get().asFile)
        staticLibraries.addAll(libraryDependencies.files)
        val manifestFile = if (aaptFriendlyManifestFileDirectory.isPresent())
            (File(aaptFriendlyManifestFileDirectory.get().asFile, SdkConstants.ANDROID_MANIFEST_XML))
        else (File(manifestFileDirectory.get().asFile, SdkConstants.ANDROID_MANIFEST_XML))

        val config = AaptPackageConfig(
                androidJarPath = androidJarInput.getAndroidJar().get().absolutePath,
                manifestFile = manifestFile,
                options = AaptOptions(noCompress.orNull, additionalParameters = null),
                staticLibraryDependencies = staticLibraries.build(),
                imports = ImmutableList.copyOf(sharedLibraryDependencies.asIterable()),
                sourceOutputDir = rClassSource.get().asFile,
                resourceOutputApk = resourceApUnderscoreDirectory.get().file("res.apk").asFile,
                variantType = VariantTypeImpl.LIBRARY,
                intermediateDir = aaptIntermediateDir)

        val aapt2ServiceKey = aapt2.registerAaptService()
        workerExecutor.noIsolation().submit(Aapt2LinkRunnable::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.aapt2ServiceKey.set(aapt2ServiceKey)
            it.request.set(config)
            it.errorFormatMode.set(aapt2.getErrorFormatMode())
        }
    }

    class CreationAction(creationConfig: ComponentCreationConfig) :
        VariantTaskCreationAction<ProcessAndroidAppResourcesTask, ComponentCreationConfig>(
            creationConfig
        ) {

        override val name: String
            get() = computeTaskName("process", "NamespacedResources")
        override val type: Class<ProcessAndroidAppResourcesTask>
            get() = ProcessAndroidAppResourcesTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<ProcessAndroidAppResourcesTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ProcessAndroidAppResourcesTask::rClassSource
            ).withName("out").on(InternalArtifactType.RUNTIME_R_CLASS_SOURCES)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ProcessAndroidAppResourcesTask::resourceApUnderscoreDirectory
            ).withName("out").on(InternalArtifactType.PROCESSED_RES)
        }

        override fun configure(
            task: ProcessAndroidAppResourcesTask
        ) {
            super.configure(task)

            val artifacts = creationConfig.artifacts
            task.aaptFriendlyManifestFileDirectory =
                artifacts.get(InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS)

            task.manifestFileDirectory =
                artifacts.get(creationConfig.manifestArtifactType)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.RES_STATIC_LIBRARY,
                task.thisSubProjectStaticLibrary
            )
            task.libraryDependencies =
                    creationConfig.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.RES_STATIC_LIBRARY)
            task.sharedLibraryDependencies =
                    creationConfig.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.RES_SHARED_STATIC_LIBRARY)

            task.aaptIntermediateDir =
                    FileUtils.join(
                            creationConfig.services.projectInfo.getIntermediatesDir(), "res-process-intermediate", creationConfig.dirName)

            task.androidJarInput.initialize(creationConfig)
            if (creationConfig is ApkCreationConfig) {
                task.noCompress.set(creationConfig.androidResources.noCompress)
            }
            task.noCompress.disallowChanges()
            creationConfig.services.initializeAapt2Input(task.aapt2)

        }
    }

}
