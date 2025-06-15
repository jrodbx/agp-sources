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

import com.android.SdkConstants
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.impl.features.AndroidResourcesCreationConfigImpl
import com.android.build.api.component.impl.features.InstrumentationCreationConfigImpl
import com.android.build.api.component.impl.features.PrivacySandboxCreationConfigImpl
import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilation
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.Component
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.Instrumentation
import com.android.build.api.variant.InternalSources
import com.android.build.api.variant.JavaCompilation
import com.android.build.api.variant.ManifestFiles
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.SourceDirectories
import com.android.build.api.variant.impl.AndroidResourcesImpl
import com.android.build.api.variant.impl.DirectoryEntries
import com.android.build.api.variant.impl.FileBasedDirectoryEntryImpl
import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.api.variant.impl.KotlinMultiplatformFlatSourceDirectoriesImpl
import com.android.build.api.variant.impl.LayeredSourceDirectoriesImpl
import com.android.build.api.variant.impl.ManifestFilesImpl
import com.android.build.api.variant.impl.ProviderBasedDirectoryEntryImpl
import com.android.build.api.variant.impl.SourceType
import com.android.build.api.variant.impl.SourcesImpl
import com.android.build.api.variant.impl.initializeAaptOptionsFromDsl
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.component.KmpComponentCreationConfig
import com.android.build.gradle.internal.component.features.AndroidResourcesCreationConfig
import com.android.build.gradle.internal.component.features.BuildConfigCreationConfig
import com.android.build.gradle.internal.component.features.ManifestPlaceholdersCreationConfig
import com.android.build.gradle.internal.component.features.PrivacySandboxCreationConfig
import com.android.build.gradle.internal.component.features.ResValuesCreationConfig
import com.android.build.gradle.internal.component.legacy.ModelV1LegacySupport
import com.android.build.gradle.internal.component.legacy.OldVariantApiLegacySupport
import com.android.build.gradle.internal.core.MergedJavaCompileOptions
import com.android.build.gradle.internal.core.ProductFlavor
import com.android.build.gradle.internal.core.dsl.KmpComponentDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dependency.getProvidedClasspath
import com.android.build.gradle.internal.dsl.AbstractPublishing
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.ComponentPublishingInfo
import com.android.build.gradle.internal.publishing.VariantPublishingInfo
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.getDirectories
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.builder.core.ComponentType
import com.android.utils.appendCapitalized
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.util.PatternSet
import java.io.File
import java.util.Locale
import java.util.function.Predicate

abstract class KmpComponentImpl<DslInfoT: KmpComponentDslInfo>(
    val dslInfo: DslInfoT,
    protected val internalServices: VariantServices,
    override val buildFeatures: BuildFeatureValues,
    override val variantDependencies: VariantDependencies,
    override val paths: VariantPathHelper,
    override val artifacts: ArtifactsImpl,
    override val taskContainer: MutableTaskContainer,
    override val services: TaskCreationServices,
    override val global: GlobalTaskCreationConfig,
    final override val androidKotlinCompilation: KotlinMultiplatformAndroidCompilation,
    manifestFile: File
): Component, KmpComponentCreationConfig, ComponentIdentity by dslInfo.componentIdentity {

    final override val withJava: Boolean
        get() = dslInfo.withJava

    final override val dirName: String
        get() = paths.dirName
    final override val baseName: String
        get() = paths.baseName
    override val componentType: ComponentType
        get() =  dslInfo.componentType
    override val description: String = "Kotlin multiplatform android plugin"

    override fun computeTaskNameInternal(prefix: String, suffix: String): String =
        prefix.appendCapitalized(name, suffix)

    override fun computeTaskNameInternal(prefix: String): String = prefix.appendCapitalized(name)

    override fun getArtifactName(name: String): String = name

    override val productFlavorList: List<ProductFlavor> = emptyList()
    override val debuggable = false

    // public variant api

    override val instrumentationCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        InstrumentationCreationConfigImpl(
            this,
            internalServices
        )
    }

    override val instrumentation: Instrumentation
        get() = instrumentationCreationConfig.instrumentation
    override val compileConfiguration: Configuration
        get() = variantDependencies.compileClasspath
    override val runtimeConfiguration: Configuration
        get() = variantDependencies.runtimeClasspath

    // DSL delegates

    override val applicationId: Provider<String>
        get() = dslInfo.applicationId
    override val namespace: Provider<String>
        get() = dslInfo.namespace
    override val minSdk: AndroidVersion
        get() = dslInfo.minSdkVersion

    final override val useBuiltInKotlinSupport = false
    final override val useBuiltInKaptSupport = false

    override val sources = KmpSourcesImpl(
        dslInfo,
        internalServices,
        manifestFile,
        androidKotlinCompilation,
        buildFeatures
    )

    final override fun getJavaClasspath(
        configType: AndroidArtifacts.ConsumedConfigType,
        classesType: AndroidArtifacts.ArtifactType,
        generatedBytecodeKey: Any?
    ): FileCollection = getJavaClasspath(
        this, configType, classesType, generatedBytecodeKey
    )

    override val compileClasspath: FileCollection by lazy(LazyThreadSafetyMode.NONE) {
        getJavaClasspath(
            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
            AndroidArtifacts.ArtifactType.CLASSES_JAR,
            generatedBytecodeKey = null
        )
    }
    override val providedOnlyClasspath: FileCollection by lazy(LazyThreadSafetyMode.NONE) {
        getProvidedClasspath(
            compileClasspath = variantDependencies.getArtifactCollection(
                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                AndroidArtifacts.ArtifactScope.ALL,
                AndroidArtifacts.ArtifactType.CLASSES_JAR
            ),
            runtimeClasspath = variantDependencies.getArtifactCollection(
                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                AndroidArtifacts.ArtifactScope.ALL,
                AndroidArtifacts.ArtifactType.CLASSES_JAR
            )
        )
    }

    override fun computeLocalFileDependencies(filePredicate: Predicate<File>): FileCollection =
        variantDependencies.computeLocalFileDependencies(
            internalServices,
            filePredicate
        )

    override fun computeLocalPackagedJars(): FileCollection =
        computeLocalFileDependencies { file ->
            file
                .name
                .lowercase(Locale.US)
                .endsWith(SdkConstants.DOT_JAR)
        }

    override fun publishBuildArtifacts() {
        com.android.build.gradle.internal.scope.publishBuildArtifacts(
            creationConfig = this,
            publishInfo = VariantPublishingInfo(
                components = listOf(
                    ComponentPublishingInfo(
                        componentName = name,
                        type = AbstractPublishing.Type.AAR
                    )
                )
            )
        )
    }

    override fun computeTaskName(action: String, subject: String): String =
        computeTaskName(name, action, subject)

    override val lifecycleTasks = LifecycleTasksImpl()

    override val androidResourcesCreationConfig: AndroidResourcesCreationConfig? by lazy(LazyThreadSafetyMode.NONE) {
        if (buildFeatures.androidResources) {
            AndroidResourcesCreationConfigImpl(
                this,
                dslInfo,
                dslInfo.androidResourcesDsl!!,
                internalServices,
            )
        } else {
            null
        }
    }

    override val androidResources: AndroidResourcesImpl? by lazy(LazyThreadSafetyMode.NONE) {
        if (buildFeatures.androidResources) {
            initializeAaptOptionsFromDsl(
                dslInfo.androidResourcesDsl!!.androidResources,
                buildFeatures,
                internalServices,
            )
        } else {
            null
        }
    }

    // Unsupported features
    override val resValuesCreationConfig: ResValuesCreationConfig? = null
    override val buildConfigCreationConfig: BuildConfigCreationConfig? = null
    override val manifestPlaceholdersCreationConfig: ManifestPlaceholdersCreationConfig? = null
    override val modelV1LegacySupport: ModelV1LegacySupport? = null
    override val oldVariantApiLegacySupport: OldVariantApiLegacySupport? = null

    override val javaCompilation: JavaCompilation
        get() = if (dslInfo.withJava) {
            JavaCompilationImpl(
                MergedJavaCompileOptions(),
                buildFeatures.dataBinding,
                internalServices
            )
        } else {
            throw IllegalAccessException("The kotlin multiplatform android plugin doesn't" +
                    " configure the jvm options for compilation. To setup compilation options, use" +
                    " the kotlin specific options.")
        }

    override val annotationProcessorConfiguration: Configuration
        get() = throw IllegalAccessException("The kotlin multiplatform android plugin doesn't" +
                " configure annotation processors. To configure annotation processors, use" +
                " the kapt options.")

    class KmpSourcesImpl(
        dslInfo: KmpComponentDslInfo,
        variantServices: VariantServices,
        manifestFile: File,
        compilation: KotlinMultiplatformAndroidCompilation,
        buildFeatures: BuildFeatureValues
    ): InternalSources {

        override val java = if (dslInfo.withJava) {
            KotlinMultiplatformFlatSourceDirectoriesImpl(
                name = SourceType.JAVA.folder,
                variantServices = variantServices,
                variantDslFilters = PatternSet().also { filter ->
                    filter.include("**/*.java")
                },
                compilation = compilation
            )
        } else {
            null
        }

        override val kotlin = KotlinMultiplatformFlatSourceDirectoriesImpl(
            name = SourceType.KOTLIN.folder,
            variantServices = variantServices,
            variantDslFilters = PatternSet().also { filter ->
                filter.include("**/*.kt", "**/*.kts")
            },
            compilation = compilation
        )

        override val resources = KotlinMultiplatformFlatSourceDirectoriesImpl(
            name = SourceType.JAVA_RESOURCES.folder,
            variantServices = variantServices,
            variantDslFilters = PatternSet().also { filter ->
                filter.exclude("**/*.java", "**/*.kt")
            },
            compilation = compilation
        )

        override val res = if (buildFeatures.androidResources) {
            LayeredSourceDirectoriesImpl(
                _name = SourceType.RES.folder,
                variantServices = variantServices,
                variantDslFilters = PatternSet(),
            )
        } else null

        override val assets = if (buildFeatures.androidResources) {
            LayeredSourceDirectoriesImpl(
                _name = SourceType.ASSETS.folder,
                variantServices = variantServices,
                variantDslFilters = PatternSet(),
            )
        } else null

        override fun resources(action: (FlatSourceDirectoriesImpl) -> Unit) {
            action(resources)
        }

        override fun kotlin(action: (FlatSourceDirectoriesImpl) -> Unit) {
            action(kotlin)
        }

        override fun java(action: (FlatSourceDirectoriesImpl) -> Unit) {
            java?.let(action)
        }

        override fun res(action: (LayeredSourceDirectoriesImpl) -> Unit) {
            res?.let(action)
        }

        override fun assets(action: (LayeredSourceDirectoriesImpl) -> Unit) {
            assets?.let(action)
        }

        override val manifestFile = manifestFile

        private val extras by lazy(LazyThreadSafetyMode.NONE) {
            variantServices.domainObjectContainer(
                FlatSourceDirectoriesImpl::class.java,
                SourcesImpl.SourceProviderFactory(
                    variantServices,
                ),
            )
        }

        override fun getByName(name: String): SourceDirectories.Flat = extras.maybeCreate(name)

        // Not supported
        override val jniLibs = null
        override val shaders = null
        override val mlModels = null
        override val aidl = null
        override val renderscript = null
        override val baselineProfiles = null
        override val manifestOverlayFiles = variantServices.provider { emptyList<File>() }

        override fun aidl(action: (FlatSourceDirectoriesImpl) -> Unit) {}
        override fun renderscript(action: (FlatSourceDirectoriesImpl) -> Unit) {}
        override fun jniLibs(action: (LayeredSourceDirectoriesImpl) -> Unit) {}
        override fun shaders(action: (LayeredSourceDirectoriesImpl) -> Unit) {}
        override fun mlModels(action: (LayeredSourceDirectoriesImpl) -> Unit) {}
        override fun baselineProfiles(action: (FlatSourceDirectoriesImpl) -> Unit) {}

        override val artProfile: File? = null
        override val sourceProviderNames: List<String> = emptyList()
        override val multiFlavorSourceProvider: DefaultAndroidSourceSet? = null
        override val variantSourceProvider: DefaultAndroidSourceSet? = null
        override val manifests: ManifestFiles =
                ManifestFilesImpl(
                        variantServices
                ).also { sourceFilesImpl ->
                    sourceFilesImpl.addSourceFile(manifestFile)
                }
    }

    open fun syncAndroidAndKmpClasspathAndSources() {
        val projectDir = services.projectInfo.projectDirectory

        artifacts
            .forScope(ScopedArtifacts.Scope.PROJECT)
            .getScopedArtifactsContainer(ScopedArtifact.CLASSES)
            .initialScopedContent
            .from(androidKotlinCompilation.output.classesDirs)

        androidKotlinCompilation.compileDependencyFiles = services.fileCollection(
            global.bootClasspath,
            getJavaClasspath(
                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                AndroidArtifacts.ArtifactType.CLASSES_JAR
            )
        )

        // Include all kotlin sourceSets (the ones added directly to the compilation and the ones
        // that added transitively through a dependsOn dependency).
        sources.kotlin.addStaticSources(
            services.provider {
                androidKotlinCompilation.allKotlinSourceSets.flatMap { sourceSet ->
                    sourceSet.kotlin.srcDirs.map { srcDir ->
                        FileBasedDirectoryEntryImpl(
                            name = "Kotlin",
                            directory = srcDir
                        )
                    }
                }
            }
        )

        // Include all kotlin sourceSets (the ones added directly to the compilation and the ones
        // that added transitively through a dependsOn dependency).
        sources.java?.addStaticSources(
            services.provider {
                androidKotlinCompilation.allKotlinSourceSets.flatMap { sourceSet ->
                    sourceSet.kotlin.srcDirs.map { srcDir ->
                        FileBasedDirectoryEntryImpl(
                            name = "Java",
                            // Java sources are currently located at the sourceSetDir/java
                            // (e.g. src/androidMain/java)
                            directory = File(srcDir.parentFile, "java")
                        )
                    }
                }
            }
        )

        sources.resources.addStaticSources(
            services.provider {
                androidKotlinCompilation.allKotlinSourceSets.map { sourceSet ->
                    ProviderBasedDirectoryEntryImpl(
                        name = "Java resources",
                        elements = sourceSet.resources.sourceDirectories.getDirectories(projectDir),
                        filter = PatternSet().exclude("**/*.java", "**/*.kt"),
                    )
                }
            }
        )

        sources.res?.addStaticSources(
            services.provider {
                // only add androidMain source set resources instead of for all source sets
                // in the compilation (e.g. commonMain and any intermediate source set)
                androidKotlinCompilation.defaultSourceSet.let { sourceSet ->
                    DirectoryEntries(
                        sourceSet.name,
                        sourceSet.resources.srcDirs.map { srcDir ->
                            FileBasedDirectoryEntryImpl(
                                name = sourceSet.name,
                                // Android resources are located under androidMain/res
                                directory = File(srcDir.parentFile, SourceType.RES.folder)
                            )
                        }.toMutableList()
                    )
                }
            }
        )

        sources.assets?.addStaticSources(
            services.provider {
                androidKotlinCompilation.defaultSourceSet.let { sourceSet ->
                    DirectoryEntries(
                        sourceSet.name,
                        sourceSet.resources.srcDirs.map { srcDir ->
                            FileBasedDirectoryEntryImpl(
                                name = sourceSet.name,
                                directory = File(srcDir.parentFile, SourceType.ASSETS.folder)
                            )
                        }.toMutableList()
                    )
                }
            }
        )
    }

    override val privacySandboxCreationConfig: PrivacySandboxCreationConfig?
        get() = if(dslInfo.privacySandboxDsl.enable) {
            PrivacySandboxCreationConfigImpl()
        } else {
            null
        }
}
