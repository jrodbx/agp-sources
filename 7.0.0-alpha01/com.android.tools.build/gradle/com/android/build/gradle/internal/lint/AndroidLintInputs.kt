/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.lint

import com.android.Version
import com.android.build.api.artifact.ArtifactType
import com.android.build.api.component.impl.ComponentImpl
import com.android.build.api.component.impl.UnitTestImpl
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.dsl.LintOptions
import com.android.build.gradle.internal.ide.ModelBuilder
import com.android.build.gradle.internal.ide.dependencies.ArtifactCollectionsInputs
import com.android.build.gradle.internal.ide.dependencies.ArtifactHandler
import com.android.build.gradle.internal.ide.dependencies.LibraryDependencyCacheBuildService
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService
import com.android.build.gradle.internal.ide.dependencies.computeBuildMapping
import com.android.build.gradle.internal.ide.dependencies.getDependencyGraphBuilder
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.VariantType
import com.android.builder.core.VariantTypeImpl
import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.IssueReporter
import com.android.builder.model.ApiVersion
import com.android.builder.model.SourceProvider
import com.android.ide.common.repository.GradleVersion
import com.android.sdklib.AndroidVersion
import com.android.tools.lint.model.DefaultLintModelAndroidArtifact
import com.android.tools.lint.model.DefaultLintModelBuildFeatures
import com.android.tools.lint.model.DefaultLintModelJavaArtifact
import com.android.tools.lint.model.DefaultLintModelLibraryResolver
import com.android.tools.lint.model.DefaultLintModelLintOptions
import com.android.tools.lint.model.DefaultLintModelMavenName
import com.android.tools.lint.model.DefaultLintModelModule
import com.android.tools.lint.model.DefaultLintModelResourceField
import com.android.tools.lint.model.DefaultLintModelSourceProvider
import com.android.tools.lint.model.DefaultLintModelVariant
import com.android.tools.lint.model.LintModelAndroidArtifact
import com.android.tools.lint.model.LintModelBuildFeatures
import com.android.tools.lint.model.LintModelDependencies
import com.android.tools.lint.model.LintModelFactory
import com.android.tools.lint.model.LintModelJavaArtifact
import com.android.tools.lint.model.LintModelLibrary
import com.android.tools.lint.model.LintModelModule
import com.android.tools.lint.model.LintModelModuleType
import com.android.tools.lint.model.LintModelNamespacingMode
import com.android.tools.lint.model.LintModelSourceProvider
import com.android.tools.lint.model.LintModelVariant
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.HasConvention
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile
import java.io.File

abstract class ProjectInputs {

    @get:Input
    abstract val projectDirectoryPath: Property<String>

    @get:Input
    abstract val projectGradlePath: Property<String>

    @get:Input
    abstract val projectType: Property<LintModelModuleType>

    @get:Input
    abstract val mavenGroupId: Property<String>

    @get:Input
    abstract val mavenArtifactId: Property<String>

    @get:Input
    abstract val buildDirectoryPath: Property<String>

    @get:Input
    abstract val lintOptions: Property<DefaultLintModelLintOptions>

    @get:Input
    @get:Optional
    abstract val resourcePrefix: Property<String>

    @get:Input
    abstract val dynamicFeatures: ListProperty<String>

    @get:Classpath
    abstract val bootClasspath: ConfigurableFileCollection

    @get:Input
    abstract val javaSourceLevel: Property<JavaVersion>

    @get:Input
    abstract val compileTarget: Property<String>

    /**
     * True if none of the build types used by this module have enabled shrinking,
     * or false if at least one variant's build type is known to use shrinking.
     */
    @get:Input
    abstract val neverShrinking: Property<Boolean>

    internal fun initialize(variant: VariantWithTests) {
        val creationConfig = variant.main
        val project = creationConfig.globalScope.project
        val extension = creationConfig.globalScope.extension
        initializeFromProject(project)
        projectType.setDisallowChanges(creationConfig.variantType.toLintModelModuleType())

        lintOptions.setDisallowChanges(
            LintModelFactory.getLintOptions(extension.lintOptions))
        resourcePrefix.setDisallowChanges(extension.resourcePrefix)

        if (extension is BaseAppModuleExtension) {
            dynamicFeatures.setDisallowChanges(extension.dynamicFeatures)
        }
        dynamicFeatures.disallowChanges()

        bootClasspath.fromDisallowChanges(creationConfig.globalScope.bootClasspath)
        javaSourceLevel.setDisallowChanges(extension.compileOptions.sourceCompatibility)
        compileTarget.setDisallowChanges(extension.compileSdkVersion)
        // `neverShrinking` is about all variants, so look back to the DSL
        neverShrinking.setDisallowChanges(extension.buildTypes.none { it.isMinifyEnabled })
    }

    internal fun initializeForStandalone(project: Project, javaConvention: JavaPluginConvention, dslLintOptions: LintOptions) {
        initializeFromProject(project)
        projectType.setDisallowChanges(LintModelModuleType.JAVA_LIBRARY)
        val lintModelLintOptions = project.provider { LintModelFactory.getLintOptions(dslLintOptions) }
        lintOptions.setDisallowChanges(lintModelLintOptions)
        resourcePrefix.setDisallowChanges("")
        dynamicFeatures.setDisallowChanges(setOf())
        val mainSourceSet = javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        val javaCompileTask = project.tasks.named(
            mainSourceSet.compileJavaTaskName,
            JavaCompile::class.java
        )
        bootClasspath.fromDisallowChanges(javaCompileTask.map { it.options.bootstrapClasspath ?: project.files() })
        javaSourceLevel.setDisallowChanges(javaConvention.sourceCompatibility)
        compileTarget.setDisallowChanges("")
        neverShrinking.setDisallowChanges(true)
    }

    private fun initializeFromProject(project: Project) {
        projectDirectoryPath.setDisallowChanges(project.projectDir.absolutePath)
        projectGradlePath.setDisallowChanges(project.path)
        mavenGroupId.setDisallowChanges(project.group.toString())
        mavenArtifactId.setDisallowChanges(project.name)
        buildDirectoryPath.setDisallowChanges(project.layout.buildDirectory.map { it.asFile.absolutePath })

    }

    internal fun convertToLintModelModule(): LintModelModule {
        return DefaultLintModelModule(
            loader = null,
            dir = File(projectDirectoryPath.get()),
            modulePath = projectGradlePath.get(),
            type = projectType.get(),
            mavenName = DefaultLintModelMavenName(
                mavenGroupId.get(),
                mavenArtifactId.get()
            ),
            gradleVersion = GradleVersion.tryParse(Version.ANDROID_GRADLE_PLUGIN_VERSION),
            buildFolder = File(buildDirectoryPath.get()),
            lintOptions = lintOptions.get(),
            lintRuleJars = listOf(),
            resourcePrefix = resourcePrefix.orNull,
            dynamicFeatures = dynamicFeatures.get(),
            bootClassPath = bootClasspath.files.toList(),
            javaSourceLevel = javaSourceLevel.get().toString(),
            compileTarget = compileTarget.get(),
            variants = listOf(),
            neverShrinking = neverShrinking.get(),
            oldProject = null
        )
    }
}

internal fun VariantType.toLintModelModuleType(): LintModelModuleType {
    return when (this) {
        // FIXME add other types
        VariantTypeImpl.BASE_APK -> LintModelModuleType.APP
        VariantTypeImpl.LIBRARY -> LintModelModuleType.LIBRARY
        VariantTypeImpl.OPTIONAL_APK -> LintModelModuleType.DYNAMIC_FEATURE
        VariantTypeImpl.TEST_APK -> LintModelModuleType.TEST
        else -> throw RuntimeException("Unsupported VariantTypeImpl value")
    }
}


/**
 * Inputs for the variant.
 */
abstract class VariantInputs {

    @get:Input
    abstract val name: Property<String>

    @get:Input
    abstract val minifiedEnabled: Property<Boolean>

    @get:Nested
    abstract val mainArtifact: AndroidArtifactInput

    @get:Nested
    @get:Optional
    abstract val testArtifact: Property<JavaArtifactInput>

    @get:Nested
    @get:Optional
    abstract val androidTestArtifact: Property<AndroidArtifactInput>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val mergedManifest: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val manifestMergeReport: RegularFileProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:Nested
    @get:Optional
    abstract val minSdkVersion: SdkVersionInput

    @get:Nested
    @get:Optional
    abstract val targetSdkVersion: SdkVersionInput

    @get:Input
    abstract val resValues: ListProperty<DefaultLintModelResourceField>

    @get:Input
    abstract val manifestPlaceholders: MapProperty<String, String>

    @get:Input
    abstract val resourceConfigurations: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val proguardFiles: ListProperty<RegularFile>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val consumerProguardFiles: ListProperty<File>

    @get:Nested
    abstract val sourceProviders: ListProperty<SourceProviderInput>

    @get:Nested
    abstract val testSourceProviders: ListProperty<SourceProviderInput>

    @get:Input
    abstract val debuggable: Property<Boolean>

    @get:Nested
    abstract val buildFeatures: BuildFeaturesInput

    @get:Internal
    abstract val libraryDependencyCacheBuildService: Property<LibraryDependencyCacheBuildService>

    @get:Internal
    abstract val mavenCoordinatesCache: Property<MavenCoordinatesCacheBuildService>


    fun initialize(variantWithTests: VariantWithTests, checkDependencies: Boolean) {
        val creationConfig = variantWithTests.main
        name.setDisallowChanges(creationConfig.name)
        minifiedEnabled.setDisallowChanges(creationConfig.codeShrinker != null)
        mainArtifact.initialize(creationConfig as ComponentImpl, checkDependencies)

        testArtifact.setDisallowChanges(
            variantWithTests.unitTest?.let { unitTest ->
                creationConfig.services.newInstance(JavaArtifactInput::class.java)
                    .initialize(unitTest as UnitTestImpl, checkDependencies)
            }
        )

        androidTestArtifact.setDisallowChanges(
            variantWithTests.androidTest?.let { androidTest ->
                creationConfig.services.newInstance(AndroidArtifactInput::class.java)
                    .initialize(androidTest as ComponentImpl, checkDependencies)
        })
        mergedManifest.setDisallowChanges(
            creationConfig.artifacts.get(ArtifactType.MERGED_MANIFEST)
        )
        manifestMergeReport.setDisallowChanges(
            creationConfig.artifacts.get(InternalArtifactType.MANIFEST_MERGE_REPORT)
        )
        packageName.setDisallowChanges(creationConfig.packageName)

        minSdkVersion.initialize(creationConfig.minSdkVersion)

        targetSdkVersion.initialize(creationConfig.targetSdkVersion)

        // FIXME resvalue
        if (creationConfig is ApkCreationConfig) {
            manifestPlaceholders.setDisallowChanges(
                creationConfig.manifestPlaceholders
            )
        }

        resourceConfigurations.setDisallowChanges(creationConfig.resourceConfigurations)

        sourceProviders.setDisallowChanges(creationConfig.variantSources.sortedSourceProviders.map { sourceProvider ->
            creationConfig.services.newInstance(SourceProviderInput::class.java).initialize(sourceProvider)
        })
        // FIXME proguardFiles
        // FIXME consumerProguardFiles

        // FIXME testSourceProviders
        debuggable.setDisallowChanges(
            if (creationConfig is ApkCreationConfig) {
                creationConfig.debuggable
            } else true
        )
        buildFeatures.initialize(creationConfig)
        libraryDependencyCacheBuildService.setDisallowChanges(getBuildService(creationConfig.services.buildServiceRegistry))
        mavenCoordinatesCache.setDisallowChanges(getBuildService(creationConfig.services.buildServiceRegistry))
    }

    internal fun initializeForStandalone(project: Project, javaConvention: JavaPluginConvention, projectOptions: ProjectOptions, customLintChecks: FileCollection, dslLintOptions: LintOptions) {
        val mainSourceSet = javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        val testSourceSet = javaConvention.sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME)

        name.setDisallowChanges(mainSourceSet.name)
        mainArtifact.initializeForStandalone(project, projectOptions, mainSourceSet)
        testArtifact.setDisallowChanges(project.objects.newInstance(JavaArtifactInput::class.java).initializeForStandalone(project, projectOptions, mainSourceSet))
        androidTestArtifact.disallowChanges()
        packageName.setDisallowChanges("")
        minSdkVersion.initializeEmpty()
        targetSdkVersion.initializeEmpty()
        manifestPlaceholders.disallowChanges()
        resourceConfigurations.disallowChanges()
        debuggable.setDisallowChanges(true)
        mergedManifest.setDisallowChanges(null)
        manifestMergeReport.setDisallowChanges(null)
        minifiedEnabled.setDisallowChanges(false)
        sourceProviders.setDisallowChanges(listOf(
            project.objects.newInstance(SourceProviderInput::class.java)
                .initializeForStandalone(project, mainSourceSet, unitTestOnly = false)
        ))
        testSourceProviders.setDisallowChanges(listOf(
            project.objects.newInstance(SourceProviderInput::class.java)
                .initializeForStandalone(project, testSourceSet, unitTestOnly = false)
        ))
        buildFeatures.initializeForStandalone()
        libraryDependencyCacheBuildService.setDisallowChanges(getBuildService(project.gradle.sharedServices))
        mavenCoordinatesCache.setDisallowChanges(getBuildService(project.gradle.sharedServices))
    }

    fun toLintModel(module: LintModelModule): LintModelVariant {
        val dependencyCaches = DependencyCaches(
            libraryDependencyCacheBuildService.get().localJarCache,
            mavenCoordinatesCache.get().cache)

        val checkDependencies = module.lintOptions.checkDependencies
        return DefaultLintModelVariant(
            module,
            name.get(),
            useSupportLibraryVectorDrawables = false,
            mainArtifact = mainArtifact.toLintModel(dependencyCaches, checkDependencies),
            testArtifact = testArtifact.orNull?.toLintModel(dependencyCaches, checkDependencies),
            androidTestArtifact = androidTestArtifact.orNull?.toLintModel(dependencyCaches, checkDependencies),
            mergedManifest = mergedManifest.orNull?.asFile,
            manifestMergeReport = manifestMergeReport.orNull?.asFile,
            `package` = packageName.get(),
            minSdkVersion = minSdkVersion.toLintModel(),
            targetSdkVersion = targetSdkVersion.toLintModel(),
            resValues = resValues.get().associateBy { it.name },
            manifestPlaceholders = manifestPlaceholders.get(),
            resourceConfigurations = resourceConfigurations.get(),
            proguardFiles = proguardFiles.get().map { it.asFile },
            consumerProguardFiles = consumerProguardFiles.get(),
            sourceProviders = sourceProviders.get().map { it.toLintModel() },
            testSourceProviders = listOf(), //FIXME
            debuggable = debuggable.get(),
            shrinkable = false, //FIXME
            buildFeatures = buildFeatures.toLintModel(),
            libraryResolver = DefaultLintModelLibraryResolver(dependencyCaches.libraryMap),
            oldVariant = null
        )
    }

}

abstract class BuildFeaturesInput {
    @get:Input
    abstract val viewBinding: Property<Boolean>

    @get:Input
    abstract val coreLibraryDesugaringEnabled: Property<Boolean>

    @get:Input
    abstract val namespacingMode: Property<LintModelNamespacingMode>

    fun initialize(creationConfig: ConsumableCreationConfig) {
        viewBinding.setDisallowChanges(creationConfig.buildFeatures.viewBinding)
        coreLibraryDesugaringEnabled.setDisallowChanges(creationConfig.isCoreLibraryDesugaringEnabled)
        namespacingMode.setDisallowChanges(
            if (creationConfig.globalScope.extension.aaptOptions.namespaced) {
                LintModelNamespacingMode.DISABLED
            } else {
                LintModelNamespacingMode.REQUIRED
            }
        )
    }
    fun initializeForStandalone() {
        viewBinding.setDisallowChanges(false)
        coreLibraryDesugaringEnabled.setDisallowChanges(false)
        namespacingMode.setDisallowChanges(LintModelNamespacingMode.DISABLED)
    }

    fun toLintModel(): LintModelBuildFeatures {
        return DefaultLintModelBuildFeatures(
            viewBinding.get(),
            coreLibraryDesugaringEnabled.get(),
            namespacingMode.get(),
        )
    }
}

abstract class SourceProviderInput {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val manifestFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val javaDirectories: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val resDirectories: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val assetsDirectories: ConfigurableFileCollection

    @get:Input
    abstract val debugOnly: Property<Boolean>

    @get:Input
    abstract val unitTestOnly: Property<Boolean>

    @get:Input
    abstract val instrumentationTestOnly: Property<Boolean>

    internal fun initialize(sourceProvider: SourceProvider): SourceProviderInput {
        this.manifestFile.set(sourceProvider.manifestFile)
        var javaDirectories = sourceProvider.javaDirectories
        (sourceProvider as HasConvention).convention.plugins["kotlin"]?.let { kotlinSourceSet ->
            val sourceDirectorySet =
                kotlinSourceSet.javaClass.getDeclaredMethod("getKotlin")
                    .invoke(kotlinSourceSet) as SourceDirectorySet
            // Kotlin and java source directories may overlap, so de-duplicate
            javaDirectories = sourceDirectorySet.srcDirs.plus(javaDirectories)
        }
        this.javaDirectories.fromDisallowChanges(javaDirectories)
        this.resDirectories.fromDisallowChanges(sourceProvider.resDirectories)
        this.assetsDirectories.fromDisallowChanges(sourceProvider.assetsDirectories)
        this.debugOnly.setDisallowChanges(false) //TODO
        this.unitTestOnly.setDisallowChanges(false) //TODO
        this.instrumentationTestOnly.setDisallowChanges(false) //TODO
        return this
    }

    internal fun initializeForStandalone(project: Project, sourceSet: SourceSet, unitTestOnly: Boolean): SourceProviderInput {
        val fakeManifestFile =
            project.layout.buildDirectory.file("fakeAndroidManifest/${sourceSet.name}/AndroidManifest.xml")
        this.manifestFile.setDisallowChanges(fakeManifestFile)
        this.javaDirectories.fromDisallowChanges(project.provider { sourceSet.allSource.srcDirs })
        this.resDirectories.disallowChanges()
        this.assetsDirectories.disallowChanges()
        this.debugOnly.setDisallowChanges(false)
        this.unitTestOnly.setDisallowChanges(unitTestOnly)
        this.instrumentationTestOnly.setDisallowChanges(false)
        return this
    }

    internal fun toLintModel(): LintModelSourceProvider {
        return DefaultLintModelSourceProvider(
            manifestFile = manifestFile.get().asFile,
            javaDirectories = javaDirectories.files.toList(),
            resDirectories = resDirectories.files.toList(),
            assetsDirectories = assetsDirectories.files.toList(),
            debugOnly = debugOnly.get(),
            unitTestOnly = unitTestOnly.get(),
            instrumentationTestOnly = instrumentationTestOnly.get(),
        )
    }
}

/**
 * Inputs for an SdkVersion. This is used by [VariantInputs] for min/target SDK Version
 */
abstract class SdkVersionInput {

    @get:Input
    abstract val apiLevel: Property<Int>

    @get:Input
    @get:Optional
    abstract val codeName: Property<String?>

    internal fun initialize(version: ApiVersion) {
        apiLevel.setDisallowChanges(version.apiLevel)
        codeName.setDisallowChanges(version.codename)
    }

    internal fun initialize(version: com.android.build.api.variant.AndroidVersion) {
        apiLevel.setDisallowChanges(version.apiLevel)
        codeName.setDisallowChanges(version.codename)
    }

    internal fun initializeEmpty() {
        apiLevel.setDisallowChanges(-1)
        codeName.setDisallowChanges("")
    }

    internal fun toLintModel(): AndroidVersion? {
        val api = apiLevel.get()
        if (api <= 0) {
            return null
        }
        return AndroidVersion(api, codeName.orNull)
    }
}

/**
 * Inputs for an Android Artifact. This is used by [VariantInputs] for the main and AndroidTest
 * artifacts.
 */
abstract class AndroidArtifactInput : ArtifactInput() {

    @get:Input
    abstract val applicationId: Property<String>

    @get:Internal
    abstract val generatedSourceFolders: ListProperty<File>

    @get:Internal
    abstract val generatedResourceFolders: ListProperty<File>

    fun initialize(componentImpl: ComponentImpl, checkDependencies: Boolean): AndroidArtifactInput {
        applicationId.setDisallowChanges(componentImpl.applicationId)
        generatedSourceFolders.setDisallowChanges(ModelBuilder.getGeneratedSourceFolders(componentImpl))
        generatedResourceFolders.setDisallowChanges(ModelBuilder.getGeneratedResourceFolders(componentImpl))
        classesOutputDirectories.from(componentImpl.artifacts.get(InternalArtifactType.JAVAC))

        classesOutputDirectories.from(
            componentImpl.variantData.allPreJavacGeneratedBytecode
        )
        classesOutputDirectories.from(componentImpl.variantData.allPostJavacGeneratedBytecode)
        classesOutputDirectories.from(componentImpl
            .getCompiledRClasses(
                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH))
        classesOutputDirectories.disallowChanges()
        if (!checkDependencies) {
            projectDependencyExplodedAars =
                componentImpl.variantDependencies.getArtifactCollectionForToolingModel(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    AndroidArtifacts.ArtifactType.LOCAL_EXPLODED_AAR_FOR_LINT
                )
        }

        artifactCollectionsInputs.setDisallowChanges(ArtifactCollectionsInputs(
            variantDependencies = componentImpl.variantDependencies,
            projectPath = componentImpl.globalScope.project.path,
            variantName = componentImpl.name,
            runtimeType = ArtifactCollectionsInputs.RuntimeType.FULL,
            buildMapping = componentImpl.globalScope.project.gradle.computeBuildMapping(),
            mavenCoordinatesCache = getBuildService(componentImpl.services.buildServiceRegistry)
        ))
        return this
    }

    fun initializeForStandalone(project: Project, projectOptions: ProjectOptions, sourceSet: SourceSet) {
        applicationId.setDisallowChanges("")
        generatedSourceFolders.setDisallowChanges(listOf())
        generatedResourceFolders.setDisallowChanges(listOf())
        classesOutputDirectories.fromDisallowChanges(sourceSet.output.classesDirs)

        val variantDependencies = VariantDependencies(
            variantName = sourceSet.name,
            variantType = VariantTypeImpl.JAVA_LIBRARY,
            compileClasspath = project.configurations.getByName(sourceSet.compileClasspathConfigurationName),
            runtimeClasspath = project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName),
            sourceSetRuntimeConfigurations = listOf(),
            sourceSetImplementationConfigurations = listOf(),
            elements = mapOf(),
            providedClasspath = project.configurations.getByName(sourceSet.compileOnlyConfigurationName),
            annotationProcessorConfiguration = project.configurations.getByName(sourceSet.annotationProcessorConfigurationName),
            reverseMetadataValuesConfiguration = null,
            wearAppConfiguration = null,
            testedVariant = null,
            project = project,
            projectOptions = projectOptions
        )
        artifactCollectionsInputs.setDisallowChanges(ArtifactCollectionsInputs(
            variantDependencies = variantDependencies,
            projectPath = project.path,
            variantName = sourceSet.name,
            runtimeType = ArtifactCollectionsInputs.RuntimeType.FULL,
            buildMapping = project.gradle.computeBuildMapping(),
            mavenCoordinatesCache = getBuildService(project.gradle.sharedServices)
        ))
    }

    internal fun toLintModel(dependencyCaches: DependencyCaches, checkDependencies: Boolean): LintModelAndroidArtifact {
        return DefaultLintModelAndroidArtifact(
            applicationId.get(),
            generatedResourceFolders.get(),
            generatedSourceFolders.get(),
            classesOutputDirectories.files.toList(),
            computeDependencies(dependencyCaches, checkDependencies)
        )
    }
}

/**
 * Inputs for a Java Artifact. This is used by [VariantInputs] for the unit test artifact.
 */
abstract class JavaArtifactInput : ArtifactInput() {

    fun initialize(unitTestImpl: UnitTestImpl, checkDependencies: Boolean): JavaArtifactInput {
        classesOutputDirectories.from(
            unitTestImpl.artifacts.get(InternalArtifactType.JAVAC)
        )
        classesOutputDirectories.from(
            unitTestImpl.variantData.allPreJavacGeneratedBytecode
        )
        classesOutputDirectories.from(unitTestImpl.variantData.allPostJavacGeneratedBytecode)
        classesOutputDirectories.from(unitTestImpl
            .getCompiledRClasses(
                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH))
        classesOutputDirectories.disallowChanges()
        if (!checkDependencies) {
            projectDependencyExplodedAars =
                unitTestImpl.variantDependencies.getArtifactCollectionForToolingModel(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    AndroidArtifacts.ArtifactType.LOCAL_EXPLODED_AAR_FOR_LINT
                )
        }
        artifactCollectionsInputs.setDisallowChanges(ArtifactCollectionsInputs(
            variantDependencies = unitTestImpl.variantDependencies,
            projectPath = unitTestImpl.globalScope.project.path,
            variantName = unitTestImpl.name,
            runtimeType = ArtifactCollectionsInputs.RuntimeType.FULL,
            buildMapping = unitTestImpl.globalScope.project.gradle.computeBuildMapping(),
            mavenCoordinatesCache = getBuildService(unitTestImpl.services.buildServiceRegistry)
        ))
        return this
    }

    fun initializeForStandalone(project: Project, projectOptions: ProjectOptions, sourceSet: SourceSet): JavaArtifactInput {
        classesOutputDirectories.fromDisallowChanges(sourceSet.output.classesDirs)
        val variantDependencies = VariantDependencies(
            variantName = sourceSet.name,
            variantType = VariantTypeImpl.JAVA_LIBRARY,
            compileClasspath = project.configurations.getByName(sourceSet.compileClasspathConfigurationName),
            runtimeClasspath = project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName),
            sourceSetRuntimeConfigurations = listOf(),
            sourceSetImplementationConfigurations = listOf(),
            elements = mapOf(),
            providedClasspath = project.configurations.getByName(sourceSet.compileOnlyConfigurationName),
            annotationProcessorConfiguration = project.configurations.getByName(sourceSet.annotationProcessorConfigurationName),
            reverseMetadataValuesConfiguration = null,
            wearAppConfiguration = null,
            testedVariant = null,
            project = project,
            projectOptions = projectOptions
        )
        artifactCollectionsInputs.setDisallowChanges(
            ArtifactCollectionsInputs(
                variantDependencies = variantDependencies,
                projectPath = project.path,
                variantName = sourceSet.name,
                runtimeType = ArtifactCollectionsInputs.RuntimeType.FULL,
                buildMapping = project.gradle.computeBuildMapping(),
                mavenCoordinatesCache = getBuildService(project.gradle.sharedServices)
            )
        )
        return this
    }


    internal fun toLintModel(dependencyCaches: DependencyCaches, checkDependencies: Boolean): LintModelJavaArtifact {
        return DefaultLintModelJavaArtifact(
            classesOutputDirectories.files.toList(),
            computeDependencies(dependencyCaches, checkDependencies)
        )
    }
}

/**
 * Base Inputs for Android/Java artifacts
 */
abstract class ArtifactInput {

    @get:Classpath
    abstract val classesOutputDirectories: ConfigurableFileCollection

    @get:Nested
    abstract val artifactCollectionsInputs: Property<ArtifactCollectionsInputs>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    @get:Optional
    val projectExplodedAarsFileCollection: FileCollection?
        get() = projectDependencyExplodedAars?.artifactFiles

    @get:Internal
    var projectDependencyExplodedAars: ArtifactCollection? = null

    internal fun computeDependencies(dependencyCaches: DependencyCaches, checkDependencies: Boolean): LintModelDependencies {

        val artifactCollectionsInputs = artifactCollectionsInputs.get()

        val artifactHandler: ArtifactHandler<LintModelLibrary> =
            if (checkDependencies) {
                // TODO(b/160392650): Maybe add the java library project dependency models as an
                //   input here, and then treat the library as external if there is no corresponding
                //   lint model (as the build author now needs to apply the standalone lint plugin)
                //   This could help to ease the transition to this new approach.
                LintModelArtifactHandler(dependencyCaches)
            } else {
                // When not checking dependencies, treat all dependencies as external.
                ExternalLintModelArtifactHandler.create(
                    dependencyCaches,
                    projectDependencyExplodedAars,
                    null,
                    artifactCollectionsInputs.compileClasspath.projectJars,
                    artifactCollectionsInputs.runtimeClasspath!!.projectJars,
                    buildMapping = artifactCollectionsInputs.buildMapping
                )
            }
        val modelBuilder = LintDependencyModelBuilder(
            artifactHandler = artifactHandler, libraryMap = dependencyCaches.libraryMap
        )

        val graph = getDependencyGraphBuilder()
        val issueReporter = object : IssueReporter() {
            override fun reportIssue(type: Type, severity: Severity, exception: EvalIssueException) {
                if (severity == Severity.ERROR) {
                    throw exception
                }
            }

            override fun hasIssue(type: Type) = false
        }

        graph.createDependencies(
            modelBuilder = modelBuilder,
            artifactCollectionsProvider = artifactCollectionsInputs,
            withFullDependency = true,
            issueReporter = issueReporter
        )

        return modelBuilder.createModel()
    }
}
