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
import com.android.build.api.component.impl.features.InstrumentationCreationConfigImpl
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.Instrumentation
import com.android.build.api.variant.InternalSources
import com.android.build.api.variant.JavaCompilation
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.SourceDirectories
import com.android.build.api.variant.impl.FileBasedDirectoryEntryImpl
import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.api.variant.impl.KotlinMultiplatformAndroidCompilation
import com.android.build.api.variant.impl.LayeredSourceDirectoriesImpl
import com.android.build.api.variant.impl.SourceType
import com.android.build.api.variant.impl.SourcesImpl
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.component.KmpComponentCreationConfig
import com.android.build.gradle.internal.component.features.AndroidResourcesCreationConfig
import com.android.build.gradle.internal.component.features.AssetsCreationConfig
import com.android.build.gradle.internal.component.features.BuildConfigCreationConfig
import com.android.build.gradle.internal.component.features.ManifestPlaceholdersCreationConfig
import com.android.build.gradle.internal.component.features.ResValuesCreationConfig
import com.android.build.gradle.internal.component.legacy.ModelV1LegacySupport
import com.android.build.gradle.internal.component.legacy.OldVariantApiLegacySupport
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
    override val androidKotlinCompilation: KotlinMultiplatformAndroidCompilation,
    manifestFile: File
): KmpComponentCreationConfig, ComponentIdentity by dslInfo.componentIdentity {

    final override val dirName: String
        get() = paths.dirName
    final override val baseName: String
        get() = paths.baseName
    override val componentType: ComponentType
        get() =  dslInfo.componentType
    override val description: String = "Kotlin multiplatform android plugin"

    override fun computeTaskName(prefix: String, suffix: String): String =
        prefix.appendCapitalized(name, suffix)

    override fun computeTaskName(prefix: String): String = prefix.appendCapitalized(name)

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

    val instrumentation: Instrumentation
        get() = instrumentationCreationConfig.instrumentation
    val compileConfiguration: Configuration
        get() = variantDependencies.compileClasspath
    val runtimeConfiguration: Configuration
        get() = variantDependencies.runtimeClasspath

    // DSL delegates

    override val applicationId: Provider<String>
        get() = dslInfo.applicationId
    override val namespace: Provider<String>
        get() = dslInfo.namespace
    override val minSdk: AndroidVersion
        get() = dslInfo.minSdkVersion

    override val sources = KmpSourcesImpl(internalServices, manifestFile)

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

    // Unsupported features

    override val assetsCreationConfig: AssetsCreationConfig? = null
    override val androidResourcesCreationConfig: AndroidResourcesCreationConfig? = null
    override val resValuesCreationConfig: ResValuesCreationConfig? = null
    override val buildConfigCreationConfig: BuildConfigCreationConfig? = null
    override val manifestPlaceholdersCreationConfig: ManifestPlaceholdersCreationConfig? = null
    override val modelV1LegacySupport: ModelV1LegacySupport? = null
    override val oldVariantApiLegacySupport: OldVariantApiLegacySupport? = null

    override val javaCompilation: JavaCompilation
        get() = throw IllegalAccessException("The kotlin multiplatform android plugin doesn't" +
                " configure the jvm options for compilation. To setup compilation options, use" +
                " the kotlin specific options.")

    val annotationProcessorConfiguration: Configuration
        get() = throw IllegalAccessException("The kotlin multiplatform android plugin doesn't" +
                " configure annotation processors. To configure annotation processors, use" +
                " the kapt options.")

    class KmpSourcesImpl(
        variantServices: VariantServices,
        manifestFile: File
    ): InternalSources {

        override val java = FlatSourceDirectoriesImpl(
            SourceType.JAVA.folder,
            variantServices,
            PatternSet().also { filter ->
                filter.include("**/*.java")
            },
        )

        override val kotlin = FlatSourceDirectoriesImpl(
            SourceType.KOTLIN.folder,
            variantServices,
            PatternSet().also { filter ->
                filter.include("**/*.kt", "**/*.kts")
            },
        )

        override val resources = FlatSourceDirectoriesImpl(
            SourceType.JAVA_RESOURCES.folder,
            variantServices,
            PatternSet().also { filter ->
                filter.exclude("**/*.java", "**/*.kt")
            },
        )

        override fun resources(action: (FlatSourceDirectoriesImpl) -> Unit) {
            action(resources)
        }

        override fun kotlin(action: (FlatSourceDirectoriesImpl) -> Unit) {
            action(kotlin)
        }

        override fun java(action: (FlatSourceDirectoriesImpl) -> Unit) {
            action(java)
        }

        override val manifestFile: Provider<File> = variantServices.provider {
            manifestFile
        }

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

        override val res = null
        override val assets = null
        override val jniLibs = null
        override val shaders = null
        override val mlModels = null
        override val aidl = null
        override val renderscript = null
        override val baselineProfiles = null
        override val manifestOverlayFiles = variantServices.provider { emptyList<File>() }

        override fun aidl(action: (FlatSourceDirectoriesImpl) -> Unit) {}
        override fun renderscript(action: (FlatSourceDirectoriesImpl) -> Unit) {}
        override fun res(action: (LayeredSourceDirectoriesImpl) -> Unit) {}
        override fun assets(action: (LayeredSourceDirectoriesImpl) -> Unit) {}
        override fun jniLibs(action: (LayeredSourceDirectoriesImpl) -> Unit) {}
        override fun shaders(action: (LayeredSourceDirectoriesImpl) -> Unit) {}
        override fun mlModels(action: (LayeredSourceDirectoriesImpl) -> Unit) {}
        override fun baselineProfiles(action: (FlatSourceDirectoriesImpl) -> Unit) {}

        override val artProfile: Provider<File>? = null
        override val sourceProviderNames: List<String> = emptyList()
        override val multiFlavorSourceProvider: DefaultAndroidSourceSet? = null
        override val variantSourceProvider: DefaultAndroidSourceSet? = null
    }

    open fun syncAndroidAndKmpClasspathAndSources() {
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
        androidKotlinCompilation.allKotlinSourceSets.forEach { sourceSet ->
            sources.kotlin { kotlin ->
                sourceSet.kotlin.srcDirs.forEach { srcDir ->
                    kotlin.addSource(
                        FileBasedDirectoryEntryImpl(
                            name = "Kotlin",
                            directory = srcDir
                        )
                    )
                }
            }

            sources.resources { resources ->
                sourceSet.resources.srcDirs.forEach { srcDir ->
                    resources.addSource(
                        FileBasedDirectoryEntryImpl(
                            name = "Java resources",
                            directory = srcDir,
                            filter = PatternSet().exclude("**/*.java", "**/*.kt"),
                        )
                    )
                }
            }
        }
    }
}
