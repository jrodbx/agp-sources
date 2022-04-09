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

package com.android.build.gradle.internal.res

import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.internal.AndroidJarInput
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.initialize
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.PROJECT
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_RESOURCE_PKG
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.services.getErrorFormatMode
import com.android.build.gradle.internal.services.registerAaptService
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.ide.common.resources.mergeIdentifiedSourceSetFiles
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
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
import java.io.IOException

/**
 * Task to link app resources into a proto format so that it can be consumed by the bundle tool.
 */
@CacheableTask
abstract class LinkAndroidResForBundleTask : NonIncrementalTask() {
    @get:Input
    abstract val debuggable: Property<Boolean>

    // Not an input as it is only used to rewrite exceptions and doesn't affect task output
    @get:Internal
    abstract val mergeBlameLogFolder: DirectoryProperty

    // Not an input as it is only used to rewrite exceptions and doesn't affect task output
    @get:Internal
    abstract val manifestMergeBlameFile: RegularFileProperty

    private var buildTargetDensity: String? = null

    @get:OutputFile
    abstract val bundledResFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var featureResourcePackages: FileCollection
        private set

    @get:Input
    @get:Optional
    abstract val resOffset: Property<Int>

    @get:OutputDirectory
    lateinit var incrementalFolder: File
        private set

    @get:Input
    abstract val excludeResSourcesForReleaseBundles: Property<Boolean>

    @get:Nested
    abstract val aapt2: Aapt2Input

    @get:Nested
    abstract val androidJarInput: AndroidJarInput

    // No effect on task output, used for generating absolute paths for error messaging.
    @get:Internal
    abstract val sourceSetMaps: ConfigurableFileCollection

    private var compiledDependenciesResources: ArtifactCollection? = null

    override fun doTaskAction() {

        val manifestFile = manifestFile.get().asFile

        val outputFile = bundledResFile.get().asFile
        FileUtils.mkdirs(outputFile.parentFile)

        val featurePackagesBuilder = ImmutableList.builder<File>()
        for (featurePackage in featureResourcePackages) {
            val buildElements = BuiltArtifactsLoaderImpl.loadFromDirectory(featurePackage)
            if (buildElements?.elements?.size != 1) {
                throw IOException("Found more than one PROCESSED_RES output at $featurePackage")
            }

            featurePackagesBuilder.add(File(buildElements.elements.first().outputFile))
        }

        val compiledDependenciesResourcesDirs =
            getCompiledDependenciesResources()?.reversed()?.toImmutableList() ?: emptyList<File>()

        val identifiedSourceSetMap =
                mergeIdentifiedSourceSetFiles(sourceSetMaps.files.filterNotNull())

        val config = AaptPackageConfig(
            androidJarPath = androidJarInput.getAndroidJar().get().absolutePath,
            generateProtos = true,
            manifestFile = manifestFile,
            options = AaptOptions(noCompress.get(), aaptAdditionalParameters.get()),
            resourceOutputApk = outputFile,
            variantType = VariantTypeImpl.BASE_APK,
            packageId = resOffset.orNull,
            allowReservedPackageId = minSdkVersion < AndroidVersion.VersionCodes.O,
            dependentFeatures = featurePackagesBuilder.build(),
            resourceDirs = ImmutableList.Builder<File>().addAll(compiledDependenciesResourcesDirs)
                .add(
                    checkNotNull(getInputResourcesDir().orNull?.asFile)
                ).build(),
            resourceConfigs = ImmutableSet.copyOf(resConfig),
            // We only want to exclude res sources for release builds and when the flag is turned on
            // This will result in smaller release bundles
            excludeSources = excludeResSourcesForReleaseBundles.get() && debuggable.get().not(),
            mergeBlameDirectory = mergeBlameLogFolder.get().asFile,
            manifestMergeBlameFile = manifestMergeBlameFile.orNull?.asFile,
            identifiedSourceSetMap = identifiedSourceSetMap
        )
        if (logger.isInfoEnabled) {
            logger.info("Aapt output file {}", outputFile.absolutePath)
        }

        val aapt2ServiceKey = aapt2.registerAaptService()
        workerExecutor.noIsolation().submit(Aapt2ProcessResourcesRunnable::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.aapt2ServiceKey.set(aapt2ServiceKey)
            it.request.set(config)
            it.errorFormatMode.set(aapt2.getErrorFormatMode())
        }
    }

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val manifestFile: RegularFileProperty

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract fun getInputResourcesDir(): DirectoryProperty

    @get:Input
    lateinit var resConfig: Collection<String> private set

    @get:Input
    abstract val noCompress: ListProperty<String>

    @get:Input
    abstract val aaptAdditionalParameters: ListProperty<String>

    /**
     * Returns a file collection of the directories containing the compiled remote libraries
     * resource files.
     */
    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getCompiledDependenciesResources(): FileCollection? {
        return compiledDependenciesResources?.artifactFiles
    }

    @get:Input
    var minSdkVersion: Int = 1
        private set

    class CreationAction(apkCreationConfig: ApkCreationConfig) :
        VariantTaskCreationAction<LinkAndroidResForBundleTask, ApkCreationConfig>(
            apkCreationConfig
        ) {

        override val name: String
            get() = computeTaskName("bundle", "Resources")
        override val type: Class<LinkAndroidResForBundleTask>
            get() = LinkAndroidResForBundleTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<LinkAndroidResForBundleTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                LinkAndroidResForBundleTask::bundledResFile
            ).withName("bundled-res.ap_").on(InternalArtifactType.LINKED_RES_FOR_BUNDLE)
        }

        override fun configure(
            task: LinkAndroidResForBundleTask
        ) {
            super.configure(task)

            val variantScope = creationConfig.variantScope
            val projectOptions = creationConfig.services.projectOptions

            task.incrementalFolder = creationConfig.paths.getIncrementalDir(name)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.BUNDLE_MANIFEST,
                task.manifestFile)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.MERGED_RES,
                task.getInputResourcesDir()
            )

            task.featureResourcePackages = creationConfig.variantDependencies.getArtifactFileCollection(
                COMPILE_CLASSPATH, PROJECT, FEATURE_RESOURCE_PKG)

            if (creationConfig.variantType.isDynamicFeature && creationConfig is DynamicFeatureCreationConfig) {
                task.resOffset.set(creationConfig.resOffset)
                task.resOffset.disallowChanges()
            }

            task.debuggable.setDisallowChanges(creationConfig.debuggable)

            task.noCompress.setDisallowChanges(creationConfig.androidResources.noCompress)
            task.aaptAdditionalParameters.setDisallowChanges(
                creationConfig.androidResources.aaptAdditionalParameters
            )

            task.excludeResSourcesForReleaseBundles
                .setDisallowChanges(
                    projectOptions.getProvider(BooleanOption.EXCLUDE_RES_SOURCES_FOR_RELEASE_BUNDLES)
                )

            task.buildTargetDensity =
                    projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY)

            task.mergeBlameLogFolder.setDisallowChanges(creationConfig.artifacts.get(InternalArtifactType.MERGED_RES_BLAME_FOLDER))

            task.minSdkVersion = creationConfig.minSdkVersion.apiLevel

            task.resConfig = creationConfig.variantDslInfo.resourceConfigurations

            task.manifestMergeBlameFile.setDisallowChanges(creationConfig.artifacts.get(
                InternalArtifactType.MANIFEST_MERGE_BLAME_FILE
            ))

            if (creationConfig.isPrecompileDependenciesResourcesEnabled) {
                task.compiledDependenciesResources = creationConfig.variantDependencies.getArtifactCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.COMPILED_DEPENDENCIES_RESOURCES
                )
            }
            creationConfig.services.initializeAapt2Input(task.aapt2)
            task.androidJarInput.initialize(creationConfig)

            if (projectOptions[BooleanOption.ENABLE_SOURCE_SET_PATHS_MAP]) {
                val sourceSetMap =
                        creationConfig.artifacts.get(InternalArtifactType.SOURCE_SET_PATH_MAP)
                task.sourceSetMaps.fromDisallowChanges(
                        creationConfig.services.fileCollection(sourceSetMap)
                )
                task.dependsOn(sourceSetMap)
            } else {
                task.sourceSetMaps.disallowChanges()
            }
        }
    }
}
