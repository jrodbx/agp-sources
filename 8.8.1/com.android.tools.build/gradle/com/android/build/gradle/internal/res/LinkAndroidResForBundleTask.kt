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

import com.android.SdkConstants
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.internal.AndroidJarInput
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ApplicationCreationConfig
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
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.AndroidResourcesTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.AndroidResourcesTaskCreationActionImpl
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.build.gradle.options.BooleanOption
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.core.ComponentTypeImpl
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
import org.gradle.api.provider.SetProperty
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
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ANDROID_RESOURCES, secondaryTaskCategories = [TaskCategory.LINKING])
abstract class LinkAndroidResForBundleTask : NonIncrementalTask() {
    @get:Input
    abstract val debuggable: Property<Boolean>

    // Not an input as it is only used to rewrite exceptions and doesn't affect task output
    @get:Internal
    abstract val mergeBlameLogFolder: DirectoryProperty

    // Not an input as it is only used to rewrite exceptions and doesn't affect task output
    @get:Internal
    abstract val manifestMergeBlameFile: RegularFileProperty

    @get:OutputFile
    abstract val linkedResourcesOutputFile: RegularFileProperty

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

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val stableIdsFile: RegularFileProperty

    private var compiledDependenciesResources: ArtifactCollection? = null

    override fun doTaskAction() {

        val manifestFile = manifestFile.get().asFile

        val outputFile = linkedResourcesOutputFile.get().asFile
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
            componentType = ComponentTypeImpl.BASE_APK,
            packageId = resOffset.orNull,
            allowReservedPackageId = minSdkVersion < AndroidVersion.VersionCodes.O,
            dependentFeatures = featurePackagesBuilder.build(),
            resourceDirs = ImmutableList.Builder<File>().addAll(compiledDependenciesResourcesDirs)
                .add(
                    checkNotNull(getInputResourcesDir().orNull?.asFile)
                ).build(),
            resourceConfigs = ImmutableSet.copyOf(resConfig),
            consumeStableIdsFile = stableIdsFile.get().asFile,
            // We only want to exclude res sources for release builds and when the flag is turned on
            // This will result in smaller release bundles
            excludeSources = excludeResSourcesForReleaseBundles.get() && debuggable.get().not(),
            mergeBlameDirectory = mergeBlameLogFolder.get().asFile,
            manifestMergeBlameFile = manifestMergeBlameFile.orNull?.asFile,
            identifiedSourceSetMap = identifiedSourceSetMap,
            localeFilters = ImmutableSet.copyOf(localeFilters.get()),
            pseudoLocalesEnabled = pseudoLocalesEnabled.get()
        )
        if (logger.isInfoEnabled) {
            logger.info("Aapt output file {}", outputFile.absolutePath)
        }

        val aapt2ServiceKey = aapt2.registerAaptService()
        workerExecutor.noIsolation().submit(Aapt2ProcessResourcesRunnable::class.java) {
            it.initializeFromBaseTask(this)
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
    abstract val localeFilters: SetProperty<String>

    @get:Input
    abstract val noCompress: ListProperty<String>

    @get:Input
    abstract val aaptAdditionalParameters: ListProperty<String>

    @get:Input
    abstract val pseudoLocalesEnabled: Property<Boolean>

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
        ), AndroidResourcesTaskCreationAction by AndroidResourcesTaskCreationActionImpl(
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
                LinkAndroidResForBundleTask::linkedResourcesOutputFile
            ).withName("linked-resources-proto-format${SdkConstants.DOT_RES}")
                .on(InternalArtifactType.LINKED_RESOURCES_FOR_BUNDLE_PROTO_FORMAT)
        }

        override fun configure(
            task: LinkAndroidResForBundleTask
        ) {
            super.configure(task)

            val projectOptions = creationConfig.services.projectOptions

            task.incrementalFolder = creationConfig.paths.getIncrementalDir(name)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.BUNDLE_MANIFEST,
                task.manifestFile)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.MERGED_RES,
                task.getInputResourcesDir()
            )

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.STABLE_RESOURCE_IDS_FILE,
                task.stableIdsFile
            )

            task.featureResourcePackages = creationConfig.variantDependencies.getArtifactFileCollection(
                COMPILE_CLASSPATH, PROJECT, FEATURE_RESOURCE_PKG)

            if (creationConfig.componentType.isDynamicFeature && creationConfig is DynamicFeatureCreationConfig) {
                task.resOffset.set(creationConfig.resOffset)
                task.resOffset.disallowChanges()
            }

            task.debuggable.setDisallowChanges(creationConfig.debuggable)

            task.noCompress.setDisallowChanges(
                creationConfig.androidResources.noCompress
            )
            task.aaptAdditionalParameters.setDisallowChanges(
                creationConfig.androidResources.aaptAdditionalParameters
            )

            task.excludeResSourcesForReleaseBundles
                .setDisallowChanges(
                    projectOptions.getProvider(BooleanOption.EXCLUDE_RES_SOURCES_FOR_RELEASE_BUNDLES)
                )

            task.mergeBlameLogFolder.setDisallowChanges(creationConfig.artifacts.get(InternalArtifactType.MERGED_RES_BLAME_FOLDER))

            task.minSdkVersion = creationConfig.minSdk.apiLevel

            task.resConfig = androidResourcesCreationConfig.resourceConfigurations

            when (creationConfig) {
                is ApplicationCreationConfig -> {
                    task.localeFilters.setDisallowChanges(creationConfig.androidResources.localeFilters)
                }
                is DynamicFeatureCreationConfig -> {
                    task.localeFilters.setDisallowChanges(creationConfig.baseModuleLocaleFilters)
                }
                else -> {
                    task.localeFilters.setDisallowChanges(ImmutableSet.of())
                }
            }

            task.pseudoLocalesEnabled.setDisallowChanges(
                androidResourcesCreationConfig.pseudoLocalesEnabled
            )

            task.manifestMergeBlameFile.setDisallowChanges(creationConfig.artifacts.get(
                InternalArtifactType.MANIFEST_MERGE_BLAME_FILE
            ))

            if (androidResourcesCreationConfig.isPrecompileDependenciesResourcesEnabled) {
                task.compiledDependenciesResources = creationConfig.variantDependencies.getArtifactCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.COMPILED_DEPENDENCIES_RESOURCES
                )
            }
            creationConfig.services.initializeAapt2Input(task.aapt2, task)
            task.androidJarInput.initialize(task, creationConfig)

            val sourceSetMap =
                    creationConfig.artifacts.get(InternalArtifactType.SOURCE_SET_PATH_MAP)
            task.sourceSetMaps.fromDisallowChanges(
                    creationConfig.services.fileCollection(sourceSetMap)
            )
            task.dependsOn(sourceSetMap)
        }
    }
}
