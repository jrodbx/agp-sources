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
import com.android.SdkConstants.FD_ASSETS
import com.android.SdkConstants.FD_DEX
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.BaseCreationConfig
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.packaging.JarCreatorFactory
import com.android.build.gradle.internal.packaging.JarCreatorType
import com.android.build.gradle.internal.pipeline.StreamFilter
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.MODULE_PATH
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_NATIVE_LIBS
import com.android.build.gradle.internal.scope.InternalArtifactType.STRIPPED_NATIVE_LIBS
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.files.NativeLibraryAbiPredicate
import com.android.builder.model.CodeShrinker
import com.android.builder.packaging.JarCreator
import com.android.builder.packaging.JarMerger
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate
import java.util.zip.Deflater
import javax.inject.Inject

/**
 * Task that zips a module's bundle elements into a zip file. This gets published
 * so that the base app can package into the bundle.
 *
 */
abstract class PerModuleBundleTask @Inject constructor(objects: ObjectFactory) :
    NonIncrementalTask() {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dexFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val featureDexFiles: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resFiles: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val javaResFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetsFiles: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nativeLibsFiles: ConfigurableFileCollection

    @get:Input
    abstract val abiFilters: SetProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val appMetadata: ConfigurableFileCollection

    @get:Input
    abstract val fileName: Property<String>

    @get:Input
    val jarCreatorType: Property<JarCreatorType> = objects.property(JarCreatorType::class.java)

    public override fun doTaskAction() {
        val jarCreator =
            JarCreatorFactory.make(
                jarFile = File(outputDir.get().asFile, fileName.get()).toPath(),
                type = jarCreatorType.get()
            )

        // Disable compression for module zips, since this will only be used in bundletool and it
        // will need to uncompress them anyway.
        jarCreator.setCompressionLevel(Deflater.NO_COMPRESSION)

        val filters = appMetadata.singleOrNull()?.let {
            ModuleMetadata.load(it).abiFilters.toSet()
        } ?: abiFilters.get()

        val abiFilter = filters?.let { NativeLibraryAbiPredicate(it, false) }

        jarCreator.use {
            it.addDirectory(
                assetsFiles.get().asFile.toPath(),
                null,
                null,
                Relocator(FD_ASSETS)
            )

            it.addJar(resFiles.get().asFile.toPath(), null, ResRelocator())

            // dex files
            val dexFilesSet = if (hasFeatureDexFiles()) featureDexFiles.files else dexFiles.files
            if (dexFilesSet.size == 1) {
                // Don't rename if there is only one input folder
                // as this might be the legacy multidex case.
                addHybridFolder(it, dexFilesSet.sortedBy { it.name }, Relocator(FD_DEX), null)
            } else {
                addHybridFolder(it, dexFilesSet.sortedBy { it.name }, DexRelocator(FD_DEX), null)
            }

            val javaResFilesSet = if (hasFeatureDexFiles()) setOf<File>() else javaResFiles.files
            addHybridFolder(it, javaResFilesSet,
                Relocator("root"),
                JarMerger.EXCLUDE_CLASSES)

            addHybridFolder(it, nativeLibsFiles.files, fileFilter = abiFilter)
        }
    }

    private fun addHybridFolder(
        jarCreator: JarCreator,
        files: Iterable<File>,
        relocator: JarCreator.Relocator? = null,
        fileFilter: Predicate<String>? = null ) {
        // in this case the artifact is a folder containing things to add
        // to the zip. These can be file to put directly, jars to copy the content
        // of, or folders
        for (file in files) {
            if (file.isFile) {
                if (file.name.endsWith(SdkConstants.DOT_JAR)) {
                    jarCreator.addJar(file.toPath(), fileFilter, relocator)
                } else if (fileFilter == null || fileFilter.test(file.name)) {
                    if (relocator != null) {
                        jarCreator.addFile(relocator.relocate(file.name), file.toPath())
                    } else {
                        jarCreator.addFile(file.name, file.toPath())
                    }
                }
            } else if (file.exists()){
                jarCreator.addDirectory(
                    file.toPath(),
                    fileFilter,
                    null,
                    relocator)
            }
        }
    }

    private fun hasFeatureDexFiles() = featureDexFiles.files.isNotEmpty()

    class CreationAction(
        creationConfig: ApkCreationConfig,
        private val packageCustomClassDependencies: Boolean
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
            val artifacts = creationConfig.artifacts

            if (creationConfig is DynamicFeatureCreationConfig) {
                task.fileName.set(creationConfig.featureName.map { "$it.zip" })
            } else {
                task.fileName.set("base.zip")
            }
            task.fileName.disallowChanges()

            creationConfig.artifacts.setTaskInputToFinalProduct(
                 InternalArtifactType.MERGED_ASSETS, task.assetsFiles)

            task.resFiles.set(
                if (creationConfig.variantScope.useResourceShrinker()) {
                    artifacts.get(InternalArtifactType.SHRUNK_LINKED_RES_FOR_BUNDLE)
                } else {
                    artifacts.get(InternalArtifactType.LINKED_RES_FOR_BUNDLE)
                }
            )
            task.resFiles.disallowChanges()

            task.dexFiles.from(
                if (creationConfig.variantScope.consumesFeatureJars()) {
                    artifacts.get(InternalArtifactType.BASE_DEX)
                } else {
                    artifacts.getAll(InternalMultipleArtifactType.DEX)
                }
            )
            if (creationConfig.shouldPackageDesugarLibDex) {
                task.dexFiles.from(
                    artifacts.get(InternalArtifactType.DESUGAR_LIB_DEX)
                )
            }

            task.featureDexFiles.from(
                creationConfig.variantDependencies.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    AndroidArtifacts.ArtifactType.FEATURE_DEX,
                    mapOf(MODULE_PATH to creationConfig.globalScope.project.path)
                )
            )
            task.javaResFiles.from(
                if (creationConfig.variantScope.codeShrinker == CodeShrinker.R8) {
                    creationConfig.globalScope.project.layout.files(
                        artifacts.get(InternalArtifactType.SHRUNK_JAVA_RES)
                    )
                } else if (creationConfig.variantScope.needsMergedJavaResStream) {
                    creationConfig.transformManager
                        .getPipelineOutputAsFileCollection(StreamFilter.RESOURCES)
                } else {
                    creationConfig.globalScope.project.layout.files(
                        artifacts.get(InternalArtifactType.MERGED_JAVA_RES)
                    )
                }
            )
            task.nativeLibsFiles.from(getNativeLibsFiles(creationConfig, packageCustomClassDependencies))

            if (creationConfig.variantType.isDynamicFeature) {
                // If this is a dynamic feature, we use the abiFilters published by the base module.
                task.appMetadata.from(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        AndroidArtifacts.ArtifactType.BASE_MODULE_METADATA
                    )
                )
            } else {
                task.abiFilters.set(creationConfig.variantDslInfo.supportedAbis)
            }
            task.abiFilters.disallowChanges()
            task.appMetadata.disallowChanges()

            task.jarCreatorType.set(creationConfig.variantScope.jarCreatorType)
            task.jarCreatorType.disallowChanges()
        }
    }
}

private class Relocator(private val prefix: String): JarCreator.Relocator {
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
private class DexRelocator(private val prefix: String): JarCreator.Relocator {
    // first valid classes.dex with an index starts at 2.
    val index = AtomicInteger(2)
    val classesDexNameUsed = AtomicBoolean(false)
    override fun relocate(entryPath: String): String {
        if (entryPath.startsWith("classes")) {
            return if (entryPath == "classes.dex" && !classesDexNameUsed.get()) {
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


private class ResRelocator : JarCreator.Relocator {
    override fun relocate(entryPath: String) = when(entryPath) {
        SdkConstants.FN_ANDROID_MANIFEST_XML -> "manifest/" + SdkConstants.FN_ANDROID_MANIFEST_XML
        else -> entryPath
    }
}

/**
 * Returns a file collection containing all of the native libraries to be packaged.
 */
fun getNativeLibsFiles(
    creationConfig: BaseCreationConfig,
    packageCustomClassDependencies: Boolean
): FileCollection {
    val nativeLibs = creationConfig.globalScope.project.files()
    if (creationConfig.variantType.isForTesting) {
        return nativeLibs.from(creationConfig.artifacts.get(MERGED_NATIVE_LIBS))
    }
    nativeLibs.from(creationConfig.artifacts.get(STRIPPED_NATIVE_LIBS))
    if (packageCustomClassDependencies) {
        nativeLibs.from(
            creationConfig.transformManager.getPipelineOutputAsFileCollection(StreamFilter.NATIVE_LIBS)
        )
    }
    return nativeLibs
}