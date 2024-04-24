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

package com.android.build.api.component.impl

import com.android.build.api.variant.impl.DirectoryEntries
import com.android.build.api.variant.impl.DirectoryEntry
import com.android.build.api.variant.impl.FileBasedDirectoryEntryImpl
import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.api.variant.impl.LayeredSourceDirectoriesImpl
import com.android.build.api.variant.impl.SourceDirectoriesImpl
import com.android.build.api.variant.impl.TaskProviderBasedDirectoryEntryImpl
import com.android.build.gradle.api.AndroidSourceDirectorySet
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.api.DefaultAndroidSourceDirectorySet
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.UnitTestCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.builder.compiling.BuildConfigType
import java.io.File
import java.util.Collections

/**
 * Computes the default sources for all [com.android.build.api.variant.impl.SourceType]s.
 */
class DefaultSourcesProviderImpl(
    val component: ComponentCreationConfig,
    val variantSources: VariantSources,
) : DefaultSourcesProvider {

    override fun getJava(lateAdditionsDelegate: FlatSourceDirectoriesImpl): List<DirectoryEntry> =
        component.defaultJavaSources(lateAdditionsDelegate)

    override fun getKotlin(lateAdditionsDelegate: FlatSourceDirectoriesImpl): List<DirectoryEntry> =
        flattenSourceProviders(lateAdditionsDelegate, AndroidSourceSet::kotlin)

    override fun getRes(lateAdditionsDelegate: LayeredSourceDirectoriesImpl): List<DirectoryEntries>? =
        if (component.buildFeatures.androidResources) {
            component.defaultResSources(lateAdditionsDelegate)
        } else null

    override fun getResources(lateAdditionsDelegate: FlatSourceDirectoriesImpl): List<DirectoryEntry> =
        flattenSourceProviders(lateAdditionsDelegate) { sourceSet -> sourceSet.resources }

    override fun getAssets(lateAdditionsDelegate: LayeredSourceDirectoriesImpl): List<DirectoryEntries> =
        defaultAssetsSources(lateAdditionsDelegate)

    override fun getJniLibs(lateAdditionsDelegate: LayeredSourceDirectoriesImpl): List<DirectoryEntries> =
        getSourceList(lateAdditionsDelegate, DefaultAndroidSourceSet::jniLibs)

    override fun getShaders(lateAdditionsDelegate: LayeredSourceDirectoriesImpl): List<DirectoryEntries>? =
        if (component.buildFeatures.shaders) getSourceList(lateAdditionsDelegate) { sourceProvider ->
            sourceProvider.shaders
        } else null

    override fun getAidl(lateAdditionsDelegate: FlatSourceDirectoriesImpl): List<DirectoryEntry>? =
        if (component.buildFeatures.aidl) {
            flattenSourceProviders(lateAdditionsDelegate) { sourceSet -> sourceSet.aidl }
        } else null

    override fun getMlModels(lateAdditionsDelegate: LayeredSourceDirectoriesImpl): List<DirectoryEntries>? =
        if (component.buildFeatures.mlModelBinding) {
            getSourceList(lateAdditionsDelegate) { sourceProvider -> sourceProvider.mlModels }
        } else null

    override fun getRenderscript(lateAdditionsDelegate: FlatSourceDirectoriesImpl): List<DirectoryEntry>? =
        if (component.buildFeatures.renderScript) {
            flattenSourceProviders(lateAdditionsDelegate) { sourceSet -> sourceSet.renderscript }
        } else null

    override fun getBaselineProfiles(lateAdditionsDelegate: FlatSourceDirectoriesImpl): List<DirectoryEntry> =
        flattenSourceProviders(lateAdditionsDelegate, AndroidSourceSet::baselineProfiles )

    override val artProfile: File
        get() = variantSources.artProfile

    override val mainManifestFile: File
        get() = variantSources.mainManifestFilePath

    override val manifestOverlayFiles: List<File>
        get() = variantSources.manifestOverlayFiles

    override val sourceProvidersNames: List<String>
        get() = variantSources.getSortedSourceProviders().map { it.name }


    private fun flattenSourceProviders(
        lateAdditionsDelegate: SourceDirectoriesImpl,
        sourceDirectory: (sourceSet: AndroidSourceSet) -> com.android.build.api.dsl.AndroidSourceDirectorySet
    ): List<DirectoryEntry> {
        val sourceSets = mutableListOf<DirectoryEntry>()
        // Variant sources are added independently later so that they can be added to the model
        for (sourceProvider in variantSources.getSortedSourceProviders(false)) {
            val sourceSet = sourceProvider as AndroidSourceSet
            val androidSourceDirectorySet =
                sourceDirectory(sourceSet) as DefaultAndroidSourceDirectorySet
            androidSourceDirectorySet.addLateAdditionDelegate(lateAdditionsDelegate)
            for (srcDir in androidSourceDirectorySet.srcDirs) {
                sourceSets.add(
                    FileBasedDirectoryEntryImpl(
                        name = sourceSet.name,
                        directory = srcDir,
                        filter = androidSourceDirectorySet.filter,
                    )
                )
            }
        }
        return sourceSets
    }

    /**
     * Computes the default java sources: source sets and generated sources.
     * For access to the final list of java sources, use [com.android.build.api.variant.Sources]
     *
     * Every entry is a ConfigurableFileTree instance to enable incremental java compilation.
     */
    private fun ComponentCreationConfig.defaultJavaSources(lateAdditionsDelegate: SourceDirectoriesImpl): List<DirectoryEntry> {
        // Build the list of source folders.
        val sourceSets = mutableListOf<DirectoryEntry>()

        // First the actual source folders.
        sourceSets.addAll(
            flattenSourceProviders(lateAdditionsDelegate) { sourceSet -> sourceSet.java }
        )

        // for the other, there's no duplicate so no issue.
        if (buildConfigCreationConfig?.buildConfigType == BuildConfigType.JAVA_SOURCE) {
            sourceSets.add(
                TaskProviderBasedDirectoryEntryImpl(
                    "generated_build_config",
                    artifacts.get(InternalArtifactType.GENERATED_BUILD_CONFIG_JAVA),
                )
            )
        }
        if (this is ConsumableCreationConfig && buildFeatures.aidl) {
            sourceSets.add(
                TaskProviderBasedDirectoryEntryImpl(
                    "generated_aidl",
                    artifacts.get(InternalArtifactType.AIDL_SOURCE_OUTPUT_DIR),
                )
            )
        }
        if (buildFeatures.dataBinding || buildFeatures.viewBinding) {
            if (this !is UnitTestCreationConfig) {
                sourceSets.add(
                    TaskProviderBasedDirectoryEntryImpl(
                        "databinding_generated",
                        artifacts.get(InternalArtifactType.DATA_BINDING_BASE_CLASS_SOURCE_OUT),
                        )
                )
            }
        }
        if (buildFeatures.mlModelBinding) {
            sourceSets.add(
                TaskProviderBasedDirectoryEntryImpl(
                    name = "mlModel_generated",
                    directoryProvider = artifacts.get(InternalArtifactType.ML_SOURCE_OUT),
                )
            )
        }
        return sourceSets
    }

    private fun ComponentCreationConfig.defaultResSources(lateAdditionsDelegate: SourceDirectoriesImpl): List<DirectoryEntries> {
        val sourceDirectories = mutableListOf<DirectoryEntries>()

        sourceDirectories.addAll(
            getSourceList(lateAdditionsDelegate) { sourceProvider -> sourceProvider.res }
        )

        val generatedFolders = mutableListOf<DirectoryEntry>()
        if (buildFeatures.renderScript) {
            generatedFolders.add(
                TaskProviderBasedDirectoryEntryImpl(
                    name = "renderscript_generated_res",
                    directoryProvider = artifacts.get(InternalArtifactType.RENDERSCRIPT_GENERATED_RES),
                )
            )
        }

        if (buildFeatures.resValues) {
            generatedFolders.add(
                TaskProviderBasedDirectoryEntryImpl(
                    name = "generated_res",
                    directoryProvider = artifacts.get(InternalArtifactType.GENERATED_RES),
                )
            )
        }

        sourceDirectories.add(DirectoryEntries("generated", generatedFolders))

        return Collections.unmodifiableList(sourceDirectories)
    }

    private fun defaultAssetsSources(lateAdditionsDelegate: SourceDirectoriesImpl): List<DirectoryEntries> =
        getSourceList(lateAdditionsDelegate) { sourceProvider -> sourceProvider.assets }

    private fun getSourceList(
        lateAdditionsDelegate: SourceDirectoriesImpl,
        action: (sourceProvider: DefaultAndroidSourceSet) -> AndroidSourceDirectorySet
    ): List<DirectoryEntries> {
        // Variant sources are added independently later so that they can be added to the model
        return variantSources.getSortedSourceProviders(false).map { sourceProvider ->
            sourceProvider as DefaultAndroidSourceSet
            val androidSourceDirectorySet =
                action(sourceProvider) as DefaultAndroidSourceDirectorySet
            androidSourceDirectorySet.addLateAdditionDelegate(lateAdditionsDelegate)
            DirectoryEntries(
                sourceProvider.name,
                androidSourceDirectorySet.srcDirs.map { directory ->
                        FileBasedDirectoryEntryImpl(
                            sourceProvider.name,
                            directory,
                        )
                    }.toMutableList()
            )
        }
    }
}
