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

import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.AndroidJarInput
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.res.getAapt2FromMavenAndVersion
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.SyncOptions
import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File

/**
 * Task to link the resources in a library project into an AAPT2 static library.
 */
@CacheableTask
abstract class LinkLibraryAndroidResourcesTask : NonIncrementalTask() {

    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val manifestFile: RegularFileProperty
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val inputResourcesDirectories: ListProperty<Directory>
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) lateinit var libraryDependencies: FileCollection private set

    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) lateinit var sharedLibraryDependencies: FileCollection private set
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) @get:Optional abstract val tested: RegularFileProperty

    @get:Input
    abstract val packageForR: Property<String>

    @get:Input
    lateinit var aapt2Version: String
        private set
    @get:Internal
    abstract val aapt2FromMaven: ConfigurableFileCollection

    @get:OutputDirectory lateinit var aaptIntermediateDir: File private set
    @get:OutputFile abstract val staticLibApk: RegularFileProperty

    @get:Nested
    abstract val androidJarInput: AndroidJarInput

    @get:Internal
    abstract val aapt2DaemonBuildService: Property<Aapt2DaemonBuildService>

    private lateinit var errorFormatMode: SyncOptions.ErrorFormatMode

    override fun doTaskAction() {

        val imports = ImmutableList.builder<File>()
        // Link against library dependencies
        imports.addAll(libraryDependencies.files)
        imports.addAll(sharedLibraryDependencies.files)

        val request = AaptPackageConfig(
                androidJarPath = androidJarInput.getAndroidJar().get().absolutePath,
                manifestFile = manifestFile.get().asFile,
                options = AaptOptions(),
                resourceDirs = ImmutableList.copyOf(inputResourcesDirectories.get().stream()
                    .map(Directory::getAsFile).iterator()),
                staticLibrary = true,
                imports = imports.build(),
                resourceOutputApk = staticLibApk.get().asFile,
                variantType = VariantTypeImpl.LIBRARY,
                customPackageForR = packageForR.get(),
                intermediateDir = aaptIntermediateDir)

        val aapt2ServiceKey = aapt2DaemonBuildService.get().registerAaptService(
            aapt2FromMaven = aapt2FromMaven.singleFile,
            logger = LoggerWrapper(logger)
        )
        getWorkerFacadeWithWorkers().use {
            it.submit(
                Aapt2LinkRunnable::class.java,
                Aapt2LinkRunnable.Params(aapt2ServiceKey, request, errorFormatMode)
            )
        }
    }

    class CreationAction(
        componentProperties: ComponentPropertiesImpl
    ) : VariantTaskCreationAction<LinkLibraryAndroidResourcesTask, ComponentPropertiesImpl>(
        componentProperties
    ) {

        override val name: String
            get() = computeTaskName("link", "Resources")
        override val type: Class<LinkLibraryAndroidResourcesTask>
            get() = LinkLibraryAndroidResourcesTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<LinkLibraryAndroidResourcesTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                LinkLibraryAndroidResourcesTask::staticLibApk
            ).withName("res.apk").on(InternalArtifactType.RES_STATIC_LIBRARY)
        }

        override fun configure(
            task: LinkLibraryAndroidResourcesTask
        ) {
            super.configure(task)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.STATIC_LIBRARY_MANIFEST,
                task.manifestFile
            )

            task.inputResourcesDirectories.set(
                creationConfig.artifacts.getAll(
                    InternalMultipleArtifactType.RES_COMPILED_FLAT_FILES))
            task.libraryDependencies =
                    creationConfig.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.RES_STATIC_LIBRARY)
            task.sharedLibraryDependencies =
                    creationConfig.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.RES_SHARED_STATIC_LIBRARY)

            creationConfig.onTestedConfig {
                it.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.RES_STATIC_LIBRARY,
                    task.tested
                )
            }

            task.aaptIntermediateDir =
                    FileUtils.join(
                            creationConfig.globalScope.intermediatesDir, "res-link-intermediate", creationConfig.variantDslInfo.dirName)

            task.packageForR.setDisallowChanges(creationConfig.packageName)

            val (aapt2FromMaven, aapt2Version) = getAapt2FromMavenAndVersion(creationConfig.globalScope)
            task.aapt2FromMaven.from(aapt2FromMaven)
            task.aapt2Version = aapt2Version
            task.errorFormatMode = SyncOptions.getErrorFormatMode(
                creationConfig.services.projectOptions
            )
            task.aapt2DaemonBuildService.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
            task.androidJarInput.sdkBuildService.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
        }
    }
}
