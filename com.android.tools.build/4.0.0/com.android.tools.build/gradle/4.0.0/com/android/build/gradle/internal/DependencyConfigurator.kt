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

import com.android.build.api.attributes.BuildTypeAttr.Companion.ATTRIBUTE
import com.android.build.api.attributes.ProductFlavorAttr
import com.android.build.gradle.internal.dependency.AarResourcesCompilerTransform
import com.android.build.gradle.internal.dependency.AarToClassTransform
import com.android.build.gradle.internal.dependency.AarTransform
import com.android.build.gradle.internal.dependency.AlternateCompatibilityRule
import com.android.build.gradle.internal.dependency.AlternateDisambiguationRule
import com.android.build.gradle.internal.dependency.AndroidXDependencySubstitution.replaceOldSupportLibraries
import com.android.build.gradle.internal.dependency.ClassesDirToClassesTransform
import com.android.build.gradle.internal.dependency.ExtractAarTransform
import com.android.build.gradle.internal.dependency.ExtractProGuardRulesTransform
import com.android.build.gradle.internal.dependency.GenericTransformParameters
import com.android.build.gradle.internal.dependency.IdentityTransform
import com.android.build.gradle.internal.dependency.JetifyTransform
import com.android.build.gradle.internal.dependency.LibraryDefinedSymbolTableTransform
import com.android.build.gradle.internal.dependency.LibrarySymbolTableTransform
import com.android.build.gradle.internal.dependency.MockableJarTransform
import com.android.build.gradle.internal.dependency.ModelArtifactCompatibilityRule.Companion.setUp
import com.android.build.gradle.internal.dependency.PlatformAttrTransform
import com.android.build.gradle.internal.dsl.BaseFlavor
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.res.getAapt2FromMavenAndVersion
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.services.getAapt2DaemonBuildService
import com.android.build.gradle.internal.variant.VariantInputModel
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.options.SyncOptions
import com.google.common.base.Strings
import com.google.common.collect.Maps
import org.gradle.api.Action
import org.gradle.api.ActionConfiguration
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.transform.TransformSpec
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.artifacts.ArtifactAttributes

/**
 * configures the dependencies for a set of variant inputs.
 */
class DependencyConfigurator(
    private val project: Project,
    private val projectName: String,
    private val globalScope: GlobalScope,
    private val variantInputModel: VariantInputModel
) {

    fun configureDependencies() {
        val dependencies: DependencyHandler = project.dependencies

        // USE_ANDROID_X indicates that the developers want to be in the AndroidX world, whereas
        // ENABLE_JETIFIER indicates that they want to have automatic tool support for converting
        // not-yet-migrated dependencies. Developers may want to use AndroidX but disable Jetifier
        // for purposes such as debugging. However, disabling AndroidX and enabling Jetifier is not
        // allowed.
        check(
            !(!globalScope.projectOptions[BooleanOption.USE_ANDROID_X]
                    && globalScope.projectOptions[BooleanOption.ENABLE_JETIFIER])
        ) {
            ("AndroidX must be enabled when Jetifier is enabled. To resolve, set "
                    + BooleanOption.USE_ANDROID_X.propertyName
                    + "=true in your gradle.properties file.")
        }
        // If Jetifier is enabled, replace old support libraries with AndroidX.
        if (globalScope.projectOptions[BooleanOption.ENABLE_JETIFIER]) {
            replaceOldSupportLibraries(project)
        }
        /*
         * Register transforms.
         */
        // The aars/jars may need to be processed (e.g., jetified to AndroidX) before they can be
        // used
        // Arguments passed to an ArtifactTransform must not be null
        val jetifierSkipIfPossible =
            globalScope.projectOptions[BooleanOption.JETIFIER_SKIP_IF_POSSIBLE]
        val jetifierBlackList = Strings.nullToEmpty(
            globalScope.projectOptions[StringOption.JETIFIER_BLACKLIST]
        )
        if (globalScope.projectOptions[BooleanOption.ENABLE_JETIFIER]) {
            dependencies.registerTransform(
                JetifyTransform::class.java
            ) { spec: TransformSpec<JetifyTransform.Parameters> ->
                spec.parameters.projectName.set(projectName)
                spec.parameters.skipIfPossible.set(jetifierSkipIfPossible)
                spec.parameters.blackListOption.set(jetifierBlackList)
                spec.from.attribute(
                    ArtifactAttributes.ARTIFACT_FORMAT,
                    AndroidArtifacts.ArtifactType.AAR.type
                )
                spec.to.attribute(
                    ArtifactAttributes.ARTIFACT_FORMAT,
                    AndroidArtifacts.ArtifactType.PROCESSED_AAR.type
                )
            }
            dependencies.registerTransform(
                JetifyTransform::class.java
            ) { spec: TransformSpec<JetifyTransform.Parameters> ->
                spec.parameters.projectName.set(projectName)
                spec.parameters.skipIfPossible.set(jetifierSkipIfPossible)
                spec.parameters.blackListOption.set(jetifierBlackList)
                spec.from.attribute(
                    ArtifactAttributes.ARTIFACT_FORMAT,
                    AndroidArtifacts.ArtifactType.JAR.type
                )
                spec.to.attribute(
                    ArtifactAttributes.ARTIFACT_FORMAT,
                    AndroidArtifacts.ArtifactType.PROCESSED_JAR.type
                )
            }
        } else {
            dependencies.registerTransform(
                IdentityTransform::class.java
            ) { spec: TransformSpec<GenericTransformParameters> ->
                spec.parameters.projectName.set(projectName)
                spec.from.attribute(
                    ArtifactAttributes.ARTIFACT_FORMAT,
                    AndroidArtifacts.ArtifactType.AAR.type
                )
                spec.to.attribute(
                    ArtifactAttributes.ARTIFACT_FORMAT,
                    AndroidArtifacts.ArtifactType.PROCESSED_AAR.type
                )
            }
            dependencies.registerTransform(
                IdentityTransform::class.java
            ) { spec: TransformSpec<GenericTransformParameters> ->
                spec.parameters.projectName.set(projectName)
                spec.from.attribute(
                    ArtifactAttributes.ARTIFACT_FORMAT,
                    AndroidArtifacts.ArtifactType.JAR.type
                )
                spec.to.attribute(
                    ArtifactAttributes.ARTIFACT_FORMAT,
                    AndroidArtifacts.ArtifactType.PROCESSED_JAR.type
                )
            }
        }
        dependencies.registerTransform(
            ExtractAarTransform::class.java
        ) { spec: TransformSpec<GenericTransformParameters> ->
            spec.parameters.projectName.set(projectName)
            spec.from.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                AndroidArtifacts.ArtifactType.PROCESSED_AAR.type
            )
            spec.to.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                AndroidArtifacts.ArtifactType.EXPLODED_AAR.type
            )
        }
        dependencies.registerTransform(
            MockableJarTransform::class.java
        ) { spec: TransformSpec<MockableJarTransform.Parameters> ->
            // Query for JAR instead of PROCESSED_JAR as android.jar doesn't need processing
            spec.parameters.projectName.set(projectName)
            spec.parameters.returnDefaultValues.set(true)
            spec.from.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                AndroidArtifacts.ArtifactType.JAR.type
            )
            spec.from.attribute(
                AndroidArtifacts.MOCKABLE_JAR_RETURN_DEFAULT_VALUES,
                true
            )
            spec.to.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
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
            spec.parameters.projectName.set(projectName)
            spec.parameters.returnDefaultValues.set(false)
            spec.from.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                AndroidArtifacts.ArtifactType.JAR.type
            )
            spec.from.attribute(
                AndroidArtifacts.MOCKABLE_JAR_RETURN_DEFAULT_VALUES,
                false
            )
            spec.to.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                AndroidArtifacts.TYPE_MOCKABLE_JAR
            )
            spec.to.attribute(
                AndroidArtifacts.MOCKABLE_JAR_RETURN_DEFAULT_VALUES,
                false
            )
        }
        // transform to extract attr info from android.jar
        dependencies.registerTransform(
            PlatformAttrTransform::class.java
        ) { spec: TransformSpec<GenericTransformParameters> ->
            spec.parameters.projectName.set(projectName)
            // Query for JAR instead of PROCESSED_JAR as android.jar doesn't need processing
            spec.from.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                AndroidArtifacts.ArtifactType.JAR.type
            )
            spec.to.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                AndroidArtifacts.TYPE_PLATFORM_ATTR
            )
        }
        val sharedLibSupport = globalScope
            .projectOptions[BooleanOption.CONSUME_DEPENDENCIES_AS_SHARED_LIBRARIES]
        val autoNamespaceDependencies =
            (globalScope.extension.aaptOptions.namespaced
                    && globalScope
                .projectOptions[BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES])
        for (transformTarget in AarTransform.getTransformTargets()) {
            dependencies.registerTransform(
                AarTransform::class.java
            ) { spec: TransformSpec<AarTransform.Parameters> ->
                spec.parameters.projectName.set(projectName)
                spec.parameters.targetType.set(transformTarget)
                spec.parameters.sharedLibSupport.set(sharedLibSupport)
                spec.parameters
                    .autoNamespaceDependencies
                    .set(autoNamespaceDependencies)
                spec.from.attribute(
                    ArtifactAttributes.ARTIFACT_FORMAT,
                    AndroidArtifacts.ArtifactType.EXPLODED_AAR.type
                )
                spec.to.attribute(
                    ArtifactAttributes.ARTIFACT_FORMAT,
                    transformTarget.type
                )
            }
        }
        if (globalScope.projectOptions[BooleanOption.PRECOMPILE_DEPENDENCIES_RESOURCES]) {
            dependencies.registerTransform(
                AarResourcesCompilerTransform::class.java
            ) { reg: TransformSpec<AarResourcesCompilerTransform.Parameters> ->
                reg.from.attribute(
                    ArtifactAttributes.ARTIFACT_FORMAT,
                    AndroidArtifacts.ArtifactType.EXPLODED_AAR.type
                )
                reg.to
                    .attribute(
                        ArtifactAttributes.ARTIFACT_FORMAT,
                        AndroidArtifacts.ArtifactType.COMPILED_DEPENDENCIES_RESOURCES.type
                    )
                reg.parameters { params: AarResourcesCompilerTransform.Parameters ->
                    val (first, second) = getAapt2FromMavenAndVersion(
                        globalScope
                    )
                    params.aapt2FromMaven
                        .from(first)
                    params.aapt2Version
                        .set(second)
                    params.errorFormatMode
                        .set(
                            SyncOptions.getErrorFormatMode(
                                globalScope.projectOptions
                            )
                        )
                    params.aapt2DaemonBuildService
                        .set(getAapt2DaemonBuildService(project))
                }
            }
        }
        // API Jar: Produce a single API jar that can also contain the library R class from the AAR
        val apiUsage: Usage = project.objects.named(Usage::class.java, Usage.JAVA_API)

        dependencies.registerTransform(
            AarToClassTransform::class.java
        ) { reg: TransformSpec<AarToClassTransform.Params> ->
            reg.from.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                AndroidArtifacts.ArtifactType.PROCESSED_AAR.type
            )
            reg.from.attribute(
                Usage.USAGE_ATTRIBUTE,
                apiUsage
            )
            reg.to.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                AndroidArtifacts.ArtifactType.CLASSES_JAR.type
            )
            reg.to.attribute(
                Usage.USAGE_ATTRIBUTE,
                apiUsage
            )
            reg.parameters { params: AarToClassTransform.Params ->
                params.forCompileUse.set(true)
                params.autoNamespaceDependencies
                    .set(autoNamespaceDependencies)
                params.generateRClassJar
                    .set(
                        globalScope.projectOptions.get(
                            BooleanOption.COMPILE_CLASSPATH_LIBRARY_R_CLASSES
                        )
                    )
            }
        }
        // Produce a single runtime jar from the AAR.
        val runtimeUsage: Usage = project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME)

        dependencies.registerTransform(
            AarToClassTransform::class.java
        ) { reg: TransformSpec<AarToClassTransform.Params> ->
            reg.from.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                AndroidArtifacts.ArtifactType.PROCESSED_AAR.type
            )
            reg.from.attribute(
                Usage.USAGE_ATTRIBUTE,
                runtimeUsage
            )
            reg.to.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                AndroidArtifacts.ArtifactType.CLASSES_JAR.type
            )
            reg.to.attribute(
                Usage.USAGE_ATTRIBUTE,
                runtimeUsage
            )
            reg.parameters { params: AarToClassTransform.Params ->
                params.forCompileUse.set(false)
                params.autoNamespaceDependencies
                    .set(autoNamespaceDependencies)
                params.generateRClassJar.set(false)
            }
        }
        if (globalScope.projectOptions[BooleanOption.ENABLE_PROGUARD_RULES_EXTRACTION]) {
            dependencies.registerTransform(
                ExtractProGuardRulesTransform::class.java
            ) { spec: TransformSpec<GenericTransformParameters> ->
                spec.parameters.projectName.set(projectName)
                spec.from.attribute(
                    ArtifactAttributes.ARTIFACT_FORMAT,
                    AndroidArtifacts.ArtifactType.PROCESSED_JAR.type
                )
                spec.to
                    .attribute(
                        ArtifactAttributes.ARTIFACT_FORMAT,
                        AndroidArtifacts.ArtifactType.UNFILTERED_PROGUARD_RULES.type
                    )
            }
        }
        dependencies.registerTransform(
            LibrarySymbolTableTransform::class.java
        ) { spec: TransformSpec<GenericTransformParameters> ->
            spec.parameters.projectName.set(projectName)
            spec.from.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                AndroidArtifacts.ArtifactType.EXPLODED_AAR.type
            )
            spec.to
                .attribute(
                    ArtifactAttributes.ARTIFACT_FORMAT,
                    AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME.type
                )
        }
        if (autoNamespaceDependencies) {
            dependencies.registerTransform(
                LibraryDefinedSymbolTableTransform::class.java
            ) { spec: TransformSpec<GenericTransformParameters> ->
                spec.parameters.projectName.set(projectName)
                spec.from.attribute(
                    ArtifactAttributes.ARTIFACT_FORMAT,
                    AndroidArtifacts.ArtifactType.EXPLODED_AAR.type
                )
                spec.to
                    .attribute(
                        ArtifactAttributes.ARTIFACT_FORMAT,
                        AndroidArtifacts.ArtifactType.DEFINED_ONLY_SYMBOL_LIST.type
                    )
            }
        }
        // Transform to go from external jars to CLASSES and JAVA_RES artifacts. This returns the
        // same exact file but with different types, since a jar file can contain both.
        for (classesOrResources in arrayOf(
            AndroidArtifacts.ArtifactType.CLASSES_JAR.type,
            AndroidArtifacts.ArtifactType.JAVA_RES.type
        )) {
            dependencies.registerTransform(IdentityTransform::class.java) { spec: TransformSpec<GenericTransformParameters?> ->
                spec.from.attribute(
                    ArtifactAttributes.ARTIFACT_FORMAT,
                    AndroidArtifacts.ArtifactType.PROCESSED_JAR.type
                )
                spec.to.attribute(
                    ArtifactAttributes.ARTIFACT_FORMAT,
                    classesOrResources
                )
            }
        }
        // The Kotlin Kapt plugin should query for PROCESSED_JAR, but it is currently querying for
        // JAR, so we need to have the workaround below to make it get PROCESSED_JAR. See
        // http://issuetracker.google.com/111009645.
        project.configurations.all { configuration: Configuration ->
            if (configuration.name.startsWith("kapt")) {
                configuration
                    .attributes
                    .attribute(
                        ArtifactAttributes.ARTIFACT_FORMAT,
                        AndroidArtifacts.ArtifactType.PROCESSED_JAR.type
                    )
            }
        }

        // When consuming classes from Android libraries, there are 2 transforms:
        //     1. `android-classes-directory` -> `android-classes`
        //     2. `android-classes-jar` -> `android-classes`
        // Currently Gradle always takes transform flow #1, which is ideal for incremental dexing.
        // (We don't know why Gradle does that, but IncrementalDesugaringTest should catch it if
        // this behavior changes.)
        dependencies.registerTransform(
            ClassesDirToClassesTransform::class.java,
            Action { spec: TransformSpec<GenericTransformParameters?> ->
                spec.from
                    .attribute(
                        ArtifactAttributes.ARTIFACT_FORMAT,
                        AndroidArtifacts.ArtifactType.CLASSES_DIR.type
                    )
                spec.to.attribute(
                    ArtifactAttributes.ARTIFACT_FORMAT,
                    AndroidArtifacts.ArtifactType.CLASSES.type
                )
            }
        )
        dependencies.registerTransform(
            IdentityTransform::class.java,
            Action { spec: TransformSpec<GenericTransformParameters?> ->
                spec.from.attribute(
                    ArtifactAttributes.ARTIFACT_FORMAT,
                    AndroidArtifacts.ArtifactType.CLASSES_JAR.type
                )
                spec.to.attribute(
                    ArtifactAttributes.ARTIFACT_FORMAT,
                    AndroidArtifacts.ArtifactType.CLASSES.type
                )
            }
        )

        // When consuming classes from Java libraries, there are 2 transforms:
        //     1. `java-classes-directory` -> `android-classes`
        //     2. `jar` -> `processed-jar` -> `android-classes-jar` -> `android-classes`
        // Currently Gradle always takes transform flow #2, which is not ideal for incremental
        // dexing.
        // TODO(147137579): Configure Gradle to take transform flow #1.
        dependencies.registerTransform(IdentityTransform::class.java) { spec: TransformSpec<GenericTransformParameters?> ->
            spec.from
                .attribute(
                    ArtifactAttributes.ARTIFACT_FORMAT,
                    ArtifactTypeDefinition.JVM_CLASS_DIRECTORY
                )
            spec.to.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                AndroidArtifacts.ArtifactType.CLASSES.type
            )
        }

        val schema = dependencies.attributesSchema
        // custom strategy for build-type and product-flavor.
        setBuildTypeStrategy(schema)
        setupFlavorStrategy(schema)
        setupModelStrategy(schema)
    }

    private fun setBuildTypeStrategy(schema: AttributesSchema) {
        // this is ugly but because the getter returns a very base class we have no choices.
        val dslBuildTypes = variantInputModel.buildTypes.values.convertTo(BuildType::class.java) {
                it.buildType
            }

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
                    AlternateDisambiguationRule.BuildTypeRule::class.java
                ) { config: ActionConfiguration ->
                    config.setParams(
                        alternateMap
                    )
                }
        }
    }

    private fun setupFlavorStrategy(schema: AttributesSchema) {
        // this is ugly but because the getter returns a very base class we have no choices.
        val flavors = variantInputModel.productFlavors.values.convertTo(ProductFlavor::class.java) {
                it.productFlavor
            }

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
        handleMissingDimensions(alternateMap, variantInputModel.defaultConfig.productFlavor)
        // now that we know we have all the fallbacks for each dimensions, we can create the
        // rule instances.
        for ((key, value) in alternateMap) {
            addFlavorStrategy(schema, key, value)
        }
    }

    private fun setupModelStrategy(attributesSchema: AttributesSchema) {
        setUp(attributesSchema)
    }

    private fun handleMissingDimensions(
        alternateMap: MutableMap<String, MutableMap<String, List<String>>>,
        flavor: BaseFlavor
    ) {
        val missingStrategies =
            flavor.missingDimensionStrategies
        if (missingStrategies.isNotEmpty()) {
            for ((dimension, value) in missingStrategies) {
                val dimensionMap = alternateMap.computeIfAbsent(dimension) { Maps.newHashMap() }
                dimensionMap[value.requested] = value.fallbacks
            }
        }
    }

    companion object {
        @JvmStatic
        fun addFlavorStrategy(
            schema: AttributesSchema,
            dimension: String,
            alternateMap: Map<String, List<String>>
        ) {
            val attr = Attribute.of(dimension, ProductFlavorAttr::class.java)
            val flavorStrategy = schema.attribute(attr)
            flavorStrategy
                .compatibilityRules
                .add(AlternateCompatibilityRule.ProductFlavorRule::class.java) {
                    it.setParams(alternateMap)
                }
            flavorStrategy
                .disambiguationRules
                .add(AlternateDisambiguationRule.ProductFlavorRule::class.java) {
                    it.setParams(alternateMap)
                }
        }
    }
}

private fun <F, T> Collection<F>.convertTo(
    convertedType: Class<T>,
    function: (F) -> T
): List<T> {
    return asSequence()
        .map(function)
        .filterIsInstance(convertedType)
        .toList()
}
