/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.attributes.AgpVersionAttr
import com.android.build.api.attributes.BuildTypeAttr
import com.android.build.api.attributes.BuildTypeAttr.Companion.ATTRIBUTE
import com.android.build.api.attributes.ProductFlavorAttr
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.coverage.JacocoConfigurations
import com.android.build.gradle.internal.coverage.JacocoOptions
import com.android.build.gradle.internal.dependency.AarResourcesCompilerTransform
import com.android.build.gradle.internal.dependency.AarToClassTransform
import com.android.build.gradle.internal.dependency.AarTransform
import com.android.build.gradle.internal.dependency.AgpVersionCompatibilityRule
import com.android.build.gradle.internal.dependency.AlternateCompatibilityRule
import com.android.build.gradle.internal.dependency.AndroidXDependencyCheck
import com.android.build.gradle.internal.dependency.AndroidXDependencySubstitution.replaceOldSupportLibraries
import com.android.build.gradle.internal.dependency.AsmClassesTransform.Companion.registerAsmTransformForComponent
import com.android.build.gradle.internal.dependency.ClassesDirToClassesTransform
import com.android.build.gradle.internal.dependency.CollectClassesTransform
import com.android.build.gradle.internal.dependency.CollectResourceSymbolsTransform
import com.android.build.gradle.internal.dependency.DexingRegistration
import com.android.build.gradle.internal.dependency.EnumerateClassesTransform
import com.android.build.gradle.internal.dependency.ExtractAarTransform
import com.android.build.gradle.internal.dependency.ExtractCompileSdkShimTransform
import com.android.build.gradle.internal.dependency.ExtractJniTransform
import com.android.build.gradle.internal.dependency.ExtractProGuardRulesTransform
import com.android.build.gradle.internal.dependency.ExtractSdkShimTransform
import com.android.build.gradle.internal.dependency.FilterShrinkerRulesTransform
import com.android.build.gradle.internal.dependency.GenericTransformParameters
import com.android.build.gradle.internal.dependency.IdentityTransform
import com.android.build.gradle.internal.dependency.JacocoTransform
import com.android.build.gradle.internal.dependency.JetifyTransform
import com.android.build.gradle.internal.dependency.LibrarySymbolTableTransform
import com.android.build.gradle.internal.dependency.MockableJarTransform
import com.android.build.gradle.internal.dependency.ModelArtifactCompatibilityRule.Companion.setUp
import com.android.build.gradle.internal.dependency.MultiVariantBuildTypeRule
import com.android.build.gradle.internal.dependency.MultiVariantProductFlavorRule
import com.android.build.gradle.internal.dependency.PlatformAttrTransform
import com.android.build.gradle.internal.dependency.RecalculateStackFramesTransform.Companion.registerGlobalRecalculateStackFramesTransform
import com.android.build.gradle.internal.dependency.RecalculateStackFramesTransform.Companion.registerRecalculateStackFramesTransformForComponent
import com.android.build.gradle.internal.dependency.ShrinkerVersion
import com.android.build.gradle.internal.dependency.registerDexingOutputSplitTransform
import com.android.build.gradle.internal.dsl.BaseFlavor
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ModulePropertyKey
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.packaging.getDefaultDebugKeystoreSigningConfig
import com.android.build.gradle.internal.publishing.AarOrJarTypeToConsume
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.res.namespaced.AutoNamespacePreProcessTransform
import com.android.build.gradle.internal.res.namespaced.AutoNamespaceTransform
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.signing.SigningConfigData
import com.android.build.gradle.internal.tasks.AsarToApksTransform
import com.android.build.gradle.internal.tasks.AsarTransform
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfig
import com.android.build.gradle.internal.utils.ATTR_ENABLE_CORE_LIBRARY_DESUGARING
import com.android.build.gradle.internal.utils.ATTR_LINT_MIN_SDK
import com.android.build.gradle.internal.utils.D8BackportedMethodsGenerator
import com.android.build.gradle.internal.utils.D8_DESUGAR_METHODS
import com.android.build.gradle.internal.utils.getDesugarLibConfigFiles
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.variant.VariantInputModel
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.options.SyncOptions
import com.android.builder.core.BuilderConstants
import com.android.builder.dexing.R8Version
import com.android.repository.Revision
import com.google.common.collect.Maps
import org.gradle.api.ActionConfiguration
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformSpec
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.provider.Provider
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import java.lang.Boolean.FALSE
import java.lang.Boolean.TRUE

/**
 * configures the dependencies for a set of variant inputs.
 */
class DependencyConfigurator(
    private val project: Project,
    private val projectServices: ProjectServices
) {
    fun configureDependencySubstitutions(): DependencyConfigurator {
        // If Jetifier is enabled, replace old support libraries with AndroidX.
        if (projectServices.projectOptions.get(BooleanOption.ENABLE_JETIFIER)) {
            replaceOldSupportLibraries(
                project,
                // Inline the property name for a slight memory improvement (so that the JVM doesn't
                // create a new string every time this code is executed, which could be many when
                // there are many subprojects).
                reasonToReplace = "android.enableJetifier=true")
        }
        return this
    }
    fun configureDependencyChecks(): DependencyConfigurator {
        val useAndroidX = projectServices.projectOptions.get(BooleanOption.USE_ANDROID_X)
        val enableJetifier = projectServices.projectOptions.get(BooleanOption.ENABLE_JETIFIER)

        if (!useAndroidX && !enableJetifier) {
            project.configurations.configureEach { configuration ->
                if (configuration.isCanBeResolved) {
                    configuration.incoming.afterResolve(
                        AndroidXDependencyCheck.AndroidXDisabledJetifierDisabled(
                            project, configuration.name, projectServices.issueReporter
                        )
                    )
                }
            }
        }

        return this
    }

    fun configureGeneralTransforms(
            namespacedAndroidResources: Boolean,
            aarOrJarTypeToConsume: AarOrJarTypeToConsume
    ): DependencyConfigurator {
        val dependencies: DependencyHandler = project.dependencies

        val projectOptions = projectServices.projectOptions

        // The aars/jars may need to be processed (e.g., jetified to AndroidX) before they can be
        // used
        val autoNamespaceDependencies =
            namespacedAndroidResources && projectOptions[BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES]
        val jetifiedAarOutputType = if (autoNamespaceDependencies) {
            AndroidArtifacts.ArtifactType.MAYBE_NON_NAMESPACED_PROCESSED_AAR
        } else {
            AndroidArtifacts.ArtifactType.PROCESSED_AAR
        }
        // Arguments passed to an ArtifactTransform must not be null
        val jetifierIgnoreList = projectOptions[StringOption.JETIFIER_IGNORE_LIST] ?: ""
        if (projectOptions.get(BooleanOption.ENABLE_JETIFIER)) {
            registerTransform(
                JetifyTransform::class.java,
                AndroidArtifacts.ArtifactType.AAR,
                jetifiedAarOutputType
            ) { params ->
                params.ignoreListOption.setDisallowChanges(jetifierIgnoreList)
            }
            registerTransform(
                JetifyTransform::class.java,
                AndroidArtifacts.ArtifactType.JAR,
                AndroidArtifacts.ArtifactType.PROCESSED_JAR
            ) { params ->
                params.ignoreListOption.setDisallowChanges(jetifierIgnoreList)
            }
        } else if (autoNamespaceDependencies) {
            // Namespaced resources code path is not optimized. Identity transforms are removed
            // otherwise.
            registerIdentityTransformWhenJetifierIsDisabled(jetifiedAarOutputType)
        } else {
            // Still register the transform if/when dagger plugin is applied
            // TODO(b/288221106): Dagger plugin depends on our internal implementation,
            // we need to eliminate their dependency on this to be able to remove the following.
            project.plugins.withId("dagger.hilt.android.plugin") {
                registerIdentityTransformWhenJetifierIsDisabled(jetifiedAarOutputType)
            }
        }

        registerTransform(
            ExtractAarTransform::class.java,
            aarOrJarTypeToConsume.aar,
            AndroidArtifacts.ArtifactType.EXPLODED_AAR
        )
        registerTransform(
            ExtractAarTransform::class.java,
            AndroidArtifacts.ArtifactType.LOCAL_AAR_FOR_LINT,
            AndroidArtifacts.ArtifactType.LOCAL_EXPLODED_AAR_FOR_LINT
        )
        dependencies.registerTransform(
            MockableJarTransform::class.java
        ) { spec: TransformSpec<MockableJarTransform.Parameters> ->
            // Query for JAR instead of PROCESSED_JAR as android.jar doesn't need processing
            spec.parameters.projectName.set(project.name)
            spec.parameters.returnDefaultValues.set(true)
            spec.from.attribute(
                ARTIFACT_TYPE_ATTRIBUTE,
                AndroidArtifacts.ArtifactType.JAR.type
            )
            spec.from.attribute(
                AndroidArtifacts.MOCKABLE_JAR_RETURN_DEFAULT_VALUES,
                true
            )
            spec.to.attribute(
                ARTIFACT_TYPE_ATTRIBUTE,
                AndroidArtifacts.TYPE_MOCKABLE_JAR
            )
            spec.to.attribute(
                AndroidArtifacts.MOCKABLE_JAR_RETURN_DEFAULT_VALUES,
                true
            )
        }
        dependencies.registerTransform(
            MockableJarTransform::class.java
        ) { spec: TransformSpec<MockableJarTransform.Parameters> ->
            // Query for JAR instead of PROCESSED_JAR as android.jar doesn't need processing
            spec.parameters.projectName.set(project.name)
            spec.parameters.returnDefaultValues.set(false)
            spec.from.attribute(
                ARTIFACT_TYPE_ATTRIBUTE,
                AndroidArtifacts.ArtifactType.JAR.type
            )
            spec.from.attribute(
                AndroidArtifacts.MOCKABLE_JAR_RETURN_DEFAULT_VALUES,
                false
            )
            spec.to.attribute(
                ARTIFACT_TYPE_ATTRIBUTE,
                AndroidArtifacts.TYPE_MOCKABLE_JAR
            )
            spec.to.attribute(
                AndroidArtifacts.MOCKABLE_JAR_RETURN_DEFAULT_VALUES,
                false
            )
        }

        // transform to extract attr info from android.jar
        registerTransform(
            PlatformAttrTransform::class.java,
            // Query for JAR instead of PROCESSED_JAR as android.jar doesn't need processing
            AndroidArtifacts.ArtifactType.JAR.type,
            AndroidArtifacts.TYPE_PLATFORM_ATTR
        )

        val sharedLibSupport = projectOptions[BooleanOption.CONSUME_DEPENDENCIES_AS_SHARED_LIBRARIES]

        val libraryCategory = project.objects.named(Category::class.java, Category.LIBRARY)
        for (transformTarget in AarTransform.getTransformTargets(aarOrJarTypeToConsume)) {
            dependencies.registerTransform(
                AarTransform::class.java
            ) { spec ->
                spec.from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, AndroidArtifacts.ArtifactType.EXPLODED_AAR.type)
                spec.from.attribute(Category.CATEGORY_ATTRIBUTE, libraryCategory)
                spec.to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, transformTarget.type)
                spec.to.attribute(Category.CATEGORY_ATTRIBUTE, libraryCategory)
                spec.parameters.projectName.setDisallowChanges(project.name)
                spec.parameters.targetType.setDisallowChanges(transformTarget)
                spec.parameters.sharedLibSupport.setDisallowChanges(sharedLibSupport)
            }
        }
        if (projectOptions[BooleanOption.PRECOMPILE_DEPENDENCIES_RESOURCES]) {
            registerTransform(
                AarResourcesCompilerTransform::class.java,
                AndroidArtifacts.ArtifactType.EXPLODED_AAR,
                AndroidArtifacts.ArtifactType.COMPILED_DEPENDENCIES_RESOURCES
            ) { params ->
                projectServices.initializeAapt2Input(params.aapt2, task = null)
            }
        }
        // API Jar: Produce a single API jar that can also contain the library R class from the AAR
        val apiUsage: Usage = project.objects.named(Usage::class.java, Usage.JAVA_API)

        dependencies.registerTransform(
            AarToClassTransform::class.java
        ) { reg: TransformSpec<AarToClassTransform.Params> ->
            reg.from.attribute(
                ARTIFACT_TYPE_ATTRIBUTE,
                aarOrJarTypeToConsume.aar.type
            )
            reg.from.attribute(
                Usage.USAGE_ATTRIBUTE,
                apiUsage
            )
            reg.to.attribute(
                ARTIFACT_TYPE_ATTRIBUTE,
                AndroidArtifacts.ArtifactType.CLASSES_JAR.type
            )
            reg.to.attribute(
                Usage.USAGE_ATTRIBUTE,
                apiUsage
            )
            reg.parameters { params: AarToClassTransform.Params ->
                params.forCompileUse.set(true)
                params.generateRClassJar.set(true)
            }
        }
        // Produce a single runtime jar from the AAR.
        val runtimeUsage: Usage = project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME)

        dependencies.registerTransform(
            AarToClassTransform::class.java
        ) { reg: TransformSpec<AarToClassTransform.Params> ->
            reg.from.attribute(
                ARTIFACT_TYPE_ATTRIBUTE,
                aarOrJarTypeToConsume.aar.type
            )
            reg.from.attribute(
                Usage.USAGE_ATTRIBUTE,
                runtimeUsage
            )
            reg.to.attribute(
                ARTIFACT_TYPE_ATTRIBUTE,
                AndroidArtifacts.ArtifactType.CLASSES_JAR.type
            )
            reg.to.attribute(
                Usage.USAGE_ATTRIBUTE,
                runtimeUsage
            )
            reg.parameters { params: AarToClassTransform.Params ->
                params.forCompileUse.set(false)

                params.generateRClassJar.set(false)
            }
        }

        if (projectOptions[BooleanOption.ENABLE_PROGUARD_RULES_EXTRACTION]) {
            registerTransform(
                ExtractProGuardRulesTransform::class.java,
                aarOrJarTypeToConsume.jar,
                AndroidArtifacts.ArtifactType.UNFILTERED_PROGUARD_RULES
            )
        }
        registerTransform(
            LibrarySymbolTableTransform::class.java,
            AndroidArtifacts.ArtifactType.EXPLODED_AAR,
            AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME
        )
        if (autoNamespaceDependencies) {
            registerTransform(
                AutoNamespacePreProcessTransform::class.java,
                AndroidArtifacts.ArtifactType.MAYBE_NON_NAMESPACED_PROCESSED_AAR,
                AndroidArtifacts.ArtifactType.PREPROCESSED_AAR_FOR_AUTO_NAMESPACE
            ) { params ->
                projectServices.initializeAapt2Input(params.aapt2, task = null)
            }
            registerTransform(
                AutoNamespacePreProcessTransform::class.java,
                AndroidArtifacts.ArtifactType.JAR,
                AndroidArtifacts.ArtifactType.PREPROCESSED_AAR_FOR_AUTO_NAMESPACE
            ) { params ->
                projectServices.initializeAapt2Input(params.aapt2, task = null)
            }

            registerTransform(
                AutoNamespaceTransform::class.java,
                AndroidArtifacts.ArtifactType.PREPROCESSED_AAR_FOR_AUTO_NAMESPACE,
                AndroidArtifacts.ArtifactType.PROCESSED_AAR
            ) { params ->
                projectServices.initializeAapt2Input(params.aapt2, task = null)
            }
        }
        // Transform to go from external jars to CLASSES and JAVA_RES artifacts. This returns the
        // same exact file but with different types, since a jar file can contain both.
        for (classesOrResources in arrayOf(
            AndroidArtifacts.ArtifactType.CLASSES_JAR,
            AndroidArtifacts.ArtifactType.JAVA_RES
        )) {
            registerTransform(
                IdentityTransform::class.java,
                aarOrJarTypeToConsume.jar,
                classesOrResources
            )
        }
        registerTransform(
            ExtractJniTransform::class.java,
            aarOrJarTypeToConsume.jar,
            AndroidArtifacts.ArtifactType.JNI
        )
        // The Kotlin Kapt plugin should query for PROCESSED_JAR, but it is currently querying for
        // JAR, so we need to have the workaround below to make it get PROCESSED_JAR. See
        // http://issuetracker.google.com/111009645.
        project.configurations.configureEach { configuration: Configuration ->
            if (configuration.name.startsWith("kapt")) {
                configuration
                    .attributes
                    .attribute(
                        ARTIFACT_TYPE_ATTRIBUTE,
                        aarOrJarTypeToConsume.jar.type
                    )
            }
        }

        // From an Android library subproject, there are 2 transform flows to CLASSES:
        //     1. CLASSES_DIR -> CLASSES
        //     2. CLASSES_JAR -> CLASSES
        // From a Java library subproject, there are also 2 transform flows to CLASSES:
        //     1. JVM_CLASS_DIRECTORY -> CLASSES
        //     2. JAR -> PROCESSED_JAR -> `CLASSES_JAR -> CLASSES
        registerTransform(
            ClassesDirToClassesTransform::class.java,
            AndroidArtifacts.ArtifactType.CLASSES_DIR,
            AndroidArtifacts.ArtifactType.CLASSES
        )
        registerTransform(
            IdentityTransform::class.java,
            AndroidArtifacts.ArtifactType.CLASSES_JAR,
            AndroidArtifacts.ArtifactType.CLASSES
        )
        registerTransform(
            IdentityTransform::class.java,
            ArtifactTypeDefinition.JVM_CLASS_DIRECTORY,
            AndroidArtifacts.ArtifactType.CLASSES.type
        ) { params ->
            params.acceptNonExistentInputFile.setDisallowChanges(true)
        }

        registerTransform(
            CollectResourceSymbolsTransform::class.java,
            AndroidArtifacts.ArtifactType.ANDROID_RES.type,
            AndroidArtifacts.ArtifactType.ANDROID_RES_SYMBOLS.type
        )
        registerTransform(
            CollectClassesTransform::class.java,
            AndroidArtifacts.ArtifactType.CLASSES_JAR,
            AndroidArtifacts.ArtifactType.JAR_CLASS_LIST
        )

        return this
    }

    /** Produce the artifact type jetifier would have produced if it was enabled. */
    private fun registerIdentityTransformWhenJetifierIsDisabled(
            jetifiedAarOutputType: AndroidArtifacts.ArtifactType) {
        registerTransform(
                IdentityTransform::class.java,
                AndroidArtifacts.ArtifactType.AAR,
                jetifiedAarOutputType
        )
        registerTransform(
                IdentityTransform::class.java,
                AndroidArtifacts.ArtifactType.JAR,
                AndroidArtifacts.ArtifactType.PROCESSED_JAR
        )
    }

    fun configurePrivacySandboxSdkConsumerTransforms(
        compileSdkHashString: String,
        buildToolsRevision: Revision,
        bootstrapCreationConfig: BootClasspathConfig,
        variants: List<VariantCreationConfig> = emptyList()
    )
    : DependencyConfigurator {
        for (from in AsarTransform.supportedAsarTransformTypes) {
            registerTransform(
                    AsarTransform::class.java,
                    AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_ARCHIVE,
                    from
            ) {
                it.targetType.set(from)
            }
        }

        fun configureExtractSdkShimTransforms(experimentalProperties: Map<String, Any>?) {
            val extractSdkShimTransformParamConfig =
                { reg: TransformSpec<ExtractSdkShimTransform.Parameters> ->
                    val experimentalPropertiesApiGenerator: Dependency? =
                        experimentalProperties?.let {
                            ModulePropertyKey.Dependencies.ANDROID_PRIVACY_SANDBOX_SDK_API_GENERATOR
                                .getValue(it)?.single()
                        }
                    val apigeneratorArtifact: Dependency =
                        experimentalPropertiesApiGenerator
                            ?: project.dependencies.create(
                                projectServices.projectOptions.get(StringOption.ANDROID_PRIVACY_SANDBOX_SDK_API_GENERATOR)
                                    ?: MavenCoordinates.ANDROIDX_PRIVACYSANDBOX_TOOLS_TOOLS_APIGENERATOR.toString()
                            ) as Dependency

                    val experimentalPropertiesRuntimeApigeneratorDependencies =
                        experimentalProperties?.let {
                            ModulePropertyKey.Dependencies.ANDROID_PRIVACY_SANDBOX_SDK_API_GENERATOR_GENERATED_RUNTIME_DEPENDENCIES.getValue(
                                it
                            )
                        }
                    val runtimeDependenciesForShimSdk: List<Dependency> =
                        experimentalPropertiesRuntimeApigeneratorDependencies
                            ?: (projectServices.projectOptions
                                .get(StringOption.ANDROID_PRIVACY_SANDBOX_SDK_API_GENERATOR_GENERATED_RUNTIME_DEPENDENCIES)
                                ?.split(",")
                                ?: listOf(
                                    MavenCoordinates.ORG_JETBRAINS_KOTLIN_KOTLIN_STDLIB.toString(),
                                    MavenCoordinates.ORG_JETBRAINS_KOTLINX_KOTLINX_COROUTINES_ANDROID.toString(),
                                    MavenCoordinates.ANDROIDX_PRIVACYSANDBOX_UI_UI_CORE.toString(),
                                    MavenCoordinates.ANDROIDX_CORE_CORE_KTX.toString(),
                                    MavenCoordinates.ANDROIDX_PRIVACYSANDBOX_ACTIVITY_ACTIVITY_CORE.toString(),
                                    MavenCoordinates.ANDROIDX_PRIVACYSANDBOX_ACTIVITY_ACTIVITY_PROVIDER.toString(),
                                    MavenCoordinates.ANDROIDX_PRIVACYSANDBOX_ACTIVITY_ACTIVITY_CLIENT.toString(),
                                    MavenCoordinates.ANDROIDX_PRIVACYSANDBOX_UI_UI_CLIENT.toString(),
                                ))
                                .map {
                                    project.dependencies.create(it)
                                }

                    val params = reg.parameters
                    val apiGeneratorConfiguration =
                        project.configurations.detachedConfiguration(apigeneratorArtifact)
                    apiGeneratorConfiguration.isCanBeConsumed = false
                    apiGeneratorConfiguration.isCanBeResolved = true
                    params.apiGenerator.setFrom(apiGeneratorConfiguration)
                    params.buildTools.initialize(
                        task = null,
                        projectServices.buildServiceRegistry,
                        compileSdkHashString,
                        buildToolsRevision
                    )

                    // For kotlin compilation
                    params.bootstrapClasspath.from(bootstrapCreationConfig.fullBootClasspath)

                    val kotlinEmbeddableCompiler =
                        experimentalProperties?.let {
                            ModulePropertyKey.Dependencies.ANDROID_PRIVACY_SANDBOX_SDK_KOTLIN_COMPILER_EMBEDDABLE.getValue(
                                it
                            )?.single()
                        } as Dependency?
                    val kotlinCompiler: Configuration =
                        project.configurations.detachedConfiguration(
                            kotlinEmbeddableCompiler ?: project.dependencies.create(
                                projectServices.projectOptions.get(StringOption.ANDROID_PRIVACY_SANDBOX_SDK_KOTLIN_COMPILER_EMBEDDABLE)
                                    ?: MavenCoordinates.ORG_JETBRAINS_KOTLIN_KOTLIN_COMPILER_EMBEDDABLE.toString()
                            )
                        )
                    kotlinCompiler.isCanBeConsumed = false
                    kotlinCompiler.isCanBeResolved = true
                    params.kotlinCompiler.from(kotlinCompiler)
                    params.requireServices.set(
                        projectServices.projectOptions[BooleanOption.PRIVACY_SANDBOX_SDK_REQUIRE_SERVICES]
                    )
                    val configuration = project.configurations.detachedConfiguration(
                        *runtimeDependenciesForShimSdk.toTypedArray()
                    )
                    configuration.isCanBeConsumed = false
                    configuration.isCanBeResolved = true

                    configuration.attributes {
                        it.attribute(
                            BuildTypeAttr.ATTRIBUTE,
                            project.objects.named(
                                BuildTypeAttr::class.java,
                                BuilderConstants.RELEASE
                            )
                        )
                    }
                    params.runtimeDependencies.from(configuration.incoming.artifactView { config: ArtifactView.ViewConfiguration ->
                        config.attributes.apply {
                            attribute(
                                Usage.USAGE_ATTRIBUTE,
                                project.objects.named(Usage::class.java, Usage.JAVA_API)
                            )
                            attribute(
                                AndroidArtifacts.ARTIFACT_TYPE,
                                AndroidArtifacts.ArtifactType.CLASSES_JAR.type
                            )
                        }
                    }.artifacts.artifactFiles)
                }

            fun registerExtractSdkShimTransform(usage: String) {
                project.dependencies.registerTransform(
                    ExtractCompileSdkShimTransform::class.java,
                ) { reg ->
                    val usageObj: Usage = project.objects.named(Usage::class.java, usage)
                    reg.from.attribute(
                        ARTIFACT_TYPE_ATTRIBUTE,
                        AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_INTERFACE_DESCRIPTOR.type
                    )
                    reg.from.attribute(
                        Usage.USAGE_ATTRIBUTE,
                        usageObj
                    )
                    reg.to.attribute(
                        ARTIFACT_TYPE_ATTRIBUTE,
                        AndroidArtifacts.ArtifactType.CLASSES_JAR.type
                    )
                    reg.to.attribute(
                        Usage.USAGE_ATTRIBUTE,
                        usageObj
                    )
                    extractSdkShimTransformParamConfig(reg)
                }
            }
            registerExtractSdkShimTransform(Usage.JAVA_API)
            registerExtractSdkShimTransform(Usage.JAVA_RUNTIME)
        }

        val properties = variants.map { variant ->
            variant.experimentalProperties.also { it.disallowChanges() }.get().filterKeys {
                it == ModulePropertyKey.Dependencies.ANDROID_PRIVACY_SANDBOX_SDK_API_GENERATOR_GENERATED_RUNTIME_DEPENDENCIES.key ||
                        it == ModulePropertyKey.Dependencies.ANDROID_PRIVACY_SANDBOX_SDK_API_GENERATOR.key
            }
        }.distinct()

        if (properties.count() > 1) {
            error(
                "It is not possible to override Privacy Sandbox experimental properties per variant.\n" +
                        "Properties with different values defined across multiple variants: ${properties.joinToString()} "
            )
        }
        configureExtractSdkShimTransforms(properties.singleOrNull())

        return this
    }

    fun configurePrivacySandboxSdkVariantTransforms(
        variants: List<VariantCreationConfig>,
    ): DependencyConfigurator {
        fun registerAsarToApksTransform(variants: List<VariantCreationConfig>) {
            // For signing privacy sandbox artifacts we allow per project signing configuration
            // by the use of experimental properties. To reduce the expense of registering per
            // variant we set a limit of one signing config in all variants, then register the
            // AsarToApksTransform once. To maintain the semantic, the build file must explicitly
            // declare the same signing config for all variants.
            val variantSigningConfigs = variants.map { variant ->
                val experimentalProps = variant.experimentalProperties
                experimentalProps.finalizeValue()
                SigningConfigData.fromExperimentalPropertiesSigningConfig(variant.experimentalProperties)
            }.distinct()

            val signingConfigProvider: Provider<SigningConfigData> =
                    when (variantSigningConfigs.count()) {
                        0 -> return // No variants
                        1 -> if (variantSigningConfigs.singleOrNull() != null) {
                            // An identical signing config is set in all variants by experimental properties.
                            variants.first().services.provider {
                                variantSigningConfigs.singleOrNull()
                            }
                        } else {
                            // No experimental properties are set, use the default.
                            getBuildService(
                                    variants.first().services.buildServiceRegistry,
                                    AndroidLocationsBuildService::class.java
                            ).map(AndroidLocationsBuildService::getDefaultDebugKeystoreSigningConfig)
                        }

                        else -> throw UnsupportedOperationException(
                                "It is not possible to override Privacy Sandbox experimental properties per variant.\n" +
                                        "Set the same signing config using experimental properties in each variant explicitly.")
                    }
            registerTransform(
                    AsarToApksTransform::class.java,
                    AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_ARCHIVE,
                    AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_APKS
            ) { params ->
                projectServices.initializeAapt2Input(params.aapt2, task = null)

                params.signingConfigData.set(signingConfigProvider)
                params.signingConfigValidationResultDir.set(
                        ArtifactsImpl(project,
                                "global").get(InternalArtifactType.VALIDATE_SIGNING_CONFIG)
                )
            }
        }
        registerAsarToApksTransform(variants)

        return this
    }

    fun configureCalculateStackFramesTransforms(
            bootClasspathConfig: BootClasspathConfig) : DependencyConfigurator {
        registerGlobalRecalculateStackFramesTransform(
                project.name,
                project.dependencies,
                bootClasspathConfig.fullBootClasspathProvider,
                projectServices.buildServiceRegistry
        )
        return this
    }

    fun configureJacocoTransforms() : DependencyConfigurator {
        project.extensions.findByType(JacocoPluginExtension::class.java)?.toolVersion =
            JacocoOptions.DEFAULT_VERSION
        val jacocoTransformParametersConfig: (JacocoTransform.Params) -> Unit = {
            val jacocoVersion = JacocoOptions.DEFAULT_VERSION
            val jacocoConfiguration = JacocoConfigurations
                    .getJacocoAntTaskConfiguration(project, jacocoVersion)
            it.jacocoInstrumentationService
                    .set(getBuildService(projectServices.buildServiceRegistry))
            it.jacocoConfiguration.from(jacocoConfiguration)
            it.jacocoVersion.setDisallowChanges(jacocoVersion)
        }
        registerTransform(
                JacocoTransform::class.java,
                AndroidArtifacts.ArtifactType.CLASSES,
                AndroidArtifacts.ArtifactType.JACOCO_CLASSES,
                jacocoTransformParametersConfig
        )
        registerTransform(
                JacocoTransform::class.java,
                AndroidArtifacts.ArtifactType.CLASSES_JAR,
                AndroidArtifacts.ArtifactType.JACOCO_CLASSES_JAR,
                jacocoTransformParametersConfig
        )
        registerTransform(
                JacocoTransform::class.java,
                AndroidArtifacts.ArtifactType.ASM_INSTRUMENTED_JARS,
                AndroidArtifacts.ArtifactType.JACOCO_ASM_INSTRUMENTED_JARS,
                jacocoTransformParametersConfig
        )
        return this
    }

    private fun <T : GenericTransformParameters> registerTransform(
        transformClass: Class<out TransformAction<T>>,
        fromArtifactType: AndroidArtifacts.ArtifactType,
        toArtifactType: AndroidArtifacts.ArtifactType,
        parametersSetter: ((T) -> Unit)? = null
    ) {
        registerTransform(
            transformClass,
            fromArtifactType.type,
            toArtifactType.type,
            parametersSetter
        )
    }

    private fun <T : GenericTransformParameters> registerTransform(
        transformClass: Class<out TransformAction<T>>,
        fromArtifactType: String,
        toArtifactType: String,
        parametersSetter: ((T) -> Unit)? = null
    ) {
        project.dependencies.registerTransform(
            transformClass
        ) { spec: TransformSpec<T> ->
            spec.from.attribute(ARTIFACT_TYPE_ATTRIBUTE, fromArtifactType)
            spec.to.attribute(ARTIFACT_TYPE_ATTRIBUTE, toArtifactType)
            spec.parameters.projectName.setDisallowChanges(project.name)
            parametersSetter?.let { it(spec.parameters) }
        }
    }

    fun configureAttributeMatchingStrategies(
            variantInputModel: VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>,
            supportPrivacySandbox: Boolean
    ): DependencyConfigurator {
        val schema = project.dependencies.attributesSchema

        // custom strategy for build-type and product-flavor.
        setBuildTypeStrategy(schema, variantInputModel)
        setupFlavorStrategy(schema, variantInputModel)
        setupModelStrategy(schema, supportPrivacySandbox)
        setUpAgpVersionStrategy(schema)

        return this
    }

    private fun setBuildTypeStrategy(
            schema: AttributesSchema,
            variantInputModel: VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
    ) {
        // this is ugly but because the getter returns a very base class we have no choices.
        val dslBuildTypes = variantInputModel.buildTypes.values.map { it.buildType }

        if (dslBuildTypes.isEmpty()) {
            return
        }

        val alternateMap: MutableMap<String, List<String>> =
            Maps.newHashMap()
        for (buildType in dslBuildTypes) {
            if (!buildType.matchingFallbacks.isEmpty()) {
                alternateMap[buildType.name] = buildType.matchingFallbacks
            }
        }
        if (!alternateMap.isEmpty()) {
            val buildTypeStrategy =
                schema.attribute(
                    ATTRIBUTE
                )
            buildTypeStrategy
                .compatibilityRules
                .add(
                    AlternateCompatibilityRule.BuildTypeRule::class.java
                ) { config: ActionConfiguration ->
                    config.setParams(
                        alternateMap
                    )
                }
            buildTypeStrategy
                .disambiguationRules
                .add(
                    MultiVariantBuildTypeRule::class.java
                ) { config: ActionConfiguration ->
                    config.setParams(
                        alternateMap
                    )
                }
        }
    }

    private fun setupFlavorStrategy(
            schema: AttributesSchema,
            variantInputModel: VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
    ) {
        // this is ugly but because the getter returns a very base class we have no choices.
        val flavors = variantInputModel.productFlavors.values.map { it.productFlavor }

        // first loop through all the flavors and collect for each dimension, and each value, its
        // fallbacks
        // map of (dimension > (requested > fallbacks))
        val alternateMap: MutableMap<String, MutableMap<String, List<String>>> =
            Maps.newHashMap()
        for (flavor in flavors) {
            if (flavor.matchingFallbacks.isNotEmpty()) {
                val name = flavor.name
                val dimension = flavor.dimension!!
                val dimensionMap =
                    alternateMap.computeIfAbsent(
                        dimension
                    ) { s: String? -> Maps.newHashMap() }
                dimensionMap[name] = flavor.matchingFallbacks
            }
            handleMissingDimensions(alternateMap, flavor)
        }
        // also handle missing dimensions on the default config.
        handleMissingDimensions(alternateMap, variantInputModel.defaultConfigData.defaultConfig)
        // now that we know we have all the fallbacks for each dimensions, we can create the
        // rule instances.
        for ((key, value) in alternateMap) {
            addFlavorStrategy(schema, key, value)
        }
    }

    private fun setupModelStrategy(attributesSchema: AttributesSchema, supportPrivacySandbox: Boolean) {
        setUp(attributesSchema, supportPrivacySandbox)
    }

    /** This is to enforce AGP version across a single or composite build. */
    private fun setUpAgpVersionStrategy(attributesSchema: AttributesSchema) {
        val strategy = attributesSchema.attribute(AgpVersionAttr.ATTRIBUTE)
        strategy.compatibilityRules.add(AgpVersionCompatibilityRule::class.java)
    }

    private fun handleMissingDimensions(
        alternateMap: MutableMap<String, MutableMap<String, List<String>>>,
        flavor: BaseFlavor
    ) {
        val missingStrategies = flavor.missingDimensionStrategies
        if (missingStrategies.isNotEmpty()) {
            for ((dimension, value) in missingStrategies) {
                val dimensionMap = alternateMap.computeIfAbsent(dimension) { Maps.newHashMap() }
                dimensionMap[value.requested] = value.fallbacks
            }
        }
    }

    /** Configure artifact transforms that require variant-specific attribute information.  */
    fun configureVariantTransforms(
        variants: List<VariantCreationConfig>,
        nestedComponents: List<ComponentCreationConfig>,
        bootClasspathConfig: BootClasspathConfig
    ): DependencyConfigurator {

        val allComponents: List<ComponentCreationConfig> = variants.plus(nestedComponents)

        val dependencies = project.dependencies
        val projectOptions = projectServices.projectOptions

        for (component in allComponents) {
            registerAsmTransformForComponent(
                project.name,
                dependencies,
                component
            )

            registerRecalculateStackFramesTransformForComponent(
                project.name,
                dependencies,
                component
            )
        }
        if (allComponents.isNotEmpty()) {
            val bootClasspath = project.files(bootClasspathConfig.bootClasspath)
            val services = allComponents.first().services
            DexingRegistration.registerTransforms(
                allComponents,
                DexingRegistration.ComponentAgnosticParameters(
                    projectName = project.name,
                    dependencyHandler = dependencies,
                    bootClasspath = bootClasspath,
                    desugarLibConfigFiles = getDesugarLibConfigFiles(services),
                    errorFormat = SyncOptions.getErrorFormatMode(projectOptions),
                    // Disable incremental dexing for main and androidTest components in dynamic
                    // feature module (b/246326007)
                    disableIncrementalDexing = allComponents.any { it.componentType.isDynamicFeature },
                    components = allComponents
                )
            )

            val d8Version = R8Version.getVersionString()

            allComponents
                .mapTo(linkedSetOf()) { it.minSdk.apiLevel }
                .forEach { minSdkVersion ->
                    // register d8 backported methods generatore when desugaring enabled
                    project.dependencies.registerTransform(
                        D8BackportedMethodsGenerator::class.java
                    ) { spec ->
                        spec.parameters { parameters ->
                            parameters.minSdkVersion.set(minSdkVersion)
                            parameters.d8Version.set(d8Version)
                            parameters.desugarLibConfigFiles.setFrom(getDesugarLibConfigFiles(services))
                            parameters.bootclasspath.from(bootClasspath)
                        }
                        spec.from.attribute(ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                        spec.from.attribute(ATTR_ENABLE_CORE_LIBRARY_DESUGARING, TRUE.toString())
                        spec.from.attribute(ATTR_LINT_MIN_SDK, minSdkVersion.toString())
                        spec.to.attribute(ARTIFACT_TYPE_ATTRIBUTE, D8_DESUGAR_METHODS)
                        spec.to.attribute(ATTR_ENABLE_CORE_LIBRARY_DESUGARING, TRUE.toString())
                        spec.to.attribute(ATTR_LINT_MIN_SDK, minSdkVersion.toString())
                    }

                    // register d8 backported methods generator when desugaring disabled
                    project.dependencies.registerTransform(
                        D8BackportedMethodsGenerator::class.java
                    ) { spec ->
                        spec.parameters { parameters ->
                            parameters.minSdkVersion.set(minSdkVersion)
                            parameters.d8Version.set(d8Version)
                        }
                        spec.from.attribute(ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                        spec.from.attribute(ATTR_ENABLE_CORE_LIBRARY_DESUGARING, FALSE.toString())
                        spec.from.attribute(ATTR_LINT_MIN_SDK, minSdkVersion.toString())
                        spec.to.attribute(ARTIFACT_TYPE_ATTRIBUTE, D8_DESUGAR_METHODS)
                        spec.to.attribute(ATTR_ENABLE_CORE_LIBRARY_DESUGARING, FALSE.toString())
                        spec.to.attribute(ATTR_LINT_MIN_SDK, minSdkVersion.toString())
                    }
                }
        }

        if (projectOptions[BooleanOption.ENABLE_PROGUARD_RULES_EXTRACTION]
                && allComponents.any {
                    it is ConsumableCreationConfig && it.optimizationCreationConfig.minifiedEnabled
                }) {
            dependencies.registerTransform(
                    FilterShrinkerRulesTransform::class.java
            ) { reg: TransformSpec<FilterShrinkerRulesTransform.Parameters> ->
                reg.from
                        .attribute(
                                ARTIFACT_TYPE_ATTRIBUTE,
                                AndroidArtifacts.ArtifactType.UNFILTERED_PROGUARD_RULES.type
                        )
                reg.to
                        .attribute(
                                ARTIFACT_TYPE_ATTRIBUTE,
                                AndroidArtifacts.ArtifactType.FILTERED_PROGUARD_RULES.type
                        )
                reg.parameters { params: FilterShrinkerRulesTransform.Parameters ->
                    params.shrinker.set(ShrinkerVersion.R8)
                    params.projectName.set(project.name)
                }
            }
        }

        if (projectOptions[BooleanOption.ENABLE_DUPLICATE_CLASSES_CHECK]) {
            registerTransform(
                EnumerateClassesTransform::class.java,
                AndroidArtifacts.ArtifactType.CLASSES_JAR,
                AndroidArtifacts.ArtifactType.ENUMERATED_RUNTIME_CLASSES
            )
        }

        registerDexingOutputSplitTransform(dependencies)

        return this
    }

    companion object {
        @JvmStatic
        fun addFlavorStrategy(
            schema: AttributesSchema,
            dimension: String,
            alternateMap: Map<String, List<String>>
        ) {
            val attr = ProductFlavorAttr.of(dimension)
            val flavorStrategy = schema.attribute(attr)
            flavorStrategy
                .compatibilityRules
                .add(AlternateCompatibilityRule.ProductFlavorRule::class.java) {
                    it.setParams(alternateMap)
                }
            flavorStrategy
                .disambiguationRules
                .add(MultiVariantProductFlavorRule::class.java) {
                    it.setParams(alternateMap)
                }
        }
    }
}
