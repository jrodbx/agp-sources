/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.api.variant.impl

import com.android.build.api.component.impl.DefaultSourcesProvider
import com.android.build.api.variant.InternalSources
import com.android.build.api.variant.SourceDirectories
import com.android.build.api.variant.Sources
import com.android.build.gradle.api.AndroidSourceDirectorySet
import com.android.build.gradle.internal.api.DefaultAndroidSourceDirectorySet
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectFactory
import com.android.build.gradle.internal.services.VariantServices
import org.gradle.api.provider.Provider
import java.io.File

/**
 * Implementation of [Sources] for a particular source type like java, kotlin, etc...
 *
 * @param defaultSourceProvider function to provide initial content of the sources for a specific
 * [SourceType]. These are all the basic folders set for main. buildTypes and flavors including
 * those set through the DSL settings.
 * @param variantServices the variant's [VariantServices]
 * @param variantSourceProvider optional variant specific [DefaultAndroidSourceSet] if there is one, null
 * otherwise (if the application does not have product flavor, there won't be one).
 */
class SourcesImpl(
    private val defaultSourceProvider: DefaultSourcesProvider,
    private val variantServices: VariantServices,
    override val multiFlavorSourceProvider: DefaultAndroidSourceSet?,
    override val variantSourceProvider: DefaultAndroidSourceSet?,
): InternalSources {

    override val java =
        FlatSourceDirectoriesImpl(
            SourceType.JAVA.folder,
            variantServices,
            variantSourceProvider?.java?.filter
        ).also { sourceDirectoriesImpl ->

            defaultSourceProvider.getJava(sourceDirectoriesImpl).run {
                sourceDirectoriesImpl.addStaticOrGeneratedSources(this)
            }
            updateSourceDirectories(sourceDirectoriesImpl, variantSourceProvider?.java)
        }

    override val kotlin =
        FlatSourceDirectoriesImpl(
            SourceType.KOTLIN.folder,
            variantServices,
            null,
        ).also { sourceDirectoriesImpl ->

            defaultSourceProvider.getKotlin(sourceDirectoriesImpl).run {
                sourceDirectoriesImpl.addStaticOrGeneratedSources(this)
            }
            updateSourceDirectories(
                sourceDirectoriesImpl,
                variantSourceProvider?.kotlin as DefaultAndroidSourceDirectorySet?)
        }

    override val baselineProfiles =
        FlatSourceDirectoriesImpl(
            SourceType.BASELINE_PROFILES.folder,
            variantServices,
            null
        ).also { sourceDirectoriesImpl ->

            defaultSourceProvider.getBaselineProfiles(sourceDirectoriesImpl).run {
                sourceDirectoriesImpl.addStaticOrGeneratedSources(this)
            }
            updateSourceDirectories(
                sourceDirectoriesImpl,
                variantSourceProvider?.baselineProfiles as DefaultAndroidSourceDirectorySet?
            )
        }

    override val manifests =
            ManifestFilesImpl(
                    variantServices
            ).also { sourceFilesImpl ->
                sourceFilesImpl.addSourceFile(defaultSourceProvider.mainManifestFile)
                defaultSourceProvider.manifestOverlayFiles.run {
                    forEach {
                        sourceFilesImpl.addSourceFile(it)
                    }
                }
            }

    override val res =
        ResSourceDirectoriesImpl(
            SourceType.RES.folder,
            variantServices,
            variantSourceProvider?.res?.filter
        ).let { sourceDirectoriesImpl ->
            val defaultResDirectories = defaultSourceProvider.getRes(sourceDirectoriesImpl) ?: return@let null
            defaultResDirectories.run {
                forEach {
                    sourceDirectoriesImpl.addStaticSources(it)
                }
            }
            updateSourceDirectories(sourceDirectoriesImpl, variantSourceProvider?.res)
            return@let sourceDirectoriesImpl
        }

    override val resources =
        FlatSourceDirectoriesImpl(
            SourceType.JAVA_RESOURCES.folder,
            variantServices,
            variantSourceProvider?.resources?.filter,
        ).also { sourceDirectoriesImpl ->

            defaultSourceProvider.getResources(sourceDirectoriesImpl).run {
                sourceDirectoriesImpl.addStaticOrGeneratedSources(this)
            }
            updateSourceDirectories(sourceDirectoriesImpl, variantSourceProvider?.resources)
        }

    override val assets =
        LayeredSourceDirectoriesImpl(
            SourceType.ASSETS.folder,
            variantServices,
            variantSourceProvider?.assets?.filter
        ).also { sourceDirectoriesImpl ->

            defaultSourceProvider.getAssets(sourceDirectoriesImpl).run {
                forEach {
                    sourceDirectoriesImpl.addStaticSources(it)
                }
            }
            updateSourceDirectories(sourceDirectoriesImpl, variantSourceProvider?.assets)
        }

    override val jniLibs =
        LayeredSourceDirectoriesImpl(
            SourceType.JNI_LIBS.folder,
            variantServices,
            variantSourceProvider?.jniLibs?.filter
        ).also { sourceDirectoriesImpl ->

            defaultSourceProvider.getJniLibs(sourceDirectoriesImpl).run {
                forEach {
                    sourceDirectoriesImpl.addStaticSources(it)
                }
            }
            updateSourceDirectories(sourceDirectoriesImpl, variantSourceProvider?.jniLibs)
        }

    override val shaders =
        LayeredSourceDirectoriesImpl(
                SourceType.SHADERS.folder,
                variantServices,
                variantSourceProvider?.shaders?.filter
            ).let { sourceDirectoriesImpl ->
                val listOfDirectoryEntries = defaultSourceProvider.getShaders(sourceDirectoriesImpl) ?: return@let null

                listOfDirectoryEntries.run {
                    forEach {
                        sourceDirectoriesImpl.addStaticSources(it)
                    }
                }
                updateSourceDirectories(sourceDirectoriesImpl, variantSourceProvider?.shaders)
                return@let sourceDirectoriesImpl
            }

    override val mlModels =
        LayeredSourceDirectoriesImpl(
            SourceType.ML_MODELS.folder,
            variantServices,
            variantSourceProvider?.mlModels?.filter
        ).let { sourceDirectoriesImpl ->
            val defaultMlModelsDirectories = defaultSourceProvider.getMlModels(sourceDirectoriesImpl) ?: return@let null
            defaultMlModelsDirectories.run {
                forEach {
                    sourceDirectoriesImpl.addStaticSources(it)
                }
            }
            updateSourceDirectories(sourceDirectoriesImpl, variantSourceProvider?.mlModels)
            return@let sourceDirectoriesImpl
        }

    override val aidl by lazy(LazyThreadSafetyMode.NONE) {
        FlatSourceDirectoriesImpl(
                SourceType.AIDL.folder,
                variantServices,
                variantSourceProvider?.aidl?.filter
        ).let { sourceDirectoriesImpl ->
            val defaultAidlDirectories =
                    defaultSourceProvider.getAidl(sourceDirectoriesImpl) ?: return@let null
            sourceDirectoriesImpl.addStaticOrGeneratedSources(defaultAidlDirectories)
            updateSourceDirectories(sourceDirectoriesImpl, variantSourceProvider?.aidl)
            return@let sourceDirectoriesImpl
        }
    }


    override val renderscript by lazy(LazyThreadSafetyMode.NONE) {
        FlatSourceDirectoriesImpl(
                SourceType.RENDERSCRIPT.folder,
                variantServices,
                variantSourceProvider?.renderscript?.filter
        ).let { sourceDirectoriesImpl ->
            val defaultRenderscriptDirectories =
                    defaultSourceProvider.getRenderscript(sourceDirectoriesImpl) ?: return@let null

            sourceDirectoriesImpl.addStaticOrGeneratedSources(defaultRenderscriptDirectories)
            updateSourceDirectories(sourceDirectoriesImpl, variantSourceProvider?.renderscript)
            return@let sourceDirectoriesImpl
        }
    }

    internal val extras: NamedDomainObjectContainer<FlatSourceDirectoriesImpl> by lazy(LazyThreadSafetyMode.NONE) {
        variantServices.domainObjectContainer(
            FlatSourceDirectoriesImpl::class.java,
            SourceProviderFactory(
                variantServices,
            ),
        )
    }

    override fun getByName(name: String): SourceDirectories.Flat = extras.maybeCreate(name)

    override fun java(action: (FlatSourceDirectoriesImpl) -> Unit) { action(java) }
    override fun kotlin(action: (FlatSourceDirectoriesImpl) -> Unit) { action(kotlin) }
    override fun resources(action: (FlatSourceDirectoriesImpl) -> Unit) { action(resources) }
    override fun aidl(action: (FlatSourceDirectoriesImpl) -> Unit) { aidl?.let(action) }
    override fun renderscript(action: (FlatSourceDirectoriesImpl) -> Unit) {
        renderscript?.let(action)
    }
    override fun baselineProfiles(action: (FlatSourceDirectoriesImpl) -> Unit) {
        action(baselineProfiles)
    }
    override fun res(action: (LayeredSourceDirectoriesImpl) -> Unit) { res?.let(action) }
    override fun assets(action: (LayeredSourceDirectoriesImpl) -> Unit) { action(assets) }
    override fun jniLibs(action: (LayeredSourceDirectoriesImpl) -> Unit) { action(jniLibs) }
    override fun shaders(action: (LayeredSourceDirectoriesImpl) -> Unit) { shaders?.let(action) }
    override fun mlModels(action: (LayeredSourceDirectoriesImpl) -> Unit) { mlModels?.let(action) }

    override val artProfile = defaultSourceProvider.artProfile

    override val manifestFile = defaultSourceProvider.mainManifestFile

    override val manifestOverlayFiles: Provider<List<File>> = manifests.all.map {
        // `all` is ordered from most prioritized to less prioritizes (main manifest)
        // need to remove main manifest and reverse order from less to the most prioritized
        files -> files.dropLast(1).reversed().map { it.asFile }
    }

    override val sourceProviderNames: List<String>
        get() = defaultSourceProvider.sourceProvidersNames

    class SourceProviderFactory(
        private val variantServices: VariantServices,
    ): NamedDomainObjectFactory<FlatSourceDirectoriesImpl> {

        override fun create(name: String): FlatSourceDirectoriesImpl =
            FlatSourceDirectoriesImpl(
                _name = name,
                variantServices = variantServices,
                variantDslFilters = null
            )
    }

    /**
     * Update SourceDirectories with the original variant specific source set from
     * [com.android.build.gradle.internal.core.VariantSources] since the variant
     * specific folders are owned by this abstraction (so users can add it if needed).
     * TODO, make the VariantSources unavailable to other components in
     * AGP as they should all use this [SourcesImpl] from now on.
     */
    private fun updateSourceDirectories(
        target: SourceDirectoriesImpl,
        sourceSet: AndroidSourceDirectorySet?,
    ) {
        if (sourceSet != null) {
            (sourceSet as DefaultAndroidSourceDirectorySet).addLateAdditionDelegate(target)
            for (srcDir in sourceSet.srcDirs) {
                target.addSource(
                    FileBasedDirectoryEntryImpl(
                        name = "variant",
                        directory = srcDir,
                        filter = sourceSet.filter,
                        // since it was part of the original set of sources for the module, we
                        // must add it back to the model as it is expecting to have variant sources.
                        shouldBeAddedToIdeModel = true
                    )
                )
            }
        }
    }
}
