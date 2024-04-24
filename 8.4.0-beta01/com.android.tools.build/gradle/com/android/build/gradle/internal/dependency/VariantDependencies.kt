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

package com.android.build.gradle.internal.dependency

import com.android.Version
import com.android.build.api.attributes.AgpVersionAttr
import com.android.build.api.attributes.BuildTypeAttr
import com.android.build.api.attributes.ProductFlavorAttr
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.core.dsl.KmpComponentDslInfo
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ANDROID_RES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.APK
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.APKS_FROM_BUNDLE
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.BASE_MODULE_LINT_MODEL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.COMPILED_DEPENDENCIES_RESOURCES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_DEX
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_NAME
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_SHRUNK_JAVA_RES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.LINT_MODEL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.PACKAGED_DEPENDENCIES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType
import com.android.build.gradle.internal.publishing.PublishedConfigSpec
import com.android.build.gradle.internal.publishing.getAttributes
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.ComponentType
import com.android.builder.core.ComponentTypeImpl
import com.google.common.base.MoreObjects
import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.file.FileCollection
import org.gradle.api.specs.Spec
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import java.io.File
import java.util.concurrent.Callable
import java.util.function.Predicate

interface ResolutionResultProvider {
    fun getResolutionResult(configType: ConsumedConfigType): ResolutionResult

    fun getAdditionalArtifacts(configType: ConsumedConfigType, type: AdditionalArtifactType): ArtifactCollection
}
/**
 * Object that represents the dependencies of variant.
 *
 * The dependencies are expressed as composite Gradle configuration objects that extends
 * all the configuration objects of the "configs".
 *
 * It optionally contains the dependencies for a test config for the given config.
 */
class VariantDependencies internal constructor(
    private val variantName: String,
    private val componentType: ComponentType,
    val compileClasspath: Configuration,
    val runtimeClasspath: Configuration,
    private val sourceSetRuntimeConfigurations: Collection<Configuration>,
    val sourceSetImplementationConfigurations: Collection<Configuration>,
    private val elements: Map<PublishedConfigSpec, Configuration>,
    private val providedClasspath: Configuration?,
    val annotationProcessorConfiguration: Configuration?,
    private val reverseMetadataValuesConfiguration: Configuration?,
    val wearAppConfiguration: Configuration?,
    private val testedVariant: VariantCreationConfig?,
    private val project: Project,
    private val projectOptions: ProjectOptions,
    val isLibraryConstraintsApplied: Boolean,
    isSelfInstrumenting: Boolean,
): ResolutionResultProvider {

    // Never exclude artifacts for self-instrumenting, test-only modules.
    private val avoidExcludingArtifacts = componentType.isSeparateTestProject && isSelfInstrumenting

    init {
        check(!componentType.isTestComponent || testedVariant != null) {
            "testedVariantDependencies null for test component"
        }
    }

    fun getIncomingRuntimeDependencies(): Collection<Dependency> {
        val builder = ImmutableList.builder<Dependency>()
        for (classpath in sourceSetRuntimeConfigurations) {
            builder.addAll(classpath.incoming.dependencies)
        }
        return builder.build()
    }

    fun getElements(configSpec: PublishedConfigSpec): Configuration? {
        return elements[configSpec]
    }

    override fun toString(): String {
        return MoreObjects.toStringHelper(this).add("name", variantName).toString()
    }

    override fun getResolutionResult(configType: ConsumedConfigType): ResolutionResult = when (configType) {
        ConsumedConfigType.COMPILE_CLASSPATH -> compileClasspath.incoming.resolutionResult
        ConsumedConfigType.RUNTIME_CLASSPATH -> runtimeClasspath.incoming.resolutionResult
        else -> throw RuntimeException("Unsupported ConsumedConfigType value: $configType")
    }

    override fun getAdditionalArtifacts(
            configType: ConsumedConfigType,
            type: AdditionalArtifactType
    ): ArtifactCollection {
        val configuration = when (configType) {
            ConsumedConfigType.COMPILE_CLASSPATH -> compileClasspath
            ConsumedConfigType.RUNTIME_CLASSPATH -> runtimeClasspath
            else -> throw RuntimeException("Unsupported ConsumedConfigType value: $configType")
        }
        val docsType = when(type) {
            AdditionalArtifactType.SOURCE -> DocsType.SOURCES
            AdditionalArtifactType.JAVADOC -> DocsType.JAVADOC
            AdditionalArtifactType.SAMPLE -> SAMPLE_SOURCE_TYPE
        }

        val buildType = configuration.attributes.getAttribute(BuildTypeAttr.ATTRIBUTE)
        val flavorMap = configuration.attributes.keySet()
                .filter { it.type == ProductFlavorAttr::class.java }
                .associateWith { configuration.attributes.getAttribute(it) } as Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr>

        return configuration.incoming.artifactView { view ->
            view.isLenient = true
            view.withVariantReselection()
            val objects = project.objects

            view.attributes.apply {
                buildType?.let {
                    attribute(BuildTypeAttr.ATTRIBUTE, buildType)
                }
                flavorMap.entries.forEach {
                    attribute(it.key, it.value)
                }

                attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType::class.java, docsType))
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.DOCUMENTATION))
                attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class.java, LibraryElements.JAR))
                attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling::class.java, Bundling.EXTERNAL))
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
            }
        }.artifacts
    }

    @JvmOverloads
    fun getArtifactFileCollection(
        configType: ConsumedConfigType,
        scope: ArtifactScope,
        artifactType: AndroidArtifacts.ArtifactType,
        attributes: AndroidAttributes? = null
    ): FileCollection = getArtifactCollection(
        configType, scope, artifactType, attributes
    ).artifactFiles

    @JvmOverloads
    fun getArtifactCollection(
        configType: ConsumedConfigType,
        scope: ArtifactScope,
        artifactType: AndroidArtifacts.ArtifactType,
        attributes: AndroidAttributes? = null
    ): ArtifactCollection {
        var artifacts =
            computeArtifactCollection(configType, scope, artifactType, attributes)

        if (configType == ConsumedConfigType.RUNTIME_CLASSPATH
            && isArtifactTypeExcluded(artifactType)
        ) {
            val excludedDirectories = computeArtifactCollection(
                ConsumedConfigType.PROVIDED_CLASSPATH,
                ArtifactScope.PROJECT,
                PACKAGED_DEPENDENCIES,
                null
            ).artifactFiles
            artifacts = FilteredArtifactCollection(
                FilteringSpec(artifacts, excludedDirectories, project.objects)
            )
        }

        if (!configType.needsTestedComponents() || !componentType.isTestComponent) {
            return artifacts
        }

        // get the matching file collection for the tested variant, if any.
        if (testedVariant == null) {
            return artifacts
        }

        // For artifact that should not be duplicated between test APk and tested APK (e.g. classes)
        // we remove duplicates from test APK. More specifically, for androidTest variants for base
        // and dynamic features, we need to remove artifacts that are already packaged in the tested
        // variant. Also, we remove artifacts already packaged in base/features that the tested
        // feature depends on.
        if (!componentType.isApk) {
            // Don't filter unit tests.
            return artifacts
        }

        if (configType != ConsumedConfigType.RUNTIME_CLASSPATH) {
            // Only filter runtime classpath.
            return artifacts
        }

        if (testedVariant.componentType.isAar) {
            // Don't filter test APKs for library projects, as there is no tested APK.
            return artifacts
        }

        if (!isArtifactTypeSubtractedForInstrumentationTests(artifactType)) {
            return artifacts
        }

        if (testedVariant.componentType.isDynamicFeature) {
            // If we're in an androidTest for a dynamic feature we need to filter out artifacts from
            // the base and dynamic features this dynamic feature depends on.
            val excludedDirectories = testedVariant
                .variantDependencies
                .computeArtifactCollection(
                    ConsumedConfigType.PROVIDED_CLASSPATH,
                    ArtifactScope.PROJECT,
                    PACKAGED_DEPENDENCIES,
                    null
                )
                .artifactFiles
            artifacts = FilteredArtifactCollection(
                FilteringSpec(artifacts, excludedDirectories, project.objects)
            )
        }

        val testedArtifactCollection = testedVariant
            .variantDependencies
            .getArtifactCollection(configType, scope, artifactType, attributes)
        artifacts =
            SubtractingArtifactCollection(artifacts, testedArtifactCollection, project.objects)
        return artifacts
    }

    private fun isArtifactTypeExcluded(artifactType: AndroidArtifacts.ArtifactType): Boolean {
        return when {
            avoidExcludingArtifacts -> false
            componentType.isDynamicFeature ->
                artifactType != PACKAGED_DEPENDENCIES
                        && artifactType != APKS_FROM_BUNDLE
                        && artifactType != FEATURE_DEX
                        && artifactType != FEATURE_NAME
                        && artifactType != FEATURE_SHRUNK_JAVA_RES
                        && artifactType != LINT_MODEL
                        && artifactType != BASE_MODULE_LINT_MODEL
            componentType.isSeparateTestProject ->
                isArtifactTypeSubtractedForInstrumentationTests(artifactType)
            else -> false
        }
    }

    private fun getConfiguration(configType: ConsumedConfigType): Configuration {
        return when (configType) {
            ConsumedConfigType.COMPILE_CLASSPATH -> compileClasspath
            ConsumedConfigType.RUNTIME_CLASSPATH -> runtimeClasspath
            ConsumedConfigType.PROVIDED_CLASSPATH -> providedClasspath!!
            ConsumedConfigType.ANNOTATION_PROCESSOR -> annotationProcessorConfiguration!!
            ConsumedConfigType.REVERSE_METADATA_VALUES ->
                checkNotNull(reverseMetadataValuesConfiguration) {
                    "reverseMetadataValuesConfiguration is null"
                }
        }
    }

    fun getArtifactCollectionForToolingModel(
        configType: ConsumedConfigType,
        scope: ArtifactScope,
        artifactType: AndroidArtifacts.ArtifactType,
        additionalFilter: ((ComponentIdentifier) -> Boolean)? = null
    ): ArtifactCollection {
        return computeArtifactCollection(configType, scope, artifactType, null, additionalFilter)
    }

    private fun computeArtifactCollection(
        configType: ConsumedConfigType,
        scope: ArtifactScope,
        artifactType: AndroidArtifacts.ArtifactType,
        attributes: AndroidAttributes?,
        additionalFilter: ((ComponentIdentifier) -> Boolean)? = null
    ): ArtifactCollection {
        checkComputeArtifactCollectionArguments(configType, scope, artifactType)

        val configuration = getConfiguration(configType)
        val attributesAction =
            Action { container: AttributeContainer ->
                container.attribute(AndroidArtifacts.ARTIFACT_TYPE, artifactType.type)
                artifactType.getAttributes { type, name ->
                    project.objects.named(type, name)
                }.addAttributesToContainer(container)
                attributes?.addAttributesToContainer(container)
            }
        val filter = getComponentFilter(scope, additionalFilter)
        val lenientMode =
            projectOptions[BooleanOption.IDE_BUILD_MODEL_ONLY] || projectOptions[BooleanOption.IDE_BUILD_MODEL_ONLY_V2]

        return configuration
            .incoming
            .artifactView { config: ArtifactView.ViewConfiguration ->
                config.attributes(attributesAction)
                filter?.let { config.componentFilter(it) }
                // TODO somehow read the unresolved dependencies?
                config.lenient(lenientMode)
            }
            .artifacts
    }

    fun computeLocalFileDependencies(
        services: VariantServices,
        filePredicate: Predicate<File>
    ): FileCollection {
        // Get a list of local file dependencies. There is currently no API to filter the
        // files here, so we need to filter it in the return statement below. That means that if,
        // for example, filePredicate filters out all files but jars in the return statement, but an
        // AarProducerTask produces an aar, then the returned FileCollection contains only jars but
        // still has AarProducerTask as a dependency.
        val dependencies = Callable {
            runtimeClasspath
                .allDependencies
                .filterIsInstance<FileCollectionDependency>()
                // Extract the wrapped FileCollection because FileCollectionDependency will
                // no longer implement Buildable in 9.0
                .map { it.files }
        }

        // Create a file collection builtBy the dependencies.  The files are resolved later.
        return if (componentType.isDynamicFeature) {
            val excludedDirectories = computeArtifactCollection(
                ConsumedConfigType.PROVIDED_CLASSPATH,
                ArtifactScope.PROJECT,
                AndroidArtifacts.ArtifactType.PACKAGED_DEPENDENCIES,
                null
            ).artifactFiles

            services.fileCollection(
                Callable {
                    excludedDirectories.elements.map { excludedDirectoriesSet ->
                        val excludedDirectoriesContent = excludedDirectoriesSet.asSequence()
                            .filter { it.asFile.isFile }
                            .flatMapTo(HashSet()) { it.asFile.readLines(Charsets.UTF_8).asSequence() }

                        dependencies.call()
                            .flatMap { it.files }
                            .filter {
                                filePredicate.test(it) &&
                                        !excludedDirectoriesContent.contains(it.absolutePath)
                            }
                    }
                }).builtBy(dependencies).builtBy(excludedDirectories.buildDependencies)
        } else {
            services.fileCollection(Callable {
                dependencies.call()
                    .flatMap { it.files }
                    .filter { filePredicate.test(it) }
            }).builtBy(dependencies)
        }
    }

    companion object {
        const val CONFIG_NAME_ANDROID_APIS = "androidApis"
        const val CONFIG_NAME_LINTCHECKS = "lintChecks"
        const val CONFIG_NAME_LINTPUBLISH = "lintPublish"
        const val CONFIG_NAME_TESTED_APKS = "testedApks"
        const val CONFIG_NAME_CORE_LIBRARY_DESUGARING = "coreLibraryDesugaring"
        const val SAMPLE_SOURCE_TYPE = "samplessources"

        @Deprecated("")
        const val CONFIG_NAME_FEATURE = "feature"

        fun createForKotlinMultiplatform(
            project: Project,
            projectOptions: ProjectOptions,
            dslInfo: KmpComponentDslInfo,
            apiClasspath: Configuration,
            compileClasspath: Configuration,
            runtimeClasspath: Configuration,
            apiElements: Configuration?,
            runtimeElements: Configuration?,
            sourcesElements: Configuration?,
            apiPublication: Configuration?,
            runtimePublication: Configuration?,
            sourcesPublication: Configuration?,
        ): VariantDependencies {
            val incomingConfigurations = listOf(compileClasspath, runtimeClasspath)
            val outgoingConfigurations = listOfNotNull(
                apiElements, runtimeElements, sourcesElements, apiPublication, runtimePublication, sourcesPublication
            )
            val publicationConfigurations = listOfNotNull(
                apiPublication, runtimePublication, sourcesPublication
            )

            // This is set to be able to consume artifacts published with the library plugin.
            // For the artifacts of the new android multiplatform plugin, kotlin plugin has a compatibility rules that
            // equates `androidJvm` with `jvm`, together with the `TargetJvmEnvironment` we are able to select the
            // android variant.
            incomingConfigurations.forEach {
                it.attributes.attribute(
                   KotlinPlatformType.attribute,
                   KotlinPlatformType.androidJvm
                )

                it.attributes.attribute(
                    Category.CATEGORY_ATTRIBUTE,
                    project.objects.named(Category::class.java, Category.LIBRARY)
                )
            }

            project.objects.named(
                TargetJvmEnvironment::class.java,
                TargetJvmEnvironment.ANDROID
            ).let { androidTarget ->
                // TODO(b/289214845): Figure out with JB which attribute to add to disambiguate
                //  android from jvm for publication as the kotlin plugin doesn't publish with
                //  target jvm environment attribute.
                (incomingConfigurations + outgoingConfigurations).forEach {
                    it.attributes.attribute(
                        TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                        androidTarget
                    )
                }
            }

            project.objects.named(
                AgpVersionAttr::class.java,
                Version.ANDROID_GRADLE_PLUGIN_VERSION
            ).let { agpVersion ->
                // Add the agp version attribute to all configurations but the publication
                (incomingConfigurations + outgoingConfigurations - publicationConfigurations.toSet()).forEach {
                    it.attributes.attribute(AgpVersionAttr.ATTRIBUTE, agpVersion)
                }
            }

            listOfNotNull(apiElements, apiPublication).forEach {
                it.extendsFrom(apiClasspath)
            }

            listOfNotNull(runtimeElements, runtimePublication).forEach {
                it.extendsFrom(runtimeClasspath)
            }

            // add dependency on main project
            if (dslInfo.componentType.isTestComponent) {
                compileClasspath.dependencies.add(project.dependencies.create(project))
                runtimeClasspath.dependencies.add(project.dependencies.create(project))
            }

            project.objects.named(
                LibraryElements::class.java,
                AndroidArtifacts.ArtifactType.AAR.type
            ).let { aar ->
                apiPublication?.attributes?.attribute(
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    aar
                )
                runtimePublication?.attributes?.attribute(
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    aar
                )
            }

            val elements = mutableMapOf<PublishedConfigSpec, Configuration>()

            runtimeElements?.let {
                elements[PublishedConfigSpec(PublishedConfigType.RUNTIME_ELEMENTS)] = runtimeElements
            }
            apiElements?.let {
                elements[PublishedConfigSpec(PublishedConfigType.API_ELEMENTS)] = apiElements
            }

            apiPublication?.let {
                elements[PublishedConfigSpec(
                    PublishedConfigType.API_PUBLICATION, dslInfo.componentIdentity.name
                )] = apiPublication
            }
            runtimePublication?.let {
                elements[PublishedConfigSpec(
                    PublishedConfigType.RUNTIME_PUBLICATION, dslInfo.componentIdentity.name
                )] = runtimePublication
            }

            return VariantDependencies(
                variantName = dslInfo.componentIdentity.name,
                componentType = ComponentTypeImpl.KMP_ANDROID,
                compileClasspath = compileClasspath,
                runtimeClasspath = runtimeClasspath,
                sourceSetRuntimeConfigurations = emptySet(),
                sourceSetImplementationConfigurations = emptySet(),
                elements = elements,
                providedClasspath = null,
                annotationProcessorConfiguration = null,
                reverseMetadataValuesConfiguration = null,
                wearAppConfiguration = null,
                testedVariant = null,
                project = project,
                projectOptions = projectOptions,
                isLibraryConstraintsApplied = false,
                isSelfInstrumenting = false
            )
        }

        private fun isArtifactTypeSubtractedForInstrumentationTests(
            artifactType: AndroidArtifacts.ArtifactType
        ): Boolean {
            return (artifactType != ANDROID_RES && artifactType != COMPILED_DEPENDENCIES_RESOURCES)
        }

        private fun checkComputeArtifactCollectionArguments(
            configType: ConsumedConfigType,
            scope: ArtifactScope,
            artifactType: AndroidArtifacts.ArtifactType
        ) {
            when (artifactType) {
                PACKAGED_DEPENDENCIES ->
                    check(
                        configType == ConsumedConfigType.PROVIDED_CLASSPATH
                                || configType == ConsumedConfigType.REVERSE_METADATA_VALUES
                    ) {
                        "Packaged dependencies must only be requested from the PROVIDED_CLASSPATH or REVERSE_METADATA_VALUES"
                    }
                else -> {
                    // No validation
                }
            }
            when (configType) {
                ConsumedConfigType.PROVIDED_CLASSPATH ->
                    check(artifactType == PACKAGED_DEPENDENCIES || artifactType == APK) {
                        "Provided classpath must only be used for from the PACKAGED_DEPENDENCIES and APKS"
                    }
                else -> {
                    // No validation
                }
            }
        }

        private fun getComponentFilter(
            scope: ArtifactScope,
            additionalFilter: ((ComponentIdentifier) -> Boolean)? = null
        ): Spec<ComponentIdentifier>? {
            if (scope != ArtifactScope.ALL) {
                check(additionalFilter == null) {
                    "Additional filter is only respected when scope is ALL"
                }
            }
            return when (scope) {
                ArtifactScope.ALL -> additionalFilter?.let { filter -> Spec { filter(it)} }
                ArtifactScope.EXTERNAL ->
                    // since we want both Module dependencies and file based dependencies in this case
                    // the best thing to do is search for non ProjectComponentIdentifier.
                    Spec { it !is ProjectComponentIdentifier }
                ArtifactScope.PROJECT -> Spec { it is ProjectComponentIdentifier }
                ArtifactScope.REPOSITORY_MODULE -> Spec { it is ModuleComponentIdentifier }
                ArtifactScope.FILE -> Spec {
                    !(it is ProjectComponentIdentifier || it is ModuleComponentIdentifier)
                }
            }
        }
    }
}

enum class AdditionalArtifactType {
    JAVADOC,
    SOURCE,
    SAMPLE,
}
