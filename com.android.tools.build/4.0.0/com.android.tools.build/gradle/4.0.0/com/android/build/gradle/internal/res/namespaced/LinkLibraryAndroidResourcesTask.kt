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

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.res.getAapt2FromMavenAndVersion
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.MultipleArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.getAapt2DaemonBuildService
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.SyncOptions
import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
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

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val convertedLibraryDependencies: DirectoryProperty

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

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var androidJar: Provider<File>
        private set

    @get:Internal
    abstract val aapt2DaemonBuildService: Property<Aapt2DaemonBuildService>

    private lateinit var errorFormatMode: SyncOptions.ErrorFormatMode

    override fun doTaskAction() {

        val imports = ImmutableList.builder<File>()
        // Link against library dependencies
        imports.addAll(libraryDependencies.files)
        convertedLibraryDependencies.let {
            it.get().asFile.listFiles().forEach { imports.add(it) }
        }
        imports.addAll(sharedLibraryDependencies.files)

        val request = AaptPackageConfig(
                androidJarPath = androidJar.get().absolutePath,
                manifestFile = manifestFile.get().asFile,
                options = AaptOptions(null, false, null),
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
        variantScope: VariantScope
    ) : VariantTaskCreationAction<LinkLibraryAndroidResourcesTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("link", "Resources")
        override val type: Class<LinkLibraryAndroidResourcesTask>
            get() = LinkLibraryAndroidResourcesTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out LinkLibraryAndroidResourcesTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesFile(
                InternalArtifactType.RES_STATIC_LIBRARY,
                taskProvider,
                LinkLibraryAndroidResourcesTask::staticLibApk,
                "res.apk"
            )
        }

        override fun configure(task: LinkLibraryAndroidResourcesTask) {
            super.configure(task)

            variantScope.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.STATIC_LIBRARY_MANIFEST,
                task.manifestFile
            )

            task.inputResourcesDirectories.set(
                variantScope.artifacts.getOperations().getAll(
                    MultipleArtifactType.RES_COMPILED_FLAT_FILES))
            task.libraryDependencies =
                    variantScope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.RES_STATIC_LIBRARY)
            if (variantScope.artifacts.hasFinalProduct(
                    InternalArtifactType.RES_CONVERTED_NON_NAMESPACED_REMOTE_DEPENDENCIES)) {
                variantScope.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.RES_CONVERTED_NON_NAMESPACED_REMOTE_DEPENDENCIES,
                    task.convertedLibraryDependencies)
            }
            task.sharedLibraryDependencies =
                    variantScope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.RES_SHARED_STATIC_LIBRARY)

            val testedScope = variantScope.testedVariantData?.scope
            if (testedScope != null) {
                testedScope.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.RES_STATIC_LIBRARY,
                    task.tested
                )
            }

            task.aaptIntermediateDir =
                    FileUtils.join(
                            variantScope.globalScope.intermediatesDir, "res-link-intermediate", variantScope.variantDslInfo.dirName)

            task.packageForR.set(variantScope.globalScope.project.provider {
                variantScope.variantDslInfo.originalApplicationId
            })
            task.packageForR.disallowChanges()

            val (aapt2FromMaven, aapt2Version) = getAapt2FromMavenAndVersion(variantScope.globalScope)
            task.aapt2FromMaven.from(aapt2FromMaven)
            task.aapt2Version = aapt2Version

            task.androidJar = variantScope.globalScope.sdkComponents.androidJarProvider

            task.errorFormatMode = SyncOptions.getErrorFormatMode(
                variantScope.globalScope.projectOptions
            )
            task.aapt2DaemonBuildService.set(getAapt2DaemonBuildService(task.project))
        }
    }

}
