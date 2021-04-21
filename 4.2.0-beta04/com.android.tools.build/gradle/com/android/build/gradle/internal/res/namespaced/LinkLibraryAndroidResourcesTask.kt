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

import com.android.build.gradle.internal.AndroidJarInput
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
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
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
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

    @get:OutputDirectory lateinit var aaptIntermediateDir: File private set
    @get:OutputFile abstract val staticLibApk: RegularFileProperty

    @get:Nested
    abstract val androidJarInput: AndroidJarInput

    @get:Nested
    abstract val aapt2: Aapt2Input

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

        val aapt2ServiceKey = aapt2.registerAaptService()
        workerExecutor.noIsolation().submit(Aapt2LinkRunnable::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.aapt2ServiceKey.set(aapt2ServiceKey)
            it.request.set(request)
            it.errorFormatMode.set(aapt2.getErrorFormatMode())
        }
    }

    class CreationAction(
        creationConfig: ComponentCreationConfig
    ) : VariantTaskCreationAction<LinkLibraryAndroidResourcesTask, ComponentCreationConfig>(
        creationConfig
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

            creationConfig.services.initializeAapt2Input(task.aapt2)

            task.androidJarInput.sdkBuildService.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
        }
    }
}
