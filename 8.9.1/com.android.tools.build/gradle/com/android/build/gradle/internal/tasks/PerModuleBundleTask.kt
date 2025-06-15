/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.SdkConstants.DOT_DEX
import com.android.SdkConstants.FD_ASSETS
import com.android.SdkConstants.FD_DEX
import com.android.build.api.artifact.SingleArtifact
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.dependency.AndroidAttributes
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.MODULE_PATH
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.STRIPPED_NATIVE_LIBS
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.tasks.factory.AndroidVariantTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.dexing.DexingType
import com.android.builder.files.NativeLibraryAbiPredicate
import com.android.builder.packaging.DexFileComparator
import com.android.builder.packaging.JarFlinger
import com.android.bundle.RuntimeEnabledSdkConfigProto
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate
import java.util.zip.Deflater

/**
 * Task that zips a module's bundle elements into a zip file. This gets published
 * so that the base app can package into the bundle.
 *
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.BUNDLE_PACKAGING)
abstract class PerModuleBundleTask: NonIncrementalTask() {

    @get:Optional
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Optional
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    /** Whether this is a base module or a dynamic feature module. */
    @get:Input
    abstract val baseModule: Property<Boolean>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dexDirectories: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val featureDexDirectories: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val javaResJar: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val featureJavaResFiles: ConfigurableFileCollection

    @get:Input
    @get:Optional // Set if baseModule == true
    abstract val runResourceShrinkingWithR8: Property<Boolean>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional // Set iff baseModule == true && runResourceShrinkingWithR8 == true
    abstract val shrunkResourcesDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional // Set iff baseModule == true && runResourceShrinkingWithR8 == false
    abstract val linkedResourcesFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional // Set iff baseModule == false
    abstract val featureShrunkResourcesFiles: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional // Set iff baseModule == false
    abstract val featureLinkedResourcesFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    abstract val assetsFilesDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nativeLibsFiles: ConfigurableFileCollection

    @get:Input
    abstract val abiFilters: SetProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val baseModuleMetadata: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val appMetadata: RegularFileProperty

    @get:Optional
    @get:Input
    abstract val fileName: Property<String>

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val privacySandboxSdkRuntimeConfigFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val versionControlInfoMetadata: RegularFileProperty

    public override fun doTaskAction() {
        val outputPath =
                (outputFile.orNull?.asFile ?: File(outputDir.get().asFile, fileName.get())).toPath()
        JarFlinger(outputPath, null).use { jarCreator ->
            // Disable compression for module zips, since this will only be used in bundletool and it
            // will need to uncompress them anyway.
            jarCreator.setCompressionLevel(Deflater.NO_COMPRESSION)

            val filters = baseModuleMetadata.singleOrNull()?.let {
                ModuleMetadata.load(it).abiFilters.toSet()
            } ?: abiFilters.get()

            val abiFilter = filters?.let { NativeLibraryAbiPredicate(it, false) }

            // https://b.corp.google.com/issues/140219742
            val excludeJarManifest =
                Predicate { path: String ->
                    !path.uppercase(Locale.US).endsWith("MANIFEST.MF")
                }

            jarCreator.addDirectory(
                assetsFilesDirectory.get().asFile.toPath(),
                null,
                null,
                Relocator(FD_ASSETS)
            )

            // Sort and rename the dex files similarly to how they are packaged in the APK
            // (see DexIncrementalRenameManager)
            val sortedDexFiles = getDexDirectories().asFileTree.files.sortedWith(DexFileComparator)
            addHybridFolder(jarCreator, sortedDexFiles, DexRelocator(FD_DEX), excludeJarManifest)

            addHybridFolder(jarCreator, getJavaResourcesFiles(), Relocator("root"), JarFlinger.EXCLUDE_CLASSES)

            jarCreator.addJar(getResourcesFile().toPath(), excludeJarManifest, ResRelocator())

            addHybridFolder(
                jarCreator,
                appMetadata.orNull?.asFile?.let { metadataFile -> setOf(metadataFile) }
                    ?: setOf(),
                Relocator("root/META-INF/com/android/build/gradle"),
                JarFlinger.EXCLUDE_CLASSES)
            addHybridFolder(
                jarCreator, versionControlInfoMetadata.orNull?.asFile?.let {
                    metadataFile -> setOf(metadataFile)
                } ?: setOf(),
                Relocator("root/META-INF/"),
                JarFlinger.EXCLUDE_CLASSES)

            addHybridFolder(
                jarCreator,
                nativeLibsFiles.files,
                { entryPath -> entryPath }, // don't relocate
                fileFilter = abiFilter)

            if (privacySandboxSdkRuntimeConfigFile.isPresent) {
                val runtimeConfigFile = privacySandboxSdkRuntimeConfigFile.get().asFile
                val runtimeConfigBytes = runtimeConfigFile.readBytes()
                val privacySandboxRuntimeConfig =
                        RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig
                                .parseFrom(runtimeConfigBytes)
                if (privacySandboxRuntimeConfig.runtimeEnabledSdkList.isNotEmpty()) {
                    jarCreator.addFile(
                            "runtime_enabled_sdk_config.pb",
                            runtimeConfigFile.toPath()
                    )
                }
            }
        }
    }

    private fun addHybridFolder(
        jarCreator: JarFlinger,
        files: Iterable<File>,
        relocator: JarFlinger.Relocator,
        fileFilter: Predicate<String>? = null
    ) {
        // in this case the artifact is a folder containing things to add
        // to the zip. These can be file to put directly, jars to copy the content
        // of, or folders
        for (file in files) {
            if (file.isFile) {
                if (file.name.endsWith(SdkConstants.DOT_JAR)) {
                    jarCreator.addJar(file.toPath(), fileFilter, relocator)
                } else if (fileFilter == null || fileFilter.test(file.name)) {
                    jarCreator.addFile(relocator.relocate(file.name), file.toPath())
                }
            } else if (file.exists()) {
                jarCreator.addDirectory(
                    file.toPath(),
                    fileFilter,
                    null,
                    relocator
                )
            }
        }
    }

    /**
     * Whether this is a dynamic feature module and the base module has shrinking enabled
     * (see [ApplicationCreationConfig.shrinkingWithDynamicFeatures]).
     *
     * Note: From a dynamic feature module, we don't have this info at task configuration time,
     * so we need to check it at task execution time, and check it indirectly through the presence
     * of [featureDexDirectories].
     */
    private fun isDynamicFeatureAndShrinkingEnabledInBase(): Boolean {
        // This returns true when the directories are set, even if they may be empty directories
        return !baseModule.get() && featureDexDirectories.files.isNotEmpty()
    }

    private fun getDexDirectories(): FileCollection {
        return if (isDynamicFeatureAndShrinkingEnabledInBase()) {
            featureDexDirectories
        } else {
            dexDirectories
        }
    }

    private fun getJavaResourcesFiles(): Set<File> {
        // we check isDynamicFeatureAndShrinkingEnabledInBase() instead of checking if
        // featureJavaResFiles.files.isNotEmpty() because we want to use featureJavaResFiles
        // even if it's empty (which will be the case when using proguard) -- see commit 65bc57f
        return if (isDynamicFeatureAndShrinkingEnabledInBase()) {
            featureJavaResFiles.files
        } else {
            setOf(javaResJar.asFile.get())
        }
    }

    private fun getResourcesFile(): File {
        return if (baseModule.get()) {
            if (runResourceShrinkingWithR8.get()) {
                shrunkResourcesDirectory.get().asFile.walk()
                    .single { it.path.endsWith(SdkConstants.DOT_RES) }
            } else {
                linkedResourcesFile.get().asFile
            }
        } else {
            if (featureShrunkResourcesFiles.files.isNotEmpty()) {
                featureShrunkResourcesFiles.files.single()
            } else {
                featureLinkedResourcesFile.get().asFile
            }
        }
    }

    class PrivacySandboxSdkCreationAction(
        private val creationConfig: PrivacySandboxSdkVariantScope
    ): AndroidVariantTaskCreationAction<PerModuleBundleTask>() {

        override val name: String = "buildModuleForBundle"
        override val type: Class<PerModuleBundleTask> = PerModuleBundleTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<PerModuleBundleTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                PerModuleBundleTask::outputFile
            ).withName("base.zip").on(PrivacySandboxSdkInternalArtifactType.MODULE_BUNDLE)
        }

        override fun configure(task: PerModuleBundleTask) {
            super.configure(task)

            task.baseModule.setDisallowChanges(true)
            task.dexDirectories.fromDisallowChanges(
                creationConfig.artifacts.get(
                    PrivacySandboxSdkInternalArtifactType.DEX
                )
            )
            task.javaResJar.setDisallowChanges(
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_JAVA_RES)
            )
            task.linkedResourcesFile.setDisallowChanges(
                creationConfig.artifacts.get(
                    PrivacySandboxSdkInternalArtifactType.LINKED_MERGE_RES_FOR_ASB
                )
            )
            task.assetsFilesDirectory.setDisallowChanges(
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_ASSETS)
            )

            // Not applicable
            task.featureDexDirectories.fromDisallowChanges()
            task.featureJavaResFiles.fromDisallowChanges()
            task.runResourceShrinkingWithR8.setDisallowChanges(false)
            task.shrunkResourcesDirectory.disallowChanges()
            task.featureShrunkResourcesFiles.disallowChanges()
            task.featureLinkedResourcesFile.disallowChanges()
            task.nativeLibsFiles.fromDisallowChanges()
            task.abiFilters.setDisallowChanges(emptySet())
        }
    }

    class CreationAction(
        creationConfig: ApkCreationConfig
    ) : VariantTaskCreationAction<PerModuleBundleTask, ApkCreationConfig>(
        creationConfig
    ) {

        override val name: String
            get() = computeTaskName("build", "PreBundle")
        override val type: Class<PerModuleBundleTask>
            get() = PerModuleBundleTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<PerModuleBundleTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                PerModuleBundleTask::outputDir
            ).on(InternalArtifactType.MODULE_BUNDLE)
        }

        override fun configure(
            task: PerModuleBundleTask
        ) {
            super.configure(task)

            val componentType = creationConfig.componentType
            val artifacts = creationConfig.artifacts

            task.baseModule.setDisallowChanges(
                when {
                    componentType.isBaseModule -> true
                    componentType.isDynamicFeature -> false
                    else -> error("Unexpected component type: $componentType")
                }
            )

            if (creationConfig is DynamicFeatureCreationConfig) {
                task.fileName.set(creationConfig.featureName.map { "$it.zip" })
            } else {
                task.fileName.set("base.zip")
            }
            task.fileName.disallowChanges()

            if (componentType.isBaseModule) {
                task.dexDirectories.from(artifacts.getAll(InternalMultipleArtifactType.DEX))
                // DESUGAR_LIB_DEX and GLOBAL_SYNTHETICS_DEX are separate from DEX and are only
                // packaged in base module, not in dynamic feature modules
                if (creationConfig.dexing.shouldPackageDesugarLibDex) {
                    task.dexDirectories.from(artifacts.get(InternalArtifactType.DESUGAR_LIB_DEX))
                }
                if (creationConfig.enableGlobalSynthetics
                    && creationConfig.dexing.dexingType == DexingType.NATIVE_MULTIDEX
                    && !creationConfig.optimizationCreationConfig.minifiedEnabled
                ) {
                    task.dexDirectories.from(artifacts.get(InternalArtifactType.GLOBAL_SYNTHETICS_DEX))
                }
                task.dexDirectories.disallowChanges()
            } else {
                // We set both properties below even though at execution time we'll use only one of
                // them. This adds more build time, but we currently have no choice
                // (see `isDynamicFeatureAndShrinkingEnabledInBase()`).
                task.featureDexDirectories.fromDisallowChanges(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        AndroidArtifacts.ArtifactType.FEATURE_DEX,
                        AndroidAttributes(MODULE_PATH to task.project.path)
                    )
                )
                task.dexDirectories.fromDisallowChanges(artifacts.getAll(InternalMultipleArtifactType.DEX))
            }

            task.javaResJar.setDisallowChanges(artifacts.get(InternalArtifactType.MERGED_JAVA_RES))
            task.featureJavaResFiles.fromDisallowChanges(
                creationConfig.variantDependencies.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    AndroidArtifacts.ArtifactType.FEATURE_SHRUNK_JAVA_RES,
                    AndroidAttributes(MODULE_PATH to task.project.path)
                )
            )

            if (componentType.isBaseModule) {
                task.runResourceShrinkingWithR8.setDisallowChanges(
                    (creationConfig as ApplicationCreationConfig).runResourceShrinkingWithR8())
                if (creationConfig.runResourceShrinkingWithR8()) {
                    task.shrunkResourcesDirectory.setDisallowChanges(
                        artifacts.get(InternalArtifactType.SHRUNK_RESOURCES_PROTO_FORMAT))
                } else {
                    task.linkedResourcesFile.setDisallowChanges(
                        artifacts.get(InternalArtifactType.LINKED_RESOURCES_FOR_BUNDLE_PROTO_FORMAT))
                }
            } else {
                task.featureShrunkResourcesFiles.fromDisallowChanges(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        AndroidArtifacts.ArtifactType.FEATURE_SHRUNK_RESOURCES_PROTO_FORMAT,
                        AndroidAttributes(MODULE_PATH to task.project.path)
                    )
                )
                task.featureLinkedResourcesFile.setDisallowChanges(
                    artifacts.get(InternalArtifactType.LINKED_RESOURCES_FOR_BUNDLE_PROTO_FORMAT))
            }

            task.assetsFilesDirectory.setDisallowChanges(creationConfig.artifacts.get(SingleArtifact.ASSETS))
            task.nativeLibsFiles.from(creationConfig.artifacts.get(STRIPPED_NATIVE_LIBS))

            if (creationConfig.componentType.isDynamicFeature) {
                // If this is a dynamic feature, we use the abiFilters published by the base module.
                task.baseModuleMetadata.from(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        AndroidArtifacts.ArtifactType.BASE_MODULE_METADATA
                    )
                )
            } else {
                task.abiFilters.set(
                    creationConfig.nativeBuildCreationConfig?.supportedAbis ?: emptyList()
                )
            }
            task.abiFilters.disallowChanges()
            task.baseModuleMetadata.disallowChanges()

            if (creationConfig.componentType.isBaseModule) {
                artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.APP_METADATA,
                    task.appMetadata
                )
                artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.VERSION_CONTROL_INFO_FILE,
                    task.versionControlInfoMetadata
                )
            }

            if (creationConfig.privacySandboxCreationConfig != null && creationConfig.componentType.isBaseModule) {
                artifacts.setTaskInputToFinalProduct(
                        InternalArtifactType.PRIVACY_SANDBOX_SDK_RUNTIME_CONFIG_FILE,
                        task.privacySandboxSdkRuntimeConfigFile
                )
            } else {
                task.privacySandboxSdkRuntimeConfigFile.disallowChanges()
            }

        }
    }
}

private class Relocator(private val prefix: String): JarFlinger.Relocator {
    override fun relocate(entryPath: String) = "$prefix/$entryPath"
}

/**
 * Relocate the dex files into a single folder which might force renaming
 * the dex files into a consistent scheme : classes.dex, classes2.dex, classes4.dex, ...
 *
 * <p>Note that classes1.dex is not a valid dex file name</p>
 *
 * When dealing with native multidex, we can have several folders each containing 1 to many
 * dex files (with the same naming scheme). In that case, merge and rename accordingly, note
 * that only one classes.dex can exist in the merged location so the others will be renamed.
 *
 * When dealing with a single feature (base) in legacy multidex, we must not rename the
 * main dex file (classes.dex) as it contains the bootstrap code for the application.
 */
private class DexRelocator(private val prefix: String): JarFlinger.Relocator {
    // first valid classes.dex with an index starts at 2.
    val index = AtomicInteger(2)
    val classesDexNameUsed = AtomicBoolean(false)
    override fun relocate(entryPath: String): String {
        // Note that the dex file may be in a subdirectory (e.g.,
        // `<dex_merging_task_output>/bucket_0/classes.dex`). Also, it may not have the name
        // `classesXY.dex` (e.g., it could be `ExampleClass.dex`).
        if (entryPath.endsWith(DOT_DEX, ignoreCase=true)) {
            return if (entryPath.endsWith("classes.dex") && !classesDexNameUsed.get()) {
                classesDexNameUsed.set(true)
                "$prefix/classes.dex"
            } else {
                val entryIndex = index.getAndIncrement()
                "$prefix/classes$entryIndex.dex"
            }
        }
        return "$prefix/$entryPath"
    }
}


private class ResRelocator : JarFlinger.Relocator {
    override fun relocate(entryPath: String) = when(entryPath) {
        SdkConstants.FN_ANDROID_MANIFEST_XML -> "manifest/" + SdkConstants.FN_ANDROID_MANIFEST_XML
        else -> entryPath
    }
}

