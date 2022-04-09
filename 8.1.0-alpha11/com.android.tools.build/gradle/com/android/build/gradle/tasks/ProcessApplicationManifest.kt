/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.getApiString
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.dependency.ArtifactCollectionWithExtraArtifact.ExtraComponentIdentifier
import com.android.build.gradle.internal.ide.dependencies.getIdString
import com.android.build.gradle.internal.profile.ProfilingMode
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.MANIFEST_MERGE_REPORT
import com.android.build.gradle.internal.scope.InternalArtifactType.NAVIGATION_JSON
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.manifest.ManifestProviderImpl
import com.android.build.gradle.internal.tasks.manifest.mergeManifests
import com.android.build.gradle.internal.utils.parseTargetHash
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.dexing.DexingType
import com.android.ide.common.resources.generateLocaleConfigManifestAttribute
import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.ManifestMerger2.Invoker
import com.android.manifmerger.ManifestMerger2.WEAR_APP_SUB_MANIFEST
import com.android.manifmerger.ManifestProvider
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
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
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.util.EnumSet

/** A task that processes the manifest  */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.MANIFEST)
abstract class ProcessApplicationManifest : ManifestProcessorTask() {

    private var manifests: ArtifactCollection? = null
    private var featureManifests: ArtifactCollection? = null

    /** The merged Manifests files folder.  */
    @get:OutputFile
    abstract val mergedManifest: RegularFileProperty

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    @get:InputFiles
    var dependencyFeatureNameArtifacts: FileCollection? = null
        private set

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    @get:InputFiles
    abstract val microApkManifest: RegularFileProperty

    @get:Optional
    @get:Input
    abstract val baseModuleDebuggable: Property<Boolean>

    @get:Optional
    @get:Input
    abstract val packageOverride: Property<String>

    @get:Input
    abstract val namespace: Property<String>

    @get:Input
    abstract val profileable: Property<Boolean>

    @get:Input
    abstract val testOnly: Property<Boolean>

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    abstract val manifestOverlays: ListProperty<File>

    @get:Optional
    @get:Input
    abstract val manifestPlaceholders: MapProperty<String, String>

    private var isFeatureSplitVariantType = false
    private var buildTypeName: String? = null

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    @get:Optional
    var navigationJsons: FileCollection? = null
        private set

    @get:Input
    abstract val addLocaleConfigAttribute: Property<Boolean>

    /**
     * For non-application components, we still depend on the multiApkManifests artifacts as part of
     * the manifest processing pipeline, and since these components don't have splits, we can just
     * use this task to output the main manifest to that directory without having to run a task to
     * just copy the manifest.
     */
    @get:Optional
    @get:OutputDirectory
    abstract val multiApkManifestOutputDirectory: DirectoryProperty

    @Throws(IOException::class)
    override fun doTaskAction() {
        if (baseModuleDebuggable.isPresent) {
            val isDebuggable = optionalFeatures.get()
                .contains(Invoker.Feature.DEBUGGABLE)
            if (baseModuleDebuggable.get() != isDebuggable) {
                val errorMessage = String.format(
                    "Dynamic Feature '%1\$s' (build type '%2\$s') %3\$s debuggable,\n"
                            + "and the corresponding build type in the base "
                            + "application %4\$s debuggable.\n"
                            + "Recommendation: \n"
                            + "   in  %5\$s\n"
                            + "   set android.buildTypes.%2\$s.debuggable = %6\$s",
                    projectPath.get(),
                    buildTypeName,
                    if (isDebuggable) "is" else "is not",
                    if (baseModuleDebuggable.get()) "is" else "is not",
                    projectBuildFile.get().asFile,
                    if (baseModuleDebuggable.get()) "true" else "false"
                )
                throw InvalidUserDataException(errorMessage)
            }
        }
        val navJsons = navigationJsons?.files ?: setOf()

        val mergingReport = mergeManifests(
            mainManifest.get(),
            manifestOverlays.get(),
            computeFullProviderList(),
            navJsons,
            featureName.orNull,
            packageOverride.get(),
            namespace.get(),
            profileable.get(),
            versionCode.orNull,
            versionName.orNull,
            minSdkVersion.orNull,
            targetSdkVersion.orNull,
            maxSdkVersion.orNull,
            testOnly.get(),
            mergedManifest.get().asFile.absolutePath /* aaptFriendlyManifestOutputFile */,
            null /* outAaptSafeManifestLocation */,
            ManifestMerger2.MergeType.APPLICATION,
            manifestPlaceholders.get(),
            optionalFeatures.get().plus(
                mutableListOf<Invoker.Feature>().also {
                    if (!jniLibsUseLegacyPackaging.get()) {
                        it.add(Invoker.Feature.DO_NOT_EXTRACT_NATIVE_LIBS)
                    }
                }
            ),
            dependencyFeatureNames,
            generatedLocaleConfigAttribute = if (addLocaleConfigAttribute.get()) {
                generateLocaleConfigManifestAttribute(LOCALE_CONFIG_FILE_NAME)
            } else {
                null
            },
            reportFile.get().asFile,
            LoggerWrapper.getLogger(ProcessApplicationManifest::class.java),
            compileSdk = compileSdk.orNull
        )
        outputMergeBlameContents(mergingReport, mergeBlameFile.get().asFile)

        // This is a non-application variant, add the main manifest to the
        // multiApkManifestOutputDirectory directly.
        if (multiApkManifestOutputDirectory.isPresent) {
            val outputFile = File(
                multiApkManifestOutputDirectory.get().asFile,
                SdkConstants.ANDROID_MANIFEST_XML
            )
            mergedManifest.get().asFile.copyTo(outputFile, overwrite = true)

            BuiltArtifactsImpl(
                artifactType = InternalArtifactType.MERGED_MANIFESTS,
                applicationId = applicationId.get(),
                variantName = variantName,
                elements = listOf(
                    BuiltArtifactImpl.make(
                        outputFile = outputFile.absolutePath
                    )
                )
            ).save(multiApkManifestOutputDirectory.get())
        }
    }

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFile
    abstract val mainManifest: Property<File>

    /**
     * Compute the final list of providers based on the manifest file collection and the other
     * providers.
     *
     * @return the list of providers.
     */
    private fun computeFullProviderList(): List<ManifestProvider> {
        val artifacts = manifests!!.artifacts
        val providers = mutableListOf<ManifestProvider>()
        for (artifact in artifacts) {
            providers.add(
                    ManifestProviderImpl(
                            artifact.file,
                            getArtifactName(artifact)
                    )
            )
        }
        if (microApkManifest.isPresent) {
            // this is now always present if embedding is enabled, but it doesn't mean
            // anything got embedded so the file may not run (the file path exists and is
            // returned by the FC but the file doesn't exist.
            val microManifest = microApkManifest.get().asFile
            if (microManifest.isFile) {
                providers.add(
                        ManifestProviderImpl(
                                microManifest, WEAR_APP_SUB_MANIFEST
                        )
                )
            }
        }

        if (featureManifests != null) {
            providers.addAll(computeProviders(featureManifests!!.artifacts))
        }
        return providers
    }

    // Only feature splits can have feature dependencies
    private val dependencyFeatureNames: List<String>
        get() {
            val list: MutableList<String> = ArrayList()
            return if (!isFeatureSplitVariantType) { // Only feature splits can have feature dependencies
                list
            } else try {
                for (file in dependencyFeatureNameArtifacts!!.files) {
                    list.add(org.apache.commons.io.FileUtils.readFileToString(file))
                }
                list
            } catch (e: IOException) {
                throw UncheckedIOException("Could not load feature declaration", e)
            }
        }

    @get:Input
    abstract val applicationId: Property<String>

    @get:Input
    @get:Optional
    abstract val compileSdk: Property<Int>

    @get:Optional
    @get:Input
    abstract val minSdkVersion: Property<String?>

    @get:Optional
    @get:Input
    abstract val targetSdkVersion: Property<String?>

    @get:Optional
    @get:Input
    abstract val maxSdkVersion: Property<Int?>

    @get:Input
    abstract val optionalFeatures: SetProperty<Invoker.Feature>

    @get:Input
    abstract val jniLibsUseLegacyPackaging: Property<Boolean>

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getManifests(): FileCollection {
        return manifests!!.artifactFiles
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getFeatureManifests(): FileCollection? {
        return if (featureManifests == null) {
            null
        } else featureManifests!!.artifactFiles
    }

    @get:Optional
    @get:Input
    abstract val featureName: Property<String>

    @get:Internal("only for task execution")
    abstract val projectBuildFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val versionCode: Property<Int?>

    @get:Input
    @get:Optional
    abstract val versionName: Property<String?>

    class CreationAction(
        creationConfig: ApkCreationConfig
    ) : VariantTaskCreationAction<ProcessApplicationManifest, ApkCreationConfig>(creationConfig) {
        override val name: String
            get() = computeTaskName("process", "MainManifest")

        override val type: Class<ProcessApplicationManifest>
            get() = ProcessApplicationManifest::class.java

        override fun preConfigure(
            taskName: String
        ) {
            super.preConfigure(taskName)
            val componentType = creationConfig.componentType
            Preconditions.checkState(!componentType.isTestComponent)
            val artifacts = creationConfig.artifacts
            artifacts.republish(
                InternalArtifactType.PACKAGED_MANIFESTS,
                InternalArtifactType.MANIFEST_METADATA
            )
        }

        override fun handleProvider(
            taskProvider: TaskProvider<ProcessApplicationManifest>
        ) {
            super.handleProvider(taskProvider)
            val artifacts = creationConfig.artifacts
            artifacts.setInitialProvider(
                taskProvider,
                ProcessApplicationManifest::mergedManifest
            )
                .on(SingleArtifact.MERGED_MANIFEST)

            artifacts.setInitialProvider(
                taskProvider,
                ManifestProcessorTask::mergeBlameFile
            )
                .withName("manifest-merger-blame-" + creationConfig.baseName + "-report.txt")
                .on(InternalArtifactType.MANIFEST_MERGE_BLAME_FILE)
            artifacts.setInitialProvider(
                taskProvider,
                ProcessApplicationManifest::reportFile
            )
                .atLocation(
                    FileUtils.join(
                        creationConfig.services.projectInfo.getOutputsDir(),
                        "logs"
                    )
                        .absolutePath
                )
                .withName("manifest-merger-" + creationConfig.baseName + "-report.txt")
                .on(MANIFEST_MERGE_REPORT)

            if (creationConfig !is ApplicationCreationConfig) {
                creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    ProcessApplicationManifest::multiApkManifestOutputDirectory
                ).on(InternalArtifactType.MERGED_MANIFESTS)
            }
        }

        override fun configure(
            task: ProcessApplicationManifest
        ) {
            super.configure(task)
            val componentType = creationConfig.componentType
            // This includes the dependent libraries.
            task.manifests = creationConfig
                .variantDependencies
                .getArtifactCollection(
                    ConsumedConfigType.RUNTIME_CLASSPATH,
                    ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.MANIFEST
                )
            // optional manifest files too.
            if (creationConfig.taskContainer.microApkTask != null
                && creationConfig.embedsMicroApp
            ) {
                creationConfig.artifacts.setTaskInputToFinalProduct(
                        InternalArtifactType.MICRO_APK_MANIFEST_FILE,
                        task.microApkManifest
                )
            }

            task.applicationId.set(creationConfig.applicationId)
            task.applicationId.disallowChanges()
            val compileSdkHashString =
                    (creationConfig as? ApplicationCreationConfig)?.global?.compileSdkHashString

            task.compileSdk.setDisallowChanges(
                    if (compileSdkHashString != null) {
                        parseTargetHash(compileSdkHashString).apiLevel
                    } else {
                        null
                    }
            )
            task.minSdkVersion.setDisallowChanges(creationConfig.minSdk.getApiString())
            task.targetSdkVersion
                .setDisallowChanges(
                        if (creationConfig.targetSdk.apiLevel < 1)
                            null
                        else creationConfig.targetSdk.getApiString()
                )
            task.maxSdkVersion.setDisallowChanges(
                (creationConfig as VariantCreationConfig).maxSdk
            )
            task.optionalFeatures.setDisallowChanges(creationConfig.services.provider {
                getOptionalFeatures(
                    creationConfig
                )
            })
            task.jniLibsUseLegacyPackaging.setDisallowChanges(
                creationConfig.packaging.jniLibs.useLegacyPackaging
            )
            if (creationConfig is ApplicationCreationConfig) {
                creationConfig.outputs.getMainSplit().let {
                    task.versionCode.setDisallowChanges(it.versionCode)
                    task.versionName.setDisallowChanges(it.versionName)
                }
            } else if (creationConfig is DynamicFeatureCreationConfig) {
                task.versionCode.setDisallowChanges(creationConfig.baseModuleVersionCode)
                task.versionName.setDisallowChanges(creationConfig.baseModuleVersionName)
            }
            // set optional inputs per module type
            if (componentType.isBaseModule) {
                task.featureManifests = creationConfig
                    .variantDependencies
                    .getArtifactCollection(
                        ConsumedConfigType.REVERSE_METADATA_VALUES,
                        ArtifactScope.PROJECT,
                        AndroidArtifacts.ArtifactType.REVERSE_METADATA_FEATURE_MANIFEST
                    )
            } else if (componentType.isDynamicFeature) {
                val dfCreationConfig =
                    creationConfig as DynamicFeatureCreationConfig
                task.featureName.setDisallowChanges(dfCreationConfig.featureName)
                task.baseModuleDebuggable.setDisallowChanges(dfCreationConfig.baseModuleDebuggable)
                task.dependencyFeatureNameArtifacts = creationConfig
                    .variantDependencies
                    .getArtifactFileCollection(
                        ConsumedConfigType.RUNTIME_CLASSPATH,
                        ArtifactScope.PROJECT,
                        AndroidArtifacts.ArtifactType.FEATURE_NAME
                    )
            }
            if (!creationConfig.global.namespacedAndroidResources) {
                task.navigationJsons = creationConfig.services.fileCollection(
                    creationConfig.artifacts.get(NAVIGATION_JSON),
                    creationConfig
                        .variantDependencies
                        .getArtifactFileCollection(
                            ConsumedConfigType.RUNTIME_CLASSPATH,
                            ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.NAVIGATION_JSON
                        )
                )
            }
            task.packageOverride.setDisallowChanges(creationConfig.applicationId)
            task.namespace.setDisallowChanges(creationConfig.namespace)
            task.profileable.setDisallowChanges(
                (creationConfig as? ApplicationCreationConfig)?.profileable ?: false
            )
            task.testOnly.setDisallowChanges(
                ProfilingMode.getProfilingModeType(
                    creationConfig.services.projectOptions[StringOption.PROFILING_MODE]
                ) != ProfilingMode.UNDEFINED
            )
            task.manifestPlaceholders.setDisallowChanges(
                creationConfig.manifestPlaceholdersCreationConfig?.placeholders,
                handleNullable = { empty() }
            )
            task.manifestPlaceholders.disallowChanges()
            task.mainManifest.setDisallowChanges(creationConfig.sources.manifestFile)
            task.manifestOverlays.setDisallowChanges(
                creationConfig.sources.manifestOverlayFiles.map { it.filter(File::isFile) })
            task.isFeatureSplitVariantType = creationConfig.componentType.isDynamicFeature
            task.buildTypeName = creationConfig.buildType
            task.projectBuildFile.set(task.project.buildFile)
            task.projectBuildFile.disallowChanges()
            task.addLocaleConfigAttribute.setDisallowChanges(
                (creationConfig is ApplicationCreationConfig) &&
                (creationConfig.generateLocaleConfig)
            )
            // TODO: here in the "else" block should be the code path for the namespaced pipeline
        }

    }

    companion object {
        private fun computeProviders(
            artifacts: Set<ResolvedArtifactResult>
        ): List<ManifestProvider> {
            val providers: MutableList<ManifestProvider> = mutableListOf()
            for (artifact in artifacts) {
                providers.add(
                    ManifestProviderImpl(
                        artifact.file,
                        getArtifactName(artifact)
                    )
                )
            }
            return providers
        }

        // TODO put somewhere else?
        @JvmStatic
        fun getArtifactName(artifact: ResolvedArtifactResult): String {
            return when(val id = artifact.id.componentIdentifier) {
                is ProjectComponentIdentifier -> id.getIdString()
                is ModuleComponentIdentifier -> "${id.group}:${id.module}:${id.version}"
                is OpaqueComponentArtifactIdentifier -> id.displayName
                is ExtraComponentIdentifier -> id.displayName
                else -> throw RuntimeException("Unsupported type of ComponentIdentifier")
            }
        }

        private fun getOptionalFeatures(
            creationConfig: ApkCreationConfig
        ): EnumSet<Invoker.Feature> {
            val features: MutableList<Invoker.Feature> =
                ArrayList()
            val componentType = creationConfig.componentType
            if (componentType.isDynamicFeature) {
                features.add(Invoker.Feature.ADD_DYNAMIC_FEATURE_ATTRIBUTES)
            }
            if (creationConfig.testOnlyApk) {
                features.add(Invoker.Feature.TEST_ONLY)
            }
            if (creationConfig.debuggable) {
                features.add(Invoker.Feature.DEBUGGABLE)
                if (creationConfig.advancedProfilingTransforms.isNotEmpty()) {
                    features.add(Invoker.Feature.ADVANCED_PROFILING)
                }
            }
            if (creationConfig.dexingCreationConfig.dexingType === DexingType.LEGACY_MULTIDEX) {
                features.add(
                    if (creationConfig.services.projectOptions[BooleanOption.USE_ANDROID_X]) {
                        Invoker.Feature.ADD_ANDROIDX_MULTIDEX_APPLICATION_IF_NO_NAME
                    } else {
                        Invoker.Feature.ADD_SUPPORT_MULTIDEX_APPLICATION_IF_NO_NAME
                    })
            }
            if (creationConfig.services.projectOptions[BooleanOption.ENFORCE_UNIQUE_PACKAGE_NAMES]
            ) {
                features.add(Invoker.Feature.ENFORCE_UNIQUE_PACKAGE_NAME)
            }
            if (creationConfig.services.projectOptions[BooleanOption.DISABLE_MINSDKLIBRARY_CHECK]) {
                features.add(Invoker.Feature.DISABLE_MINSDKLIBRARY_CHECK)
            }
            features.add(Invoker.Feature.VALIDATE_EXTRACT_NATIVE_LIBS_ATTRIBUTE)
            return if (features.isEmpty())
                EnumSet.noneOf(Invoker.Feature::class.java)
            else EnumSet.copyOf(features)
        }
    }
}
