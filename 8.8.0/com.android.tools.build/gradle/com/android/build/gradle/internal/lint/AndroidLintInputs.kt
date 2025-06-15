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
@file:JvmName("AndroidLintInputs")

package com.android.build.gradle.internal.lint

import com.android.SdkConstants
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.Lint
import com.android.build.api.variant.InternalSources
import com.android.build.api.variant.ResValue
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.api.variant.impl.LayeredSourceDirectoriesImpl
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.DeviceTestCreationConfig
import com.android.build.gradle.internal.component.HostTestCreationConfig
import com.android.build.gradle.internal.component.KmpComponentCreationConfig
import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.component.TestFixturesCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.LintImpl
import com.android.build.gradle.internal.ide.Utils.getGeneratedSourceFoldersFileCollection
import com.android.build.gradle.internal.ide.dependencies.ArtifactCollectionsInputs
import com.android.build.gradle.internal.ide.dependencies.ArtifactCollectionsInputsImpl
import com.android.build.gradle.internal.ide.dependencies.ArtifactHandler
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService
import com.android.build.gradle.internal.ide.dependencies.UsesLibraryDependencyCacheBuildService
import com.android.build.gradle.internal.ide.dependencies.getDependencyGraphBuilder
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.GENERATED_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.RENDERSCRIPT_GENERATED_RES
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.services.LintClassLoaderBuildService
import com.android.build.gradle.internal.services.LintParallelBuildService
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.utils.createTargetSdkVersion
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.getDesugaredMethods
import com.android.build.gradle.internal.utils.isKotlinPluginAppliedInTheSameClassloader
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.OptionalBooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.android.builder.core.ComponentType
import com.android.builder.core.ComponentTypeImpl
import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.IssueReporter
import com.android.ide.common.gradle.Version
import com.android.ide.common.repository.AgpVersion
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
import com.android.tools.lint.model.LintModelArtifactType
import com.android.tools.lint.model.LintModelArtifactType.MAIN
import com.android.tools.lint.model.LintModelArtifactType.UNIT_TEST
import com.android.tools.lint.model.LintModelBuildFeatures
import com.android.tools.lint.model.LintModelDependencies
import com.android.tools.lint.model.LintModelJavaArtifact
import com.android.tools.lint.model.LintModelLibrary
import com.android.tools.lint.model.LintModelLintOptions
import com.android.tools.lint.model.LintModelModule
import com.android.tools.lint.model.LintModelModuleType
import com.android.tools.lint.model.LintModelNamespacingMode
import com.android.tools.lint.model.LintModelSeverity
import com.android.tools.lint.model.LintModelSourceProvider
import com.android.tools.lint.model.LintModelVariant
import com.android.utils.FileUtils
import com.android.utils.PathUtils
import com.android.utils.appendCapitalized
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.SetProperty
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.workers.WorkerExecutor
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.Companion.DEFAULT
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Callable

abstract class LintTool {

    /** Lint itself */
    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    /**
     * The identity of lint used as keys for caches
     *
     * Used both for the [lintCacheDirectory] and for the classloader cache in [AndroidLintWorkAction]
     *
     * For published versions it will include the version of lint from maven e.g. `30.2.0-alpha05`
     * and for -dev versions, also a hash of the jars: `30.2.0-dev_920ff9cabfbb40d0318735f9fe403b9/`
     */
    @get:Input
    abstract val versionKey: Property<String>

    @get:Input
    abstract val runInProcess: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val workerHeapSize: Property<String>

    /**
     * The lint cache parent dir for artifacts recomputable by lint that save analysis time
     */
    @get:Internal
    abstract val lintCacheDirectory: DirectoryProperty

    /**
     * Computes the lint cache dir, cleaning up if lint version has changed
     *
     * This is passed to lint invocations using --cache-dir
     *
     * The lint cache is neither an input nor an output to the lint tasks, so it needs some manual
     * handling to avoid lint trying to load cache items written by a different version of lint.
     *
     * A marker file of lint-cache-version is used, for published versions it will include the
     * version of lint, e.g. `30.2.0-alpha05`
     *
     * And for -dev versions, also a hash of the jars, the same as the classloader hash
     * 30.2.0-dev_920ff9cabfbb40d0318735f9fe403b9
     *
     * Returns the arguments to add to the lint invocation.
     */
    fun initializeLintCacheDir(): List<String> {
        val directory = lintCacheDirectory.get().asFile.toPath()
        val lintVersionMarkerFile = directory.resolve("lint-cache-version.txt")
        val currentVersion = "Cache for Android Lint" + versionKey.get()
        val previousVersion = lintVersionMarkerFile.takeIf { Files.exists(it) }?.let { Files.readAllLines(it).singleOrNull() }
        if (previousVersion != currentVersion) {
            PathUtils.deleteRecursivelyIfExists(directory)
            Files.createDirectories(directory)
            Files.write(lintVersionMarkerFile, listOf(currentVersion))
        }
        return listOf("--cache-dir", directory.toString())
    }

    @get:Internal
    abstract val lintClassLoaderBuildService: Property<LintClassLoaderBuildService>

    fun initialize(taskCreationServices: TaskCreationServices, task: Task) {
        classpath.fromDisallowChanges(taskCreationServices.lintFromMaven.files)
        getBuildService<LintClassLoaderBuildService, BuildServiceParameters.None>(taskCreationServices.buildServiceRegistry).let {
            lintClassLoaderBuildService.setDisallowChanges(it)
            task.usesService(it)
        }
        versionKey.setDisallowChanges(deriveVersionKey(taskCreationServices, lintClassLoaderBuildService))
        val projectOptions = taskCreationServices.projectOptions
        runInProcess.setDisallowChanges(projectOptions.getProvider(BooleanOption.RUN_LINT_IN_PROCESS))
        workerHeapSize.setDisallowChanges(projectOptions.getProvider(StringOption.LINT_HEAP_SIZE))
        lintCacheDirectory.setDisallowChanges(
            taskCreationServices.projectInfo
                .buildDirectory
                .dir("${SdkConstants.FD_INTERMEDIATES}/lint-cache/${task.name}")
        )
    }

    private fun deriveVersionKey(
        taskCreationServices: TaskCreationServices,
        lintClassLoaderBuildService: Provider<LintClassLoaderBuildService>
    ): Provider<String> {
        val lintVersion =
            getLintMavenArtifactVersion(
                taskCreationServices.projectOptions[StringOption.LINT_VERSION_OVERRIDE]?.trim(),
                null
            )
        val versionProvider = taskCreationServices.provider { lintVersion }
        // When using development versions also hash the jar contents to avoid reusing
        // the classloader when the jars might change
        return when {
            lintVersion.endsWith("-dev") || lintVersion.endsWith("SNAPSHOT") -> {
                val jarsHash = lintClassLoaderBuildService.zip(classpath.elements, LintClassLoaderBuildService::hashJars)
                versionProvider.zip(jarsHash) { version, hash -> "${version}_$hash" }
            }
            else -> versionProvider
        }
    }

    fun submit(
        workerExecutor: WorkerExecutor,
        mainClass: String,
        arguments: List<String>,
        lintMode: LintMode,
        useK2Uast: Boolean,
    ) {
        submit(
            workerExecutor,
            mainClass,
            arguments,
            android = true,
            fatalOnly = false,
            await = false,
            lintMode = lintMode,
            hasBaseline = false,
            useK2Uast = useK2Uast,
        )
    }

    fun submit(
        workerExecutor: WorkerExecutor,
        mainClass: String,
        arguments: List<String>,
        android: Boolean,
        fatalOnly: Boolean,
        await: Boolean,
        lintMode: LintMode,
        hasBaseline: Boolean,
        useK2Uast: Boolean,
        returnValueOutputFile: File? = null
    ) {
        val workQueue = if (runInProcess.get()) {
            workerExecutor.noIsolation()
        } else {
            workerExecutor.processIsolation {
                it.classpath.from(classpath)
                // Default to using the main Gradle daemon heap size to smooth the transition
                // for build authors.
                it.forkOptions.maxHeapSize =
                    LintParallelBuildService.calculateLintHeapSize(
                        workerHeapSize.orNull,
                        Runtime.getRuntime().maxMemory()
                    )
            }
        }
        workQueue.submit(AndroidLintWorkAction::class.java) { parameters ->
            parameters.mainClass.set(mainClass)
            parameters.arguments.set(arguments)
            parameters.classpath.from(classpath)
            parameters.versionKey.set(versionKey)
            parameters.android.set(android)
            parameters.fatalOnly.set(fatalOnly)
            parameters.runInProcess.set(runInProcess.get())
            parameters.returnValueOutputFile.set(returnValueOutputFile)
            parameters.lintMode.set(lintMode)
            parameters.hasBaseline.set(hasBaseline)
            parameters.useK2Uast.set(useK2Uast)
        }
        if (await) {
            workQueue.await()
        }
    }

}

abstract class ProjectInputs {

    @get:Internal
    abstract val projectDirectoryPath: Property<String>

    // projectDirectoryPathInput should either be (1) unset if the project directory is not an input
    // to the associated task, or (2) set to the same value as projectDirectoryPath otherwise.
    @get:Input
    @get:Optional
    abstract val projectDirectoryPathInput: Property<String>

    @get:Input
    abstract val projectGradlePath: Property<String>

    @get:Input
    abstract val projectType: Property<LintModelModuleType>

    @get:Input
    abstract val mavenGroupId: Property<String>

    @get:Input
    abstract val mavenArtifactId: Property<String>

    @get:Input
    abstract val mavenVersion: Property<String>

    @get:Internal
    abstract val buildDirectoryPath: Property<String>

    // buildDirectoryPathInput should either be (1) unset if the build directory is not an input to
    // the associated task, or (2) set to the same value as buildDirectoryPath otherwise.
    @get:Input
    @get:Optional
    abstract val buildDirectoryPathInput: Property<String>

    @get:Nested
    abstract val lintOptions: LintOptionsInput

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

    /**
     * [lintConfigFiles] contains all possible lint.xml files in the module's directory or any
     * parent directories up to the root project directory.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val lintConfigFiles: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val buildFile: RegularFileProperty

    internal fun initialize(variant: VariantWithTests, lintMode: LintMode) {
        initialize(variant.main, lintMode)
    }

    internal fun initialize(creationConfig: ComponentCreationConfig, lintMode: LintMode) {
        val globalConfig = creationConfig.global

        initializeFromProject(creationConfig.services.projectInfo, lintMode)
        projectType.setDisallowChanges(creationConfig.componentType.toLintModelModuleType())

        lintOptions.initialize(globalConfig.lintOptions, lintMode)
        resourcePrefix.setDisallowChanges(globalConfig.resourcePrefix)

        dynamicFeatures.setDisallowChanges(globalConfig.dynamicFeatures)

        bootClasspath.fromDisallowChanges(globalConfig.bootClasspath)
        javaSourceLevel.setDisallowChanges(globalConfig.compileOptions.sourceCompatibility)
        compileTarget.setDisallowChanges(globalConfig.compileSdkHashString)
        // `neverShrinking` is about all variants, so look back to the DSL
        neverShrinking.setDisallowChanges(globalConfig.hasNoBuildTypeMinified)
    }

    internal fun initialize(variantScope: PrivacySandboxSdkVariantScope, lintMode: LintMode) {

        initializeFromProject(variantScope.services.projectInfo, lintMode)
        projectType.setDisallowChanges(LintModelModuleType.PRIVACY_SANDBOX_SDK)

        // This is always true for PrivacySandboxSdk module because it does not have any source and
        // we should report lint issues from dependencies.
        variantScope.lintOptions.checkDependencies = true

        lintOptions.initialize(variantScope.lintOptions, lintMode)
        resourcePrefix.setDisallowChanges("")

        dynamicFeatures.setDisallowChanges(setOf())

        bootClasspath.fromDisallowChanges(variantScope.bootClasspath)
        // TODO: Change java version to something reasonable
        javaSourceLevel.setDisallowChanges(JavaVersion.VERSION_HIGHER)

        compileTarget.setDisallowChanges(variantScope.compileSdkVersion)

        neverShrinking.setDisallowChanges(true)
    }

    internal fun initializeForStandalone(
        project: Project,
        javaExtension: JavaPluginExtension,
        dslLintOptions: Lint,
        lintMode: LintMode
    ) {
        initializeFromProject(ProjectInfo(project), lintMode)
        projectType.setDisallowChanges(LintModelModuleType.JAVA_LIBRARY)
        lintOptions.initialize(dslLintOptions, lintMode)
        resourcePrefix.setDisallowChanges("")
        dynamicFeatures.setDisallowChanges(setOf())
        val javaCompileTask =
            javaExtension.sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME)?.let {
                project.tasks.named(it.compileJavaTaskName, JavaCompile::class.java)
            }
        if (javaCompileTask != null) {
            bootClasspath.from(
                javaCompileTask.map { it.options.bootstrapClasspath ?: project.files() }
            )
        }
        bootClasspath.disallowChanges()
        javaSourceLevel.setDisallowChanges(javaExtension.sourceCompatibility)
        compileTarget.setDisallowChanges("")
        neverShrinking.setDisallowChanges(true)
    }

    private fun initializeFromProject(projectInfo: ProjectInfo, lintMode: LintMode) {
        projectDirectoryPath.setDisallowChanges(projectInfo.projectDirectory.toString())
        projectGradlePath.setDisallowChanges(projectInfo.path)
        mavenGroupId.setDisallowChanges(projectInfo.group)
        mavenArtifactId.setDisallowChanges(projectInfo.name)
        mavenVersion.setDisallowChanges(projectInfo.version)
        buildDirectoryPath.setDisallowChanges(projectInfo.buildDirectory.map { it.asFile.absolutePath })
        if (lintMode != LintMode.ANALYSIS) {
            projectDirectoryPathInput.set(projectDirectoryPath)
            buildDirectoryPathInput.set(buildDirectoryPath)
        }
        projectDirectoryPathInput.disallowChanges()
        buildDirectoryPathInput.disallowChanges()
        initializeLintConfigFiles(projectInfo)
        buildFile.set(projectInfo.buildFile)
        buildFile.disallowChanges()
    }

    internal fun convertToLintModelModule(): LintModelModule {
        return DefaultLintModelModule(
            loader = null,
            dir = File(projectDirectoryPath.get()),
            modulePath = projectGradlePath.get(),
            type = projectType.get(),
            mavenName = DefaultLintModelMavenName(
                mavenGroupId.get(),
                mavenArtifactId.get(),
                mavenVersion.get()
            ),
            agpVersion = AgpVersion.tryParse(com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION),
            buildFolder = File(buildDirectoryPath.get()),
            lintOptions = lintOptions.toLintModel(),
            lintRuleJars = listOf(),
            resourcePrefix = resourcePrefix.orNull,
            dynamicFeatures = dynamicFeatures.get(),
            bootClassPath = bootClasspath.files.toList(),
            javaSourceLevel = javaSourceLevel.get().toString(),
            compileTarget = compileTarget.get(),
            variants = listOf(),
            neverShrinking = neverShrinking.get()
        )
    }

    /**
     * Initialize [lintConfigFiles] with all possible lint.xml files in the module's directory or
     * any parent directories up to the root project directory.
     */
    private fun initializeLintConfigFiles(projectInfo: ProjectInfo) {
        var currentDir = projectInfo.projectDirectory.asFile
        var currentLintXml = File(currentDir, LINT_XML_CONFIG_FILE_NAME)
        while (FileUtils.isFileInDirectory(currentLintXml, projectInfo.rootDir)) {
            lintConfigFiles.from(currentLintXml)
            currentDir = currentDir.parentFile ?: break
            currentLintXml = File(currentDir, LINT_XML_CONFIG_FILE_NAME)
        }
        lintConfigFiles.disallowChanges()
    }
}

internal fun ComponentType.toLintModelModuleType(): LintModelModuleType {
    return when (this) {
        // FIXME add other types
        ComponentTypeImpl.BASE_APK -> LintModelModuleType.APP
        ComponentTypeImpl.LIBRARY -> LintModelModuleType.LIBRARY
        ComponentTypeImpl.OPTIONAL_APK -> LintModelModuleType.DYNAMIC_FEATURE
        ComponentTypeImpl.TEST_APK -> LintModelModuleType.TEST
        ComponentTypeImpl.KMP_ANDROID -> LintModelModuleType.LIBRARY
        else -> throw RuntimeException("Unsupported ComponentTypeImpl value")
    }
}

abstract class LintOptionsInput {
    @get:Input
    abstract val disable: SetProperty<String>
    @get:Input
    abstract val enable: SetProperty<String>
    @get:Input
    abstract val checkOnly: SetProperty<String>
    @get:Input
    abstract val abortOnError: Property<Boolean>
    @get:Input
    abstract val absolutePaths: Property<Boolean>
    @get:Input
    abstract val noLines: Property<Boolean>
    @get:Input
    abstract val quiet: Property<Boolean>
    @get:Input
    abstract val checkAllWarnings: Property<Boolean>
    @get:Input
    abstract val ignoreWarnings: Property<Boolean>
    @get:Input
    abstract val warningsAsErrors: Property<Boolean>
    @get:Input
    abstract val checkTestSources: Property<Boolean>
    @get:Input
    abstract val checkGeneratedSources: Property<Boolean>
    @get:Input
    abstract val explainIssues: Property<Boolean>
    @get:Input
    abstract val showAll: Property<Boolean>
    @get:Input
    abstract val checkDependencies: Property<Boolean>
    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val lintConfig: RegularFileProperty
    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val baseline: RegularFileProperty
    @get:Input
    abstract val severityOverrides: MapProperty<String, LintModelSeverity>
    @get:Input
    abstract val ignoreTestSources: Property<Boolean>
    @get:Input
    abstract val ignoreTestFixturesSources: Property<Boolean>

    fun initialize(lintOptions: Lint, lintMode: LintMode) {
        disable.setDisallowChanges(lintOptions.disable)
        enable.setDisallowChanges(lintOptions.enable)
        checkOnly.setDisallowChanges(lintOptions.checkOnly)
        abortOnError.setDisallowChanges(lintOptions.abortOnError)
        absolutePaths.setDisallowChanges(lintOptions.absolutePaths)
        noLines.setDisallowChanges(lintOptions.noLines)
        quiet.setDisallowChanges(lintOptions.quiet)
        checkAllWarnings.setDisallowChanges(lintOptions.checkAllWarnings)
        ignoreWarnings.setDisallowChanges(lintOptions.ignoreWarnings)
        warningsAsErrors.setDisallowChanges(lintOptions.warningsAsErrors)
        checkTestSources.setDisallowChanges(lintOptions.checkTestSources)
        checkGeneratedSources.setDisallowChanges(lintOptions.checkGeneratedSources)
        explainIssues.setDisallowChanges(lintOptions.explainIssues)
        showAll.setDisallowChanges(lintOptions.showAll)
        checkDependencies.setDisallowChanges(lintOptions.checkDependencies)
        lintOptions.lintConfig?.let { lintConfig.set(it) }
        lintConfig.disallowChanges()
        // The baseline file does not affect analysis, but otherwise it is an input.
        if (lintMode != LintMode.ANALYSIS) {
            lintOptions.baseline?.let { baseline.set(it) }
        }
        baseline.disallowChanges()
        severityOverrides.setDisallowChanges((lintOptions as LintImpl).severityOverridesMap)
        ignoreTestSources.setDisallowChanges(lintOptions.ignoreTestSources)
        ignoreTestFixturesSources.setDisallowChanges(lintOptions.ignoreTestFixturesSources)
    }

    fun toLintModel(): LintModelLintOptions {
        return DefaultLintModelLintOptions(
            disable=disable.get(),
            enable=enable.get(),
            check=checkOnly.get(),
            abortOnError=abortOnError.get(),
            absolutePaths=absolutePaths.get(),
            noLines=noLines.get(),
            quiet=quiet.get(),
            checkAllWarnings=checkAllWarnings.get(),
            ignoreWarnings=ignoreWarnings.get(),
            warningsAsErrors=warningsAsErrors.get(),
            checkTestSources=checkTestSources.get(),
            ignoreTestSources=ignoreTestSources.get(),
            ignoreTestFixturesSources=ignoreTestFixturesSources.get(),
            checkGeneratedSources=checkGeneratedSources.get(),
            explainIssues=explainIssues.get(),
            showAll=showAll.get(),
            lintConfig=lintConfig.orNull?.asFile,
            // Report setup is handled in the invocation
            textReport=false,
            textOutput=null,
            htmlReport=false,
            htmlOutput=null,
            xmlReport=false,
            xmlOutput=null,
            sarifReport=false,
            sarifOutput=null,
            checkReleaseBuilds=true, // Handled in LintTaskManager & LintPlugin
            checkDependencies=checkDependencies.get(),
            baselineFile = baseline.orNull?.asFile,
            severityOverrides=severityOverrides.get(),
        )
    }
}

/**
 * System properties which can affect lint's behavior.
 */
abstract class SystemPropertyInputs {

    @get:Input
    @get:Optional
    abstract val androidLintLogJarProblems: Property<String>

    // Use @get:Internal because javaVersion acts as proxy input for javaHome
    @get:Internal
    abstract val javaHome: Property<String>

    @get:Input
    @get:Optional
    abstract val javaVersion: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val lintApiDatabase: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val lintAutofix: Property<String>

    @get:Input
    @get:Optional
    abstract val lintBaselinesContinue: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val lintConfigurationOverride: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val lintHtmlPrefs: Property<String>

    @get:Input
    @get:Optional
    abstract val lintNullnessIgnoreDeprecated: Property<String>

    @get:Input
    @get:Optional
    abstract val lintUnusedResourcesExcludeTests: Property<String>

    @get:Input
    @get:Optional
    abstract val lintUnusedResourcesIncludeTests: Property<String>

    @get:Input
    @get:Optional
    abstract val userHome: Property<String>

    fun initialize(providerFactory: ProviderFactory, lintMode: LintMode) {
        if (lintMode == LintMode.ANALYSIS) {
            lintAutofix.disallowChanges()
            lintBaselinesContinue.disallowChanges()
            lintHtmlPrefs.disallowChanges()
            userHome.disallowChanges()
        } else {
            lintAutofix.setDisallowChanges(providerFactory.systemProperty("lint.autofix"))
            lintBaselinesContinue.setDisallowChanges(
                providerFactory.systemProperty("lint.baselines.continue")
            )
            lintHtmlPrefs.setDisallowChanges(providerFactory.systemProperty("lint.html.prefs"))
            userHome.setDisallowChanges(providerFactory.systemProperty("user.home"))
        }
        androidLintLogJarProblems.setDisallowChanges(
            providerFactory.systemProperty("android.lint.log-jar-problems")
        )
        javaHome.setDisallowChanges(providerFactory.systemProperty("java.home"))
        javaVersion.setDisallowChanges(providerFactory.systemProperty("java.version"))
        lintApiDatabase.fileProvider(
            providerFactory.systemProperty("LINT_API_DATABASE").map {
                File(it)
            }.filter { it.isFile }
        )
        lintApiDatabase.disallowChanges()
        lintConfigurationOverride.fileProvider(
            providerFactory.systemProperty("lint.configuration.override").map {
                File(it)
            }.filter { it.isFile }
        )
        lintConfigurationOverride.disallowChanges()
        lintNullnessIgnoreDeprecated.setDisallowChanges(
            providerFactory.systemProperty("lint.nullness.ignore-deprecated")
        )
        lintUnusedResourcesExcludeTests.setDisallowChanges(
            providerFactory.systemProperty("lint.unused-resources.exclude-tests")
        )
        lintUnusedResourcesIncludeTests.setDisallowChanges(
            providerFactory.systemProperty("lint.unused-resources.include-tests")
        )
    }
}

/**
 * Environment variables which can affect lint's behavior.
 */
abstract class EnvironmentVariableInputs {

    @get:Input
    @get:Optional
    abstract val androidLintIncludeLdpi: Property<String>

    @get:Input
    @get:Optional
    abstract val androidLintMaxDepth: Property<String>

    @get:Input
    @get:Optional
    abstract val androidLintMaxViewCount: Property<String>

    @get:Input
    @get:Optional
    abstract val androidLintNullnessIgnoreDeprecated: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val lintApiDatabase: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val lintHtmlPrefs: Property<String>

    @get:Input
    @get:Optional
    abstract val lintXmlRoot: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val lintOverrideConfiguration: RegularFileProperty

    fun initialize(providerFactory: ProviderFactory, lintMode: LintMode) {
        if (lintMode == LintMode.ANALYSIS) {
            lintHtmlPrefs.disallowChanges()
            lintXmlRoot.disallowChanges()
        } else {
            lintHtmlPrefs.setDisallowChanges(providerFactory.environmentVariable("LINT_HTML_PREFS"))
            lintXmlRoot.setDisallowChanges(providerFactory.environmentVariable("LINT_XML_ROOT"))
        }
        androidLintIncludeLdpi.setDisallowChanges(
            providerFactory.environmentVariable("ANDROID_LINT_INCLUDE_LDPI")
        )
        androidLintMaxDepth.setDisallowChanges(
            providerFactory.environmentVariable("ANDROID_LINT_MAX_DEPTH")
        )
        androidLintMaxViewCount.setDisallowChanges(
            providerFactory.environmentVariable("ANDROID_LINT_MAX_VIEW_COUNT")
        )
        androidLintNullnessIgnoreDeprecated.setDisallowChanges(
            providerFactory.environmentVariable("ANDROID_LINT_NULLNESS_IGNORE_DEPRECATED")
        )
        lintApiDatabase.fileProvider(
            providerFactory.environmentVariable("LINT_API_DATABASE").map {
                File(it)
            }.filter { it.isFile }
        )
        lintApiDatabase.disallowChanges()
        lintOverrideConfiguration.fileProvider(
            providerFactory.environmentVariable("LINT_OVERRIDE_CONFIGURATION").map {
                File(it)
            }.filter { it.isFile }
        )
        lintOverrideConfiguration.disallowChanges()
    }
}

/**
 * Inputs for the variant.
 */
abstract class VariantInputs : UsesLibraryDependencyCacheBuildService {

    @get:Input
    abstract val name: Property<String>

    /**
     * Whether the module dependencies should be modeled as module dependencies (instead of modeled
     * as external libraries).
     */
    @get:Input
    abstract val useModuleDependencyLintModels: Property<Boolean>

    @get:Nested
    @get:Optional
    abstract val mainArtifact: Property<AndroidArtifactInput>

    @get:Nested
    @get:Optional
    abstract val testArtifact: Property<JavaArtifactInput>

    @get:Nested
    @get:Optional
    abstract val androidTestArtifact: Property<AndroidArtifactInput>

    @get:Nested
    @get:Optional
    abstract val testFixturesArtifact: Property<AndroidArtifactInput>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val mergedManifest: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val manifestMergeReport: RegularFileProperty

    @get:Input
    abstract val namespace: Property<String>

    @get:Nested
    @get:Optional
    abstract val minSdkVersion: SdkVersionInput

    @get:Nested
    @get:Optional
    abstract val targetSdkVersion: SdkVersionInput

    @get:Input
    abstract val resValues: MapProperty<ResValue.Key, ResValue>

    @get:Input
    abstract val manifestPlaceholders: MapProperty<String, String>

    @get:Input
    abstract val resourceConfigurations: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val proguardFiles: ListProperty<RegularFile>

    // the extracted proguard files are probably also part of the proguardFiles but we need to set
    // the dependency explicitly so Gradle can track it properly.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val extractedProguardFiles: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val consumerProguardFiles: ListProperty<RegularFile>

    @get:Nested
    @get:Optional
    abstract val mainSourceProvider: Property<SourceProviderInput>

    /**
     * Androidx compose plugin uses this field to add kotlin multiplatform sources sets as there is
     * no public API to customize lint sources.
     */
    @get:Internal
    abstract val sourceProviders: ListProperty<SourceProviderInput>

    @get:Nested
    @get:Optional
    abstract val hostTestSourceProvider: Property<SourceProviderInput>

    @get:Nested
    @get:Optional
    abstract val androidTestSourceProvider: Property<SourceProviderInput>

    @get:Nested
    @get:Optional
    abstract val testFixturesSourceProvider: Property<SourceProviderInput>

    @get:Input
    abstract val debuggable: Property<Boolean>

    @get:Input
    abstract val shrinkable: Property<Boolean>

    @get:Input
    abstract val useSupportLibraryVectorDrawables: Property<Boolean>

    @get:Nested
    abstract val buildFeatures: BuildFeaturesInput

    @get:Internal
    abstract val mavenCoordinatesCache: Property<MavenCoordinatesCacheBuildService>

    /**
     * Initializes the variant inputs
     *
     * @param variantWithTests the [VariantWithTests].
     * @param useModuleDependencyLintModels whether the module dependencies should be modeled as
     *     module dependencies (instead of modeled as external libraries).
     * @param warnIfProjectTreatedAsExternalDependency whether to warn the user if the standalone
     *     plugin is not applied to a java module dependency when useModuleDependencyLintModels is
     *     true.
     * @param lintMode the [LintMode] for which this [VariantInputs] is being used.
     * @param addBaseModuleLintModel whether the base app module should be modeled as a module
     *     dependency if useModuleDependencyLintModels is false. This Boolean only affects dynamic
     *     feature modules, and it has no effect if useModuleDependencyLintModels is true.
     * @param fatalOnly whether lint is being invoked with --fatal-only
     */
    fun initialize(
        task: Task,
        variantWithTests: VariantWithTests,
        useModuleDependencyLintModels: Boolean,
        warnIfProjectTreatedAsExternalDependency: Boolean,
        lintMode: LintMode,
        addBaseModuleLintModel: Boolean = false,
        fatalOnly: Boolean
    ) {
        initialize(
            task,
            variantWithTests.main,
            variantWithTests.unitTest,
            variantWithTests.androidTest,
            variantWithTests.testFixtures,
            variantWithTests.main.services,
            variantWithTests.main.name,
            useModuleDependencyLintModels,
            warnIfProjectTreatedAsExternalDependency,
            lintMode,
            addBaseModuleLintModel,
            fatalOnly,
            includeMainArtifact = true,
            isPerComponentLintAnalysis = false
        )
    }

    fun initialize(
        task: Task,
        variantScope: PrivacySandboxSdkVariantScope,
        projectOptions: ProjectOptions,
        useModuleDependencyLintModels: Boolean,
        lintMode: LintMode,
        fatalOnly: Boolean,
    ) {
        name.setDisallowChanges(variantScope.name)
        this.useModuleDependencyLintModels.setDisallowChanges(useModuleDependencyLintModels)
        mainArtifact.setDisallowChanges(
            variantScope.services.newInstance(AndroidArtifactInput::class.java)
                .initializeForPrivacySandboxSdk(
                    task.project,
                    variantScope,
                    projectOptions,
                    lintMode,
                    useModuleDependencyLintModels,
                    fatalOnly
                ))
        mainSourceProvider.set(
            task.project.objects
                .newInstance(SourceProviderInput::class.java)
                .initializeForPrivacySandboxSdk()
        )
        testArtifact.disallowChanges()
        hostTestSourceProvider.disallowChanges()
        androidTestArtifact.disallowChanges()
        testFixturesArtifact.disallowChanges()
        namespace.setDisallowChanges("")
        minSdkVersion.initialize(variantScope.minSdkVersion.apiLevel, variantScope.minSdkVersion.codename)
        targetSdkVersion.initialize(variantScope.targetSdkVersion.apiLevel, variantScope.targetSdkVersion.codename)
        manifestPlaceholders.disallowChanges()
        resourceConfigurations.disallowChanges()
        debuggable.setDisallowChanges(true)
        shrinkable.setDisallowChanges(false)
        useSupportLibraryVectorDrawables.setDisallowChanges(false)
        mergedManifest.setDisallowChanges(variantScope.artifacts.get(
            PrivacySandboxSdkInternalArtifactType.SANDBOX_MANIFEST
        ))
        manifestMergeReport.setDisallowChanges(null)
        sourceProviders.add(mainSourceProvider)
        sourceProviders.disallowChanges()
        androidTestSourceProvider.disallowChanges()
        testFixturesSourceProvider.disallowChanges()
        buildFeatures.initializeForStandalone()
        initializeLibraryDependencyCacheBuildService(task)
        mavenCoordinatesCache.setDisallowChanges(getBuildService(task.project.gradle.sharedServices))
        proguardFiles.add(variantScope.artifacts.get(PrivacySandboxSdkInternalArtifactType.GENERATED_PROGUARD_FILE))
        proguardFiles.disallowChanges()
        extractedProguardFiles.setDisallowChanges(null)
        consumerProguardFiles.setDisallowChanges(null)
        resValues.disallowChanges()
    }

    fun initialize(
        task: Task,
        variantCreationConfig: VariantCreationConfig,
        hostTestCreationConfig: HostTestCreationConfig?,
        deviceTestCreationConfig: DeviceTestCreationConfig?,
        testFixturesCreationConfig: TestFixturesCreationConfig?,
        services: TaskCreationServices,
        variantName: String,
        useModuleDependencyLintModels: Boolean,
        warnIfProjectTreatedAsExternalDependency: Boolean,
        lintMode: LintMode,
        addBaseModuleLintModel: Boolean = false,
        fatalOnly: Boolean,
        includeMainArtifact: Boolean,
        isPerComponentLintAnalysis: Boolean
    ) {
        name.setDisallowChanges(variantName)
        this.useModuleDependencyLintModels.setDisallowChanges(useModuleDependencyLintModels)
        if (includeMainArtifact) {
            mainArtifact.set(
                services.newInstance(AndroidArtifactInput::class.java)
                    .initialize(
                        variantCreationConfig,
                        lintMode,
                        useModuleDependencyLintModels,
                        addBaseModuleLintModel,
                        warnIfProjectTreatedAsExternalDependency,
                        fatalOnly,
                        isPerComponentLintAnalysis
                    )
            )
        }
        mainArtifact.disallowChanges()

        testArtifact.setDisallowChanges(
            hostTestCreationConfig?.let { hostTest ->
                services.newInstance(JavaArtifactInput::class.java)
                    .initialize(
                        hostTest,
                        lintMode,
                        useModuleDependencyLintModels = false,
                        addBaseModuleLintModel,
                        warnIfProjectTreatedAsExternalDependency,
                        // analyzing test bytecode is expensive, without much benefit
                        includeClassesOutputDirectories = false,
                        fatalOnly,
                        isPerComponentLintAnalysis
                    )
            }
        )

        androidTestArtifact.setDisallowChanges(
            deviceTestCreationConfig?.let { androidTest ->
                services.newInstance(AndroidArtifactInput::class.java)
                    .initialize(
                        androidTest,
                        lintMode,
                        useModuleDependencyLintModels = false,
                        addBaseModuleLintModel,
                        warnIfProjectTreatedAsExternalDependency,
                        fatalOnly,
                        isPerComponentLintAnalysis,
                        // analyzing test bytecode is expensive, without much benefit
                        includeClassesOutputDirectories = false,
                        // analyzing test generated sources is expensive, without much benefit
                        includeGeneratedSourceFolders = false
                    )
            }
        )

        testFixturesArtifact.setDisallowChanges(
            testFixturesCreationConfig?.let { testFixtures ->
                services.newInstance(AndroidArtifactInput::class.java)
                    .initialize(
                        testFixtures,
                        lintMode,
                        useModuleDependencyLintModels = false,
                        addBaseModuleLintModel,
                        warnIfProjectTreatedAsExternalDependency,
                        fatalOnly,
                        isPerComponentLintAnalysis
                    )
            }
        )
        mergedManifest.setDisallowChanges(
            variantCreationConfig.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        )
        // The manifest merge report contains absolute paths, so it's not compatible with the lint
        // analysis task being cacheable.
        if (lintMode != LintMode.ANALYSIS) {
            manifestMergeReport.set(
                variantCreationConfig.artifacts.get(InternalArtifactType.MANIFEST_MERGE_REPORT)
            )
        }
        manifestMergeReport.disallowChanges()
        namespace.setDisallowChanges(variantCreationConfig.namespace)

        minSdkVersion.initialize(variantCreationConfig.minSdk)

        val lintOptions = variantCreationConfig.global.lintOptions
        when (variantCreationConfig) {
            is ApkCreationConfig ->
                    targetSdkVersion.initialize(variantCreationConfig.targetSdk)
            is LibraryCreationConfig ->
                    targetSdkVersion.initialize(
                        createTargetSdkVersion(lintOptions.targetSdk,lintOptions.targetSdkPreview) ?: variantCreationConfig.targetSdk
                    )
            is KmpComponentCreationConfig -> targetSdkVersion.initialize(variantCreationConfig.minSdk)
        }

        resValues.setDisallowChanges(
            variantCreationConfig.resValuesCreationConfig?.resValues,
            handleNullable = { empty() }
        )

        if (variantCreationConfig is ApkCreationConfig) {
            manifestPlaceholders.setDisallowChanges(
                variantCreationConfig.manifestPlaceholdersCreationConfig?.placeholders,
                handleNullable = { empty() }
            )
        }

        resourceConfigurations.setDisallowChanges(
            variantCreationConfig.androidResourcesCreationConfig?.resourceConfigurations ?: emptyList()
        )

        if (includeMainArtifact) {
            mainSourceProvider.setDisallowChanges(
                variantCreationConfig.services
                    .newInstance(SourceProviderInput::class.java)
                    .initialize(
                        variantCreationConfig.sources,
                        lintMode,
                        projectDir = variantCreationConfig.services.provider { variantCreationConfig.services.projectInfo.projectDirectory }
                    )
            )

            sourceProviders.add(mainSourceProvider)

            proguardFiles.set(variantCreationConfig.optimizationCreationConfig.proguardFiles)

            extractedProguardFiles.set(
                variantCreationConfig.global.globalArtifacts.get(
                    InternalArtifactType.DEFAULT_PROGUARD_FILES
                )
            )
            consumerProguardFiles.set(
                variantCreationConfig.optimizationCreationConfig.consumerProguardFiles
            )
        }
        sourceProviders.disallowChanges()
        proguardFiles.disallowChanges()
        extractedProguardFiles.disallowChanges()
        consumerProguardFiles.disallowChanges()

        hostTestCreationConfig?.let {
            hostTestSourceProvider.set(
                variantCreationConfig.services
                    .newInstance(SourceProviderInput::class.java)
                    .initialize(
                        it.sources,
                        lintMode,
                        projectDir = variantCreationConfig.services.provider { variantCreationConfig.services.projectInfo.projectDirectory },
                        unitTestOnly = true
                    )
            )
        }

        deviceTestCreationConfig?.let {
            androidTestSourceProvider.set(
                variantCreationConfig.services
                    .newInstance(SourceProviderInput::class.java)
                    .initialize(
                        it.sources,
                        lintMode,
                        projectDir = variantCreationConfig.services.provider { variantCreationConfig.services.projectInfo.projectDirectory },
                        instrumentationTestOnly = true
                    )
            )
        }

        testFixturesCreationConfig?.let {
            testFixturesSourceProvider.set(
                variantCreationConfig.services
                    .newInstance(SourceProviderInput::class.java)
                    .initialize(
                        it.sources,
                        lintMode,
                        projectDir = variantCreationConfig.services.provider { variantCreationConfig.services.projectInfo.projectDirectory },
                        testFixtureOnly = true
                    )
            )
        }
        hostTestSourceProvider.disallowChanges()
        androidTestSourceProvider.disallowChanges()
        testFixturesSourceProvider.disallowChanges()
        debuggable.setDisallowChanges(variantCreationConfig.debuggable)
        shrinkable.setDisallowChanges(
            variantCreationConfig.optimizationCreationConfig.minifiedEnabled
        )
        useSupportLibraryVectorDrawables.setDisallowChanges(
            variantCreationConfig.androidResourcesCreationConfig?.vectorDrawables?.useSupportLibrary
                ?: false
        )
        buildFeatures.initialize(variantCreationConfig)
        initializeLibraryDependencyCacheBuildService(task)
        mavenCoordinatesCache.setDisallowChanges(getBuildService(variantCreationConfig.services.buildServiceRegistry))
    }

    internal fun initializeForStandalone(
        project: Project,
        task: Task,
        javaExtension: JavaPluginExtension,
        kotlinExtensionWrapper: KotlinMultiplatformExtensionWrapper?,
        projectOptions: ProjectOptions,
        fatalOnly: Boolean,
        useModuleDependencyLintModels: Boolean,
        lintMode: LintMode,
        lintModelArtifactType: LintModelArtifactType?,
        jvmTargetName: String?,
        testCompileClasspath: Configuration?,
        testRuntimeClasspath: Configuration?
    ) {
        if (kotlinExtensionWrapper == null) {
            initializeForStandalone(
                project,
                task,
                javaExtension,
                projectOptions,
                fatalOnly,
                useModuleDependencyLintModels,
                lintMode,
                lintModelArtifactType,
                testCompileClasspath,
                testRuntimeClasspath
            )
        } else {
            initializeForStandaloneWithKotlinMultiplatform(
                project,
                task,
                kotlinExtensionWrapper,
                projectOptions,
                fatalOnly,
                useModuleDependencyLintModels,
                lintMode,
                lintModelArtifactType!!,
                jvmTargetName,
                testCompileClasspath,
                testRuntimeClasspath
            )
        }
    }

    private fun initializeForStandalone(
        project: Project,
        task: Task,
        javaExtension: JavaPluginExtension,
        projectOptions: ProjectOptions,
        fatalOnly: Boolean,
        useModuleDependencyLintModels: Boolean,
        lintMode: LintMode,
        lintModelArtifactType: LintModelArtifactType?,
        testCompileClasspath: Configuration?,
        testRuntimeClasspath: Configuration?
    ) {
        val mainSourceSet = javaExtension.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        val testSourceSet = javaExtension.sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME)

        name.setDisallowChanges(mainSourceSet.name)
        this.useModuleDependencyLintModels.setDisallowChanges(useModuleDependencyLintModels)
        if (lintModelArtifactType == MAIN || lintModelArtifactType == null) {
            mainArtifact.set(
                project.objects
                    .newInstance(AndroidArtifactInput::class.java)
                    .initializeForStandalone(
                        project,
                        projectOptions,
                        mainSourceSet,
                        lintMode,
                        useModuleDependencyLintModels,
                        fatalOnly
                    )
            )
            mainSourceProvider.set(
                project.objects
                    .newInstance(SourceProviderInput::class.java)
                    .initializeForStandalone(
                        mainSourceSet,
                        lintMode,
                        unitTestOnly = false
                    )
            )
        }
        mainArtifact.disallowChanges()
        mainSourceProvider.disallowChanges()
        if (lintModelArtifactType == UNIT_TEST || lintModelArtifactType == null) {
            testArtifact.set(
                project.objects
                    .newInstance(JavaArtifactInput::class.java)
                    .initializeForStandalone(
                        project,
                        projectOptions,
                        testSourceSet,
                        lintMode,
                        if (lintModelArtifactType == null) {
                            // false in this case to avoid circular task dependencies (b/291934867)
                            false
                        } else {
                            useModuleDependencyLintModels
                        },
                        // analyzing test bytecode is expensive, without much benefit
                        includeClassesOutputDirectories = false,
                        fatalOnly,
                        mainSourceSet,
                        testCompileClasspath,
                        testRuntimeClasspath
                    )
            )
            if (!fatalOnly) {
                hostTestSourceProvider.set(
                    project.objects
                        .newInstance(SourceProviderInput::class.java)
                        .initializeForStandalone(
                            testSourceSet,
                            lintMode,
                            unitTestOnly = true
                        )
                )
            }
        }
        testArtifact.disallowChanges()
        hostTestSourceProvider.disallowChanges()
        androidTestArtifact.disallowChanges()
        testFixturesArtifact.disallowChanges()
        namespace.setDisallowChanges("")
        minSdkVersion.initializeEmpty()
        targetSdkVersion.initializeEmpty()
        manifestPlaceholders.disallowChanges()
        resourceConfigurations.disallowChanges()
        debuggable.setDisallowChanges(true)
        shrinkable.setDisallowChanges(false)
        useSupportLibraryVectorDrawables.setDisallowChanges(false)
        mergedManifest.setDisallowChanges(null)
        manifestMergeReport.setDisallowChanges(null)
        sourceProviders.add(mainSourceProvider)
        sourceProviders.disallowChanges()
        androidTestSourceProvider.disallowChanges()
        testFixturesSourceProvider.disallowChanges()
        buildFeatures.initializeForStandalone()
        initializeLibraryDependencyCacheBuildService(task)
        mavenCoordinatesCache.setDisallowChanges(getBuildService(project.gradle.sharedServices))
        proguardFiles.setDisallowChanges(null)
        extractedProguardFiles.setDisallowChanges(null)
        consumerProguardFiles.setDisallowChanges(null)
        resValues.disallowChanges()
    }

    private fun initializeForStandaloneWithKotlinMultiplatform(
        project: Project,
        task: Task,
        kotlinExtensionWrapper: KotlinMultiplatformExtensionWrapper,
        projectOptions: ProjectOptions,
        fatalOnly: Boolean,
        useModuleDependencyLintModels: Boolean,
        lintMode: LintMode,
        lintModelArtifactType: LintModelArtifactType,
        jvmTargetName: String?,
        testCompileClasspath: Configuration?,
        testRuntimeClasspath: Configuration?
    ) {
        val jvmTarget = kotlinExtensionWrapper.kotlinExtension.targets.findByName(jvmTargetName ?: "jvm")
        val jvmMainCompilation = jvmTarget?.compilations?.findByName("main")
        val jvmTestCompilation = jvmTarget?.compilations?.findByName("test")

        name.setDisallowChanges(jvmTarget?.name)
        this.useModuleDependencyLintModels.setDisallowChanges(useModuleDependencyLintModels)
        if (jvmMainCompilation != null && lintModelArtifactType == MAIN) {
            mainArtifact.set(
                project.objects
                    .newInstance(AndroidArtifactInput::class.java)
                    .initializeForStandaloneWithKotlinMultiplatform(
                        project,
                        projectOptions,
                        KotlinCompilationWrapper(jvmMainCompilation),
                        lintMode,
                        useModuleDependencyLintModels,
                        fatalOnly
                    )
            )
            val sourceDirectories =
                project.files().also { fileCollection ->
                    jvmMainCompilation.kotlinSourceSets.forEach {
                        fileCollection.from(it.kotlin.sourceDirectories)
                    }
                }
            mainSourceProvider.set(
                project.objects
                    .newInstance(SourceProviderInput::class.java)
                    .initializeForStandaloneWithKotlinMultiplatform(
                        sourceDirectories,
                        lintMode,
                        unitTestOnly = false
                    )
            )
        }
        mainArtifact.disallowChanges()
        mainSourceProvider.disallowChanges()
        if (jvmTestCompilation != null && lintModelArtifactType == UNIT_TEST) {
            testArtifact.set(
                project.objects
                    .newInstance(JavaArtifactInput::class.java)
                    .initializeForStandaloneWithKotlinMultiplatform(
                        project,
                        projectOptions,
                        KotlinCompilationWrapper(jvmTestCompilation),
                        lintMode,
                        useModuleDependencyLintModels,
                        // analyzing test bytecode is expensive, without much benefit
                        includeClassesOutputDirectories = false,
                        fatalOnly,
                        testCompileClasspath,
                        testRuntimeClasspath
                    )
            )
            val sourceDirectories =
                project.files().also { fileCollection ->
                    jvmTestCompilation.kotlinSourceSets.forEach {
                        fileCollection.from(it.kotlin.sourceDirectories)
                    }
                }
            hostTestSourceProvider.set(
                project.objects
                    .newInstance(SourceProviderInput::class.java)
                    .initializeForStandaloneWithKotlinMultiplatform(
                        sourceDirectories,
                        lintMode,
                        unitTestOnly = true
                    )
            )
        }
        testArtifact.disallowChanges()
        hostTestSourceProvider.disallowChanges()
        androidTestArtifact.disallowChanges()
        testFixturesArtifact.disallowChanges()
        namespace.setDisallowChanges("")
        minSdkVersion.initializeEmpty()
        targetSdkVersion.initializeEmpty()
        manifestPlaceholders.disallowChanges()
        resourceConfigurations.disallowChanges()
        debuggable.setDisallowChanges(true)
        shrinkable.setDisallowChanges(false)
        useSupportLibraryVectorDrawables.setDisallowChanges(false)
        mergedManifest.setDisallowChanges(null)
        manifestMergeReport.setDisallowChanges(null)
        sourceProviders.add(mainSourceProvider)
        sourceProviders.disallowChanges()
        androidTestSourceProvider.disallowChanges()
        testFixturesSourceProvider.disallowChanges()
        buildFeatures.initializeForStandalone()
        initializeLibraryDependencyCacheBuildService(task)
        mavenCoordinatesCache.setDisallowChanges(getBuildService(project.gradle.sharedServices))
        proguardFiles.setDisallowChanges(null)
        extractedProguardFiles.setDisallowChanges(null)
        consumerProguardFiles.setDisallowChanges(null)
        resValues.disallowChanges()
    }

    fun toLintModel(
        module: LintModelModule,
        partialResultsDir: File? = null,
        desugaredMethodsFiles: Collection<File>,
    ): LintModelVariant {
        val dependencyCaches = DependencyCaches(
            libraryDependencyCacheBuildService.get().localJarCache,
            mavenCoordinatesCache.get())

        return DefaultLintModelVariant(
            module,
            name.get(),
            useSupportLibraryVectorDrawables.get(),
            mainArtifact.orNull?.toLintModel(dependencyCaches, MAIN),
            testArtifact.orNull?.toLintModel(dependencyCaches, UNIT_TEST),
            androidTestArtifact.orNull
                ?.toLintModel(dependencyCaches, LintModelArtifactType.INSTRUMENTATION_TEST),
            testFixturesArtifact.orNull
                ?.toLintModel(dependencyCaches, LintModelArtifactType.TEST_FIXTURES),
            mergedManifest = mergedManifest.orNull?.asFile,
            manifestMergeReport = manifestMergeReport.orNull?.asFile,
            `package` = namespace.get(),
            minSdkVersion = minSdkVersion.toLintModel(),
            targetSdkVersion = targetSdkVersion.toLintModel(),
            resValues =
                resValues.get().map {
                    DefaultLintModelResourceField(
                        it.key.type,
                        it.key.name,
                        it.value.value
                    )
                }.associateBy { it.name },
            manifestPlaceholders = manifestPlaceholders.get(),
            resourceConfigurations = resourceConfigurations.get(),
            proguardFiles = proguardFiles.orNull?.map { it.asFile } ?: listOf(),
            consumerProguardFiles = consumerProguardFiles.orNull?.map { it.asFile } ?: listOf(),
            sourceProviders = mainSourceProvider.orNull?.toLintModels() ?: emptyList(),
            testSourceProviders = listOfNotNull(
                hostTestSourceProvider.orNull?.toLintModels(),
                androidTestSourceProvider.orNull?.toLintModels(),
            ).flatten(),
            testFixturesSourceProviders = testFixturesSourceProvider.orNull?.toLintModels() ?: emptyList(),
            debuggable = debuggable.get(),
            shrinkable = shrinkable.get(),
            buildFeatures = buildFeatures.toLintModel(),
            libraryResolver = DefaultLintModelLibraryResolver(dependencyCaches.libraryMap),
            partialResultsDir = partialResultsDir,
            desugaredMethodsFiles = desugaredMethodsFiles
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

    fun initialize(creationConfig: ComponentCreationConfig) {
        viewBinding.setDisallowChanges(creationConfig.buildFeatures.viewBinding)
        coreLibraryDesugaringEnabled.setDisallowChanges(
            (creationConfig as? ConsumableCreationConfig)?.isCoreLibraryDesugaringEnabledLintCheck
                ?: false
        )
        namespacingMode.setDisallowChanges(
            if (creationConfig.global.namespacedAndroidResources) {
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
    @get:InputFiles // Note: The file may not be set or may not exist
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val manifestFilePath: RegularFileProperty

    @get:InputFiles // Note: The files may not exist
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val manifestOverlayFilePaths: ListProperty<File>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val javaDirectories: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resDirectories: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetsDirectories: ConfigurableFileCollection

    // Without javaDirectoriesClasspath, the lint analysis task would be UP-TO-DATE after a change
    // in the *order* of java source directories, which would be incorrect. We can't get rid of
    // javaDirectories entirely because without javaDirectories, the lint analysis task would be
    // UP-TO-DATE after the addition or removal of a non-existent java source directory. We need to
    // set javaDirectoriesClasspath only for lint analysis tasks because other lint tasks set
    // javaDirectoryPaths.
    @get:Classpath
    @get:Optional
    abstract val javaDirectoriesClasspath: ConfigurableFileCollection

    // See comment for javaDirectoriesClasspath
    @get:Classpath
    @get:Optional
    abstract val resDirectoriesClasspath: ConfigurableFileCollection

    // See comment for javaDirectoriesClasspath
    @get:Classpath
    @get:Optional
    abstract val assetsDirectoriesClasspath: ConfigurableFileCollection

    @get:Input // Note: The files may not exist
    @get:Optional
    abstract val manifestAbsoluteFilePaths: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val javaDirectoryPaths: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val resDirectoryPaths: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val assetsDirectoryPaths: ListProperty<String>

    @get:Input
    abstract val debugOnly: Property<Boolean>

    @get:Input
    abstract val unitTestOnly: Property<Boolean>

    @get:Input
    abstract val instrumentationTestOnly: Property<Boolean>

    @get:Input
    abstract val testFixtureOnly: Property<Boolean>

    internal fun initialize(
        sources: InternalSources,
        lintMode: LintMode,
        projectDir: Provider<Directory>,
        unitTestOnly: Boolean = false,
        instrumentationTestOnly: Boolean = false,
        testFixtureOnly: Boolean = false
    ): SourceProviderInput {
        this.manifestFilePath.set(sources.manifestFile)
        this.manifestFilePath.disallowChanges()
        this.manifestOverlayFilePaths.setDisallowChanges(sources.manifestOverlayFiles)

        fun FlatSourceDirectoriesImpl.getFilteredSourceProviders(into: ConfigurableFileCollection) {
            return getVariantSources()
                .filter { dir -> !dir.isGenerated }
                .forEach {
                    into.from(it.asFiles(projectDir))
                }
        }

        fun LayeredSourceDirectoriesImpl.getFilteredSourceProviders(into: ConfigurableFileCollection) {
            return getVariantSources().forEach { dirs ->
                dirs.directoryEntries.filter { dir ->
                    !dir.isGenerated
                }.forEach {
                    into.from(it.asFiles(projectDir))
                }
            }
        }

        sources.java?.getFilteredSourceProviders(javaDirectories)
        sources.kotlin?.getFilteredSourceProviders(javaDirectories)
        javaDirectories.disallowChanges()

        sources.res?.getFilteredSourceProviders(this.resDirectories)
        resDirectories.disallowChanges()

        sources.assets?.getFilteredSourceProviders(assetsDirectories)
        assetsDirectories.disallowChanges()


        if (lintMode == LintMode.ANALYSIS) {
            this.javaDirectoriesClasspath.from(javaDirectories)
            this.resDirectoriesClasspath.from(resDirectories)
            this.assetsDirectoriesClasspath.from(assetsDirectories)
        } else {
            this.manifestAbsoluteFilePaths.add(manifestFilePath.map { it.asFile.absolutePath })
            this.manifestAbsoluteFilePaths.addAll(manifestOverlayFilePaths.map { it.map(File::getAbsolutePath) })
            this.javaDirectoryPaths.set(javaDirectories.elements.map { elements ->
                elements.map {
                    it.asFile.absolutePath
                }
            })
            this.resDirectoryPaths.set(resDirectories.elements.map { elements ->
                elements.map {
                    it.asFile.absolutePath
                }
            })
            this.assetsDirectoryPaths.set(assetsDirectories.elements.map { elements ->
                elements.map {
                    it.asFile.absolutePath
                }
            })
        }

        this.manifestAbsoluteFilePaths.disallowChanges()
        this.javaDirectoryPaths.disallowChanges()
        this.resDirectoryPaths.disallowChanges()
        this.assetsDirectoryPaths.disallowChanges()
        this.javaDirectoriesClasspath.disallowChanges()
        this.resDirectoriesClasspath.disallowChanges()
        this.assetsDirectoriesClasspath.disallowChanges()
        this.debugOnly.setDisallowChanges(false) //TODO
        this.unitTestOnly.setDisallowChanges(unitTestOnly)
        this.instrumentationTestOnly.setDisallowChanges(instrumentationTestOnly)
        this.testFixtureOnly.setDisallowChanges(testFixtureOnly)
        return this
    }

    internal fun initializeForStandalone(
        sourceSet: SourceSet,
        lintMode: LintMode,
        unitTestOnly: Boolean
    ): SourceProviderInput {
        this.manifestFilePath.disallowChanges()
        this.manifestOverlayFilePaths.disallowChanges()
        this.javaDirectories.fromDisallowChanges(sourceSet.allJava.sourceDirectories)
        this.resDirectories.disallowChanges()
        this.assetsDirectories.disallowChanges()
        if (lintMode == LintMode.ANALYSIS) {
            this.javaDirectoriesClasspath.from(sourceSet.allJava.sourceDirectories)
        }
        this.javaDirectoriesClasspath.disallowChanges()
        this.resDirectoriesClasspath.disallowChanges()
        this.assetsDirectoriesClasspath.disallowChanges()
        this.debugOnly.setDisallowChanges(false)
        this.unitTestOnly.setDisallowChanges(unitTestOnly)
        this.instrumentationTestOnly.setDisallowChanges(false)
        this.testFixtureOnly.setDisallowChanges(false)
        return this
    }

    internal fun initializeForStandaloneWithKotlinMultiplatform(
        sourceDirectories: FileCollection,
        lintMode: LintMode,
        unitTestOnly: Boolean
    ): SourceProviderInput {
        this.manifestFilePath.disallowChanges()
        this.manifestOverlayFilePaths.disallowChanges()
        this.javaDirectories.fromDisallowChanges(sourceDirectories)
        this.resDirectories.disallowChanges()
        this.assetsDirectories.disallowChanges()
        if (lintMode == LintMode.ANALYSIS) {
            this.javaDirectoriesClasspath.from(sourceDirectories)
        }
        this.javaDirectoriesClasspath.disallowChanges()
        this.resDirectoriesClasspath.disallowChanges()
        this.assetsDirectoriesClasspath.disallowChanges()
        this.debugOnly.setDisallowChanges(false)
        this.unitTestOnly.setDisallowChanges(unitTestOnly)
        this.instrumentationTestOnly.setDisallowChanges(false)
        this.testFixtureOnly.setDisallowChanges(false)
        return this
    }

    internal fun initializeForPrivacySandboxSdk(): SourceProviderInput {
        this.manifestFilePath.disallowChanges()
        this.manifestOverlayFilePaths.disallowChanges()
        this.javaDirectories.disallowChanges()
        this.resDirectories.disallowChanges()
        this.assetsDirectories.disallowChanges()
        this.javaDirectoriesClasspath.disallowChanges()
        this.resDirectoriesClasspath.disallowChanges()
        this.assetsDirectoriesClasspath.disallowChanges()
        this.debugOnly.setDisallowChanges(false)
        this.unitTestOnly.setDisallowChanges(false)
        this.instrumentationTestOnly.setDisallowChanges(false)
        this.testFixtureOnly.setDisallowChanges(false)
        return this
    }

    internal fun toLintModels(): List<LintModelSourceProvider> {
        return listOf(
            DefaultLintModelSourceProvider(
                // Pass the main manifest file if it is set, without checking whether it exists.
                // For overlay manifest files, we pass only those that exist.
                manifestFiles = listOfNotNull(manifestFilePath.orNull?.asFile) + manifestOverlayFilePaths.get().filter(File::isFile),
                javaDirectories = javaDirectories.files.toList(),
                resDirectories = resDirectories.files.toList(),
                assetsDirectories = assetsDirectories.files.toList(),
                debugOnly = debugOnly.get(),
                unitTestOnly = unitTestOnly.get(),
                instrumentationTestOnly = instrumentationTestOnly.get(),
                testFixture = testFixtureOnly.get()
            )
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

    internal fun initialize(version: com.android.build.api.variant.AndroidVersion) {
        apiLevel.setDisallowChanges(version.apiLevel)
        codeName.setDisallowChanges(version.codename)
    }

    internal fun initialize(apiLeve:Int, codename:String?) {
        apiLevel.setDisallowChanges(apiLeve)
        codeName.setDisallowChanges(codename)
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

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedSourceFolders: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedResourceFolders: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val desugaredMethodsFiles: ConfigurableFileCollection

    fun initialize(
        creationConfig: ComponentCreationConfig,
        lintMode: LintMode,
        useModuleDependencyLintModels: Boolean,
        addBaseModuleLintModel: Boolean,
        warnIfProjectTreatedAsExternalDependency: Boolean,
        fatalOnly: Boolean,
        isPerComponentLintAnalysis: Boolean,
        includeClassesOutputDirectories: Boolean = true,
        includeGeneratedSourceFolders: Boolean = true
    ): AndroidArtifactInput {
        applicationId.setDisallowChanges(creationConfig.applicationId)
        if (includeGeneratedSourceFolders) {
            generatedSourceFolders.from(
                getGeneratedSourceFoldersFileCollection(creationConfig)
            )
        }
        generatedSourceFolders.disallowChanges()
        generatedResourceFolders.fromDisallowChanges(getGeneratedResourceFolders(creationConfig))
        if (includeClassesOutputDirectories) {
            if (creationConfig is KmpComponentCreationConfig) {
                classesOutputDirectories.from(
                    creationConfig
                        .artifacts
                        .forScope(ScopedArtifacts.Scope.PROJECT)
                        .getFinalArtifacts(ScopedArtifact.CLASSES)
                )
            } else {
                classesOutputDirectories.from(creationConfig.artifacts.get(InternalArtifactType.JAVAC))
                creationConfig.getBuiltInKotlincOutput()?.let { classesOutputDirectories.from(it) }
                creationConfig.getBuiltInKaptArtifact(InternalArtifactType.BUILT_IN_KAPT_CLASSES_DIR)
                    ?.let { classesOutputDirectories.from(it) }
            }
            creationConfig.oldVariantApiLegacySupport?.variantData?.let {
                classesOutputDirectories.from(
                    it.allPreJavacGeneratedBytecode
                )
                classesOutputDirectories.from(it.allPostJavacGeneratedBytecode)
            }
            creationConfig.androidResourcesCreationConfig?.let {
                classesOutputDirectories.from(
                    it.getCompiledRClasses(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
                    )
                )
            }
        }
        classesOutputDirectories.disallowChanges()
        this.warnIfProjectTreatedAsExternalDependency.setDisallowChanges(warnIfProjectTreatedAsExternalDependency)
        this.ignoreUnexpectedArtifactTypes.setDisallowChanges(false)
        initializeProjectDependencyLintArtifacts(
            useModuleDependencyLintModels,
            creationConfig.variantDependencies,
            lintMode,
            // TODO(b/197322928) Initialize dependency partial results for nested components once
            //  there is a lint analysis task per component.
            isMainArtifact = !creationConfig.componentType.isNestedComponent,
            fatalOnly,
            isPerComponentLintAnalysis
        )
        if (!useModuleDependencyLintModels) {
            if (addBaseModuleLintModel) {
                initializeBaseModuleLintModel(creationConfig.variantDependencies)
            }
            projectRuntimeExplodedAars =
                creationConfig.variantDependencies.getArtifactCollectionForToolingModel(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    AndroidArtifacts.ArtifactType.LOCAL_EXPLODED_AAR_FOR_LINT
                )
            projectCompileExplodedAars =
                creationConfig.variantDependencies.getArtifactCollectionForToolingModel(
                    AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    AndroidArtifacts.ArtifactType.LOCAL_EXPLODED_AAR_FOR_LINT
                )
        }

        artifactCollectionsInputs.setDisallowChanges(
            ArtifactCollectionsInputsImpl(
                variantDependencies = creationConfig.variantDependencies,
                projectPath = creationConfig.services.projectInfo.path,
                variantName = creationConfig.name,
                runtimeType = ArtifactCollectionsInputs.RuntimeType.FULL,
            )
        )

        val coreLibDesugaring = (creationConfig as? ConsumableCreationConfig)?.isCoreLibraryDesugaringEnabledLintCheck
                ?: false
        desugaredMethodsFiles.from(
                getDesugaredMethods(
                        creationConfig.services,
                        coreLibDesugaring,
                        creationConfig.minSdk,
                        creationConfig.global
                )
        ).disallowChanges()

        return this
    }

    fun initializeForStandalone(
        project: Project,
        projectOptions: ProjectOptions,
        sourceSet: SourceSet,
        lintMode: LintMode,
        useModuleDependencyLintModels: Boolean,
        fatalOnly: Boolean
    ): AndroidArtifactInput {
        applicationId.setDisallowChanges("")
        generatedSourceFolders.disallowChanges()
        generatedResourceFolders.disallowChanges()
        desugaredMethodsFiles.disallowChanges()
        classesOutputDirectories.fromDisallowChanges(sourceSet.output.classesDirs)
        warnIfProjectTreatedAsExternalDependency.setDisallowChanges(false)
        ignoreUnexpectedArtifactTypes.setDisallowChanges(true)
        val variantDependencies = VariantDependencies(
            variantName = sourceSet.name,
            componentType = ComponentTypeImpl.JAVA_LIBRARY,
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
            projectOptions = projectOptions,
            isLibraryConstraintsApplied = false,
            isSelfInstrumenting = false,
        )
        artifactCollectionsInputs.setDisallowChanges(
            ArtifactCollectionsInputsImpl(
                variantDependencies = variantDependencies,
                projectPath = project.path,
                variantName = sourceSet.name,
                runtimeType = ArtifactCollectionsInputs.RuntimeType.FULL,
            )
        )
        initializeProjectDependencyLintArtifacts(
            useModuleDependencyLintModels,
            variantDependencies,
            lintMode,
            isMainArtifact = true,
            fatalOnly,
            projectOptions[BooleanOption.LINT_ANALYSIS_PER_COMPONENT]
        )
        return this
    }

    fun initializeForStandaloneWithKotlinMultiplatform(
        project: Project,
        projectOptions: ProjectOptions,
        kotlinCompilationWrapper: KotlinCompilationWrapper,
        lintMode: LintMode,
        useModuleDependencyLintModels: Boolean,
        fatalOnly: Boolean
    ): AndroidArtifactInput {
        val compilation = kotlinCompilationWrapper.kotlinCompilation
        applicationId.setDisallowChanges("")
        generatedSourceFolders.disallowChanges()
        generatedResourceFolders.disallowChanges()
        desugaredMethodsFiles.disallowChanges()
        classesOutputDirectories.fromDisallowChanges(compilation.output.classesDirs)
        warnIfProjectTreatedAsExternalDependency.setDisallowChanges(false)
        ignoreUnexpectedArtifactTypes.setDisallowChanges(true)
        val variantDependencies = VariantDependencies(
            variantName = compilation.name,
            componentType = ComponentTypeImpl.JAVA_LIBRARY,
            compileClasspath = project.configurations.getByName(compilation.compileDependencyConfigurationName),
            runtimeClasspath = project.configurations.getByName(compilation.runtimeDependencyConfigurationName ?: compilation.compileDependencyConfigurationName),
            sourceSetRuntimeConfigurations = listOf(),
            sourceSetImplementationConfigurations = listOf(),
            elements = mapOf(),
            providedClasspath = project.configurations.getByName(compilation.compileOnlyConfigurationName),
            annotationProcessorConfiguration = null,
            reverseMetadataValuesConfiguration = null,
            wearAppConfiguration = null,
            testedVariant = null,
            project = project,
            projectOptions = projectOptions,
            isLibraryConstraintsApplied = false,
            isSelfInstrumenting = false,
        )
        artifactCollectionsInputs.setDisallowChanges(
            ArtifactCollectionsInputsImpl(
                variantDependencies = variantDependencies,
                projectPath = project.path,
                variantName = compilation.name,
                runtimeType = ArtifactCollectionsInputs.RuntimeType.FULL,
            )
        )
        initializeProjectDependencyLintArtifacts(
            useModuleDependencyLintModels,
            variantDependencies,
            lintMode,
            isMainArtifact = true,
            fatalOnly,
            projectOptions[BooleanOption.LINT_ANALYSIS_PER_COMPONENT]
        )
        return this
    }

    fun initializeForPrivacySandboxSdk(
        project: Project,
        variantScope: PrivacySandboxSdkVariantScope,
        projectOptions: ProjectOptions,
        lintMode: LintMode,
        useModuleDependencyLintModels: Boolean,
        fatalOnly: Boolean
    ): AndroidArtifactInput {
        applicationId.setDisallowChanges("")
        generatedSourceFolders.disallowChanges()
        generatedResourceFolders.disallowChanges()
        desugaredMethodsFiles.disallowChanges()
        classesOutputDirectories.fromDisallowChanges(project.objects.fileCollection())
        warnIfProjectTreatedAsExternalDependency.setDisallowChanges(false)
        ignoreUnexpectedArtifactTypes.setDisallowChanges(true)
        val variantDependencies = VariantDependencies(
            variantName = variantScope.name,
            componentType = ComponentTypeImpl.PRIVACY_SANDBOX_SDK,
            compileClasspath = project.configurations.getByName("includeApiClasspath"),
            runtimeClasspath = project.configurations.getByName("includeRuntimeClasspath"),
            sourceSetRuntimeConfigurations = listOf(),
            sourceSetImplementationConfigurations = listOf(),
            elements = mapOf(),
            providedClasspath = null,
            annotationProcessorConfiguration = null,
            reverseMetadataValuesConfiguration = null,
            wearAppConfiguration = null,
            testedVariant = null,
            project = project,
            projectOptions = projectOptions,
            isLibraryConstraintsApplied = false,
            isSelfInstrumenting = false,
        )
        artifactCollectionsInputs.setDisallowChanges(
            ArtifactCollectionsInputsImpl(
                variantDependencies = variantDependencies,
                projectPath = project.path,
                variantName = variantScope.name,
                runtimeType = ArtifactCollectionsInputs.RuntimeType.FULL,
            )
        )
        initializeProjectDependencyLintArtifacts(
            useModuleDependencyLintModels,
            variantDependencies,
            lintMode,
            isMainArtifact = true,
            fatalOnly,
            projectOptions[BooleanOption.LINT_ANALYSIS_PER_COMPONENT]
        )
        return this
    }

    internal fun toLintModel(
        dependencyCaches: DependencyCaches,
        type: LintModelArtifactType
    ): LintModelAndroidArtifact {
        return DefaultLintModelAndroidArtifact(
            applicationId.get(),
            generatedResourceFolders.toList(),
            generatedSourceFolders.toList(),
            desugaredMethodsFiles.toList(),
            computeDependencies(dependencyCaches),
            classesOutputDirectories.files.toList(),
            type
        )
    }

    private fun getGeneratedResourceFolders(component: ComponentCreationConfig): FileCollection {
        val fileCollection = component.services.fileCollection()
        component.sources
            .res { resSources ->
                resSources.forAllSources { directoryEntry ->
                    if (directoryEntry.isUserAdded && directoryEntry.isGenerated) {
                        fileCollection.from(
                            directoryEntry.asFiles(
                                component.services
                                    .provider { component.services.projectInfo.projectDirectory }
                            )
                        )
                    }
                }
            }
        if (component.buildFeatures.renderScript) {
            fileCollection.from(component.artifacts.get(RENDERSCRIPT_GENERATED_RES))
        }
        if (component.buildFeatures.androidResources) {
            if (component.artifacts.get(GENERATED_RES).isPresent) {
                fileCollection.from(component.artifacts.get(GENERATED_RES))
            }
        }
        fileCollection.disallowChanges()
        return fileCollection
    }
}

/**
 * Inputs for a Java Artifact. This is used by [VariantInputs] for the unit test artifact.
 */
abstract class JavaArtifactInput : ArtifactInput() {

    fun initialize(
        creationConfig: HostTestCreationConfig,
        lintMode: LintMode,
        useModuleDependencyLintModels: Boolean,
        addBaseModuleLintModel: Boolean,
        warnIfProjectTreatedAsExternalDependency: Boolean,
        includeClassesOutputDirectories: Boolean,
        fatalOnly: Boolean,
        isPerComponentLintAnalysis: Boolean
    ): JavaArtifactInput {
        if (includeClassesOutputDirectories) {
            if (creationConfig is KmpComponentCreationConfig) {
                classesOutputDirectories.from(
                    creationConfig
                        .artifacts
                        .forScope(ScopedArtifacts.Scope.PROJECT)
                        .getFinalArtifacts(ScopedArtifact.CLASSES)
                )
            } else {
                classesOutputDirectories.from(creationConfig.artifacts.get(InternalArtifactType.JAVAC))
            }

            creationConfig.oldVariantApiLegacySupport?.variantData?.let {
                classesOutputDirectories.from(
                    it.allPreJavacGeneratedBytecode
                )
                classesOutputDirectories.from(it.allPostJavacGeneratedBytecode)
            }
            creationConfig.androidResourcesCreationConfig?.let {
                classesOutputDirectories.from(
                    it.getCompiledRClasses(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
                    )
                )
            }
        }
        classesOutputDirectories.disallowChanges()
        this.warnIfProjectTreatedAsExternalDependency.setDisallowChanges(warnIfProjectTreatedAsExternalDependency)
        this.ignoreUnexpectedArtifactTypes.setDisallowChanges(false)
        initializeProjectDependencyLintArtifacts(
            useModuleDependencyLintModels,
            creationConfig.variantDependencies,
            lintMode,
            // TODO(b/197322928) Initialize dependency partial results for unit test components once
            //  there is a lint analysis task per component.
            isMainArtifact = false,
            fatalOnly,
            isPerComponentLintAnalysis
        )
        if (!useModuleDependencyLintModels) {
            if (addBaseModuleLintModel) {
                initializeBaseModuleLintModel(creationConfig.variantDependencies)
            }
            projectRuntimeExplodedAars =
                creationConfig.variantDependencies.getArtifactCollectionForToolingModel(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    AndroidArtifacts.ArtifactType.LOCAL_EXPLODED_AAR_FOR_LINT
                )
            projectCompileExplodedAars =
                creationConfig.variantDependencies.getArtifactCollectionForToolingModel(
                    AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    AndroidArtifacts.ArtifactType.LOCAL_EXPLODED_AAR_FOR_LINT
                )
        }
        artifactCollectionsInputs.setDisallowChanges(
            ArtifactCollectionsInputsImpl(
                variantDependencies = creationConfig.variantDependencies,
                projectPath = creationConfig.services.projectInfo.path,
                variantName = creationConfig.name,
                runtimeType = ArtifactCollectionsInputs.RuntimeType.FULL,
            )
        )
        return this
    }

    fun initializeForStandalone(
        project: Project,
        projectOptions: ProjectOptions,
        sourceSet: SourceSet,
        lintMode: LintMode,
        useModuleDependencyLintModels: Boolean,
        includeClassesOutputDirectories: Boolean,
        fatalOnly: Boolean,
        testedSourceSet: SourceSet,
        compileClasspath: Configuration?,
        runtimeClasspath: Configuration?
    ): JavaArtifactInput {
        if (includeClassesOutputDirectories) {
            classesOutputDirectories.from(sourceSet.output.classesDirs)
        }
        classesOutputDirectories.disallowChanges()
        // Only ever used within the model builder in the standalone plugin
        warnIfProjectTreatedAsExternalDependency.setDisallowChanges(false)
        ignoreUnexpectedArtifactTypes.setDisallowChanges(true)

        // Use custom compile and runtime classpath configurations for unit tests for the
        // standalone lint plugin because the existing testCompileClasspath and testRuntimeClasspath
        // configurations don't include the main source set's jar output in their artifacts.
        val mainJarTask = project.tasks.named(testedSourceSet.jarTaskName, Jar::class.java)
        compileClasspath?.run {
            extendsFrom(
                project.configurations.getByName(sourceSet.compileClasspathConfigurationName)
            )
            project.dependencies
                .add(
                    name,
                    project.files(Callable { mainJarTask.flatMap { it.archiveFile } })
                )
        }
        runtimeClasspath?.run {
            extendsFrom(
                project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName)
            )
            project.dependencies
                .add(
                    name,
                    project.files(Callable { mainJarTask.flatMap { it.archiveFile } })
                )
        }

        val variantDependencies = VariantDependencies(
            variantName = sourceSet.name,
            componentType = ComponentTypeImpl.JAVA_LIBRARY,
            compileClasspath = compileClasspath ?: project.configurations.getByName(sourceSet.compileClasspathConfigurationName),
            runtimeClasspath = runtimeClasspath ?: project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName),
            sourceSetRuntimeConfigurations = listOf(),
            sourceSetImplementationConfigurations = listOf(),
            elements = mapOf(),
            providedClasspath = project.configurations.getByName(sourceSet.compileOnlyConfigurationName),
            annotationProcessorConfiguration = project.configurations.getByName(sourceSet.annotationProcessorConfigurationName),
            reverseMetadataValuesConfiguration = null,
            wearAppConfiguration = null,
            testedVariant = null,
            project = project,
            projectOptions = projectOptions,
            isLibraryConstraintsApplied = false,
            isSelfInstrumenting = false,
        )
        artifactCollectionsInputs.setDisallowChanges(
            ArtifactCollectionsInputsImpl(
                variantDependencies = variantDependencies,
                projectPath = project.path,
                variantName = sourceSet.name,
                runtimeType = ArtifactCollectionsInputs.RuntimeType.FULL,
            )
        )
        initializeProjectDependencyLintArtifacts(
            useModuleDependencyLintModels,
            variantDependencies,
            lintMode,
            // TODO(b/197322928) Initialize dependency partial results for unit test components once
            //  there is a lint analysis task per component.
            isMainArtifact = false,
            fatalOnly,
            projectOptions[BooleanOption.LINT_ANALYSIS_PER_COMPONENT]
        )
        return this
    }

    fun initializeForStandaloneWithKotlinMultiplatform(
        project: Project,
        projectOptions: ProjectOptions,
        kotlinCompilationWrapper: KotlinCompilationWrapper,
        lintMode: LintMode,
        useModuleDependencyLintModels: Boolean,
        includeClassesOutputDirectories: Boolean,
        fatalOnly: Boolean,
        compileClasspath: Configuration?,
        runtimeClasspath: Configuration?
    ): JavaArtifactInput {
        val compilation = kotlinCompilationWrapper.kotlinCompilation
        if (includeClassesOutputDirectories) {
            classesOutputDirectories.from(compilation.output.classesDirs)
        }
        classesOutputDirectories.disallowChanges()
        warnIfProjectTreatedAsExternalDependency.setDisallowChanges(false)
        ignoreUnexpectedArtifactTypes.setDisallowChanges(true)

        // Use custom compile and runtime dependency configurations for unit tests for the
        // standalone lint plugin because the existing compile and runtime dependency
        // configurations don't include the main jar output in their artifacts.
        val jvmTarget = kotlinCompilationWrapper.kotlinCompilation.target
        val mainJarTask = project.tasks.named("${jvmTarget.name}Jar", Jar::class.java)
        val compileClasspathForLint: Configuration =
            compileClasspath?.apply {
                this.extendsFrom(
                    project.configurations.getByName(compilation.compileDependencyConfigurationName)
                )
                project.dependencies
                    .add(
                        this.name,
                        project.files(Callable { mainJarTask.flatMap { it.archiveFile } })
                    )
            } ?: project.configurations.getByName(compilation.compileDependencyConfigurationName)
        val runtimeClasspathForLint: Configuration =
            compilation.runtimeDependencyConfigurationName?.let { runtimeConfigName ->
                runtimeClasspath?.apply {
                    this.extendsFrom(project.configurations.getByName(runtimeConfigName))
                    project.dependencies
                        .add(
                            this.name,
                            project.files(Callable { mainJarTask.flatMap { it.archiveFile } })
                        )
                }
            } ?: compileClasspathForLint

        val variantDependencies = VariantDependencies(
            variantName = compilation.name,
            componentType = ComponentTypeImpl.JAVA_LIBRARY,
            compileClasspath = compileClasspathForLint,
            runtimeClasspath = runtimeClasspathForLint,
            sourceSetRuntimeConfigurations = listOf(),
            sourceSetImplementationConfigurations = listOf(),
            elements = mapOf(),
            providedClasspath = project.configurations.getByName(compilation.compileOnlyConfigurationName),
            annotationProcessorConfiguration = null,
            reverseMetadataValuesConfiguration = null,
            wearAppConfiguration = null,
            testedVariant = null,
            project = project,
            projectOptions = projectOptions,
            isLibraryConstraintsApplied = false,
            isSelfInstrumenting = false,
        )
        artifactCollectionsInputs.setDisallowChanges(
            ArtifactCollectionsInputsImpl(
                variantDependencies = variantDependencies,
                projectPath = project.path,
                variantName = compilation.name,
                runtimeType = ArtifactCollectionsInputs.RuntimeType.FULL,
            )
        )
        initializeProjectDependencyLintArtifacts(
            useModuleDependencyLintModels,
            variantDependencies,
            lintMode,
            // TODO(b/197322928) Initialize dependency partial results for unit test components once
            //  there is a lint analysis task per component.
            isMainArtifact = false,
            fatalOnly,
            projectOptions[BooleanOption.LINT_ANALYSIS_PER_COMPONENT]
        )
        return this
    }

    internal fun toLintModel(
        dependencyCaches: DependencyCaches,
        type: LintModelArtifactType
    ): LintModelJavaArtifact {
        return DefaultLintModelJavaArtifact(
            computeDependencies(dependencyCaches),
            classesOutputDirectories.files.toList(),
            type
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

    @get:Classpath
    @get:Optional
    val projectRuntimeExplodedAarsFileCollection: FileCollection?
        get() = projectRuntimeExplodedAars?.artifactFiles

    @get:Internal
    var projectRuntimeExplodedAars: ArtifactCollection? = null

    @get:Classpath
    @get:Optional
    val projectCompileExplodedAarsFileCollection: FileCollection?
        get() = projectCompileExplodedAars?.artifactFiles

    @get:Internal
    var projectCompileExplodedAars: ArtifactCollection? = null

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val projectRuntimeLintModelsFileCollection: ConfigurableFileCollection

    @get:Internal
    abstract val projectRuntimeLintModels: Property<ArtifactCollection>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val projectCompileLintModelsFileCollection: ConfigurableFileCollection

    @get:Internal
    abstract val projectCompileLintModels: Property<ArtifactCollection>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val baseModuleLintModelFileCollection: ConfigurableFileCollection

    @get:Internal
    abstract val baseModuleLintModel: Property<ArtifactCollection>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val runtimeLintModelMetadataFileCollection: ConfigurableFileCollection

    @get:Internal
    abstract val runtimeLintModelMetadata: Property<ArtifactCollection>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val compileLintModelMetadataFileCollection: ConfigurableFileCollection

    @get:Internal
    abstract val compileLintModelMetadata: Property<ArtifactCollection>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val runtimeLintPartialResultsFileCollection: ConfigurableFileCollection

    @get:Internal
    abstract val runtimeLintPartialResults: Property<ArtifactCollection>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val compileLintPartialResultsFileCollection: ConfigurableFileCollection

    @get:Internal
    abstract val compileLintPartialResults: Property<ArtifactCollection>

    @get:Internal
    abstract val warnIfProjectTreatedAsExternalDependency: Property<Boolean>

    /**
     * Whether to ignore unexpected artifact types when resolving dependencies. This should be true
     * when running lint via the standalone plugin (b/198048896).
     */
    @get:Internal
    abstract val ignoreUnexpectedArtifactTypes: Property<Boolean>

    protected fun initializeProjectDependencyLintArtifacts(
        useModuleDependencyLintModels: Boolean,
        variantDependencies: VariantDependencies,
        lintMode: LintMode,
        isMainArtifact: Boolean,
        fatalOnly: Boolean,
        isPerComponentLintAnalysis: Boolean
    ) {
        if (useModuleDependencyLintModels) {
            val runtimeArtifacts = variantDependencies.getArtifactCollectionForToolingModel(
                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                AndroidArtifacts.ArtifactScope.PROJECT,
                AndroidArtifacts.ArtifactType.LINT_MODEL
            )
            projectRuntimeLintModels.set(runtimeArtifacts)
            projectRuntimeLintModelsFileCollection.from(runtimeArtifacts.artifactFiles)
            val compileArtifacts = variantDependencies.getArtifactCollectionForToolingModel(
                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                AndroidArtifacts.ArtifactScope.PROJECT,
                AndroidArtifacts.ArtifactType.LINT_MODEL
            )
            projectCompileLintModels.set(compileArtifacts)
            projectCompileLintModelsFileCollection.from(compileArtifacts.artifactFiles)
        } else {
            val runtimeLintModelMetadataArtifacts =
                variantDependencies.getArtifactCollectionForToolingModel(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    AndroidArtifacts.ArtifactType.LINT_MODEL_METADATA
                )
            runtimeLintModelMetadata.set(runtimeLintModelMetadataArtifacts)
            runtimeLintModelMetadataFileCollection.from(
                runtimeLintModelMetadataArtifacts.artifactFiles
            )
            val compileLintModelMetadataArtifacts =
                variantDependencies.getArtifactCollectionForToolingModel(
                    AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    AndroidArtifacts.ArtifactType.LINT_MODEL_METADATA
                )
            compileLintModelMetadata.set(compileLintModelMetadataArtifacts)
            compileLintModelMetadataFileCollection.from(
                compileLintModelMetadataArtifacts.artifactFiles
            )
            if (isMainArtifact && lintMode == LintMode.ANALYSIS && isPerComponentLintAnalysis) {
                val runtimeLintPartialResultsArtifacts =
                    variantDependencies.getArtifactCollectionForToolingModel(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        if (fatalOnly) {
                            AndroidArtifacts.ArtifactType.LINT_VITAL_PARTIAL_RESULTS
                        } else {
                            AndroidArtifacts.ArtifactType.LINT_PARTIAL_RESULTS
                        }
                    )
                runtimeLintPartialResults.set(runtimeLintPartialResultsArtifacts)
                runtimeLintPartialResultsFileCollection.from(
                    runtimeLintPartialResultsArtifacts.artifactFiles
                )
                val compileLintPartialResultsArtifacts =
                    variantDependencies.getArtifactCollectionForToolingModel(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        if (fatalOnly) {
                            AndroidArtifacts.ArtifactType.LINT_VITAL_PARTIAL_RESULTS
                        } else {
                            AndroidArtifacts.ArtifactType.LINT_PARTIAL_RESULTS
                        }
                    )
                compileLintPartialResults.set(compileLintPartialResultsArtifacts)
                compileLintPartialResultsFileCollection.from(
                    compileLintPartialResultsArtifacts.artifactFiles
                )
            }
        }
        projectRuntimeLintModels.disallowChanges()
        projectRuntimeLintModelsFileCollection.disallowChanges()
        projectCompileLintModels.disallowChanges()
        projectCompileLintModelsFileCollection.disallowChanges()
        runtimeLintModelMetadata.disallowChanges()
        runtimeLintModelMetadataFileCollection.disallowChanges()
        compileLintModelMetadata.disallowChanges()
        compileLintModelMetadataFileCollection.disallowChanges()
        runtimeLintPartialResults.disallowChanges()
        runtimeLintPartialResultsFileCollection.disallowChanges()
        compileLintPartialResults.disallowChanges()
        compileLintPartialResultsFileCollection.disallowChanges()
    }

    protected fun initializeBaseModuleLintModel(variantDependencies: VariantDependencies) {
        val artifactCollection = variantDependencies.getArtifactCollectionForToolingModel(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.PROJECT,
            AndroidArtifacts.ArtifactType.BASE_MODULE_LINT_MODEL
        )
        baseModuleLintModel.setDisallowChanges(artifactCollection)
        baseModuleLintModelFileCollection.fromDisallowChanges(artifactCollection.artifactFiles)
    }

    internal fun computeDependencies(dependencyCaches: DependencyCaches): LintModelDependencies {

        val artifactCollectionsInputs = artifactCollectionsInputs.get()

        val artifactHandler: ArtifactHandler<LintModelLibrary> =
            if (projectRuntimeLintModels.isPresent) {
                val thisProject =
                    ProjectKey(
                        artifactCollectionsInputs.buildPath.get(),
                        artifactCollectionsInputs.projectPath,
                        artifactCollectionsInputs.variantName
                    )
                CheckDependenciesLintModelArtifactHandler(
                    dependencyCaches,
                    thisProject,
                    projectRuntimeLintModels.get(),
                    projectCompileLintModels.get(),
                    artifactCollectionsInputs.compileClasspath.projectJars,
                    artifactCollectionsInputs.runtimeClasspath!!.projectJars,
                    warnIfProjectTreatedAsExternalDependency.get())
            } else {
                // When not checking dependencies, treat all dependencies as external, with the
                // possible exception of the base module dependency. (When writing a dynamic feature
                // lint model for publication, we want to model the base module dependency as a
                // module dependency, not as an external dependency.)
                ExternalLintModelArtifactHandler.create(
                    dependencyCaches,
                    projectRuntimeExplodedAars,
                    projectCompileExplodedAars,
                    null,
                    artifactCollectionsInputs.compileClasspath.projectJars,
                    artifactCollectionsInputs.runtimeClasspath!!.projectJars,
                    baseModuleLintModel.orNull,
                    runtimeLintModelMetadata.get(),
                    compileLintModelMetadata.get(),
                    runtimeLintPartialResults.orNull,
                    compileLintPartialResults.orNull,
                )
            }
        val modelBuilder = LintDependencyModelBuilder(
            artifactHandler = artifactHandler,
            libraryMap = dependencyCaches.libraryMap,
            mavenCoordinatesCache = dependencyCaches.mavenCoordinatesCache
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
            ignoreUnexpectedArtifactTypes = ignoreUnexpectedArtifactTypes.get(),
            issueReporter = issueReporter
        )

        return modelBuilder.createModel()
    }
}

/**
 * Inputs related to whether to use K2 UAST
 */
abstract class UastInputs  {

    /**
     * Whether to use K2 UAST when running the corresponding task. This provider will be set iff the
     * corresponding [OptionalBooleanOption.LINT_USE_K2_UAST] or [OptionalBoolean.LINT_USE_K2_UAST]
     * are set.
     *
     * If unset, K2 UAST will be used iff the corresponding kotlin language version is at least 2.0
     * (see [UsesUast.useK2Uast])
     */
    @get:Input
    @get:Optional
    abstract val useK2UastManualSetting: Property<Boolean>

    /**
     * The kotlin language version used by the corresponding [KotlinCompile] task. This property is
     * set via [KotlinCompile.compilerOptions], which is the replacement for the deprecated
     * [KotlinCompile.kotlinOptions].
     */
    @get:Input
    @get:Optional
    abstract val compilerOptionsKotlinLanguageVersion: Property<String>

    /**
     * The kotlin language version used by the corresponding [KotlinCompile] task. This property is
     * set via the deprecated [KotlinCompile.kotlinOptions].
     */
    @get:Input
    @get:Optional
    abstract val kotlinOptionsKotlinLanguageVersion: Property<String>

    /**
     * The default kotlin language version used by the corresponding [KotlinCompile] task, which is
     * used if the language version is not set on [KotlinCompile.compilerOptions] or
     * [KotlinCompile.kotlinOptions].
     */
    @get:Input
    @get:Optional
    abstract val defaultKotlinLanguageVersion: Property<String>

    @get:Internal
    val useK2Uast: Boolean
        @Suppress("UnstableApiUsage")
        get() {
            return useK2UastManualSetting.orNull
                ?: kotlinLanguageVersion
                    ?.let { Version.parse(it) >= Version.prefixInfimum("2") }
                ?: false
        }

    @get:Internal
    val kotlinLanguageVersion: String?
        get() =
            compilerOptionsKotlinLanguageVersion.orNull
                ?: kotlinOptionsKotlinLanguageVersion.orNull
                ?: defaultKotlinLanguageVersion.orNull

    fun initialize(project: Project, variant: VariantCreationConfig) {
        this.useK2UastManualSetting.setDisallowChanges(variant.lintUseK2UastManualSetting)
        val kotlinCompileTaskName =
            if (variant.componentType == ComponentTypeImpl.KMP_ANDROID) {
                "compileAndroidMain"
            } else {
                "compile".appendCapitalized(variant.name, "Kotlin")
            }
        initializeFromKotlinCompileTask(kotlinCompileTaskName, project)
    }

    fun initialize(variantScope: PrivacySandboxSdkVariantScope) {
        this.useK2UastManualSetting.setDisallowChanges(variantScope.lintUseK2UastManualSetting)
    }

    fun initializeForStandalone(
        project: Project,
        taskCreationServices: TaskCreationServices,
        kotlinCompileTaskName: String
    ) {
        this.useK2UastManualSetting
            .setDisallowChanges(
                taskCreationServices.projectOptions
                    .getProvider(OptionalBooleanOption.LINT_USE_K2_UAST)
            )
        initializeFromKotlinCompileTask(kotlinCompileTaskName, project)
    }

    /**
     * This function makes an effort to set the kotlin language version inputs based on the task
     * named [kotlinCompileTaskName], but it's not always possible (e.g., if KGP is not applied in
     * the same class loader). This function catches any exceptions that might be thrown because of
     * unexpected behavior from KGP.
     */
    private fun initializeFromKotlinCompileTask(
        kotlinCompileTaskName: String,
        project: Project
    ) {
        if (!isKotlinPluginAppliedInTheSameClassloader(project)) {
            return
        }
        val kotlinCompileTaskProvider: TaskProvider<KotlinCompile> =
            try {
                project.tasks.withType(KotlinCompile::class.java).named(kotlinCompileTaskName)
            } catch (e: UnknownDomainObjectException) {
                return
            }
        this.compilerOptionsKotlinLanguageVersion.set(
            kotlinCompileTaskProvider.flatMap { kotlinCompileTask ->
                runCatching {
                    kotlinCompileTask.compilerOptions.languageVersion.map { it.version }
                }.getOrNull()
                    ?: project.provider { null }
            }
        )
        this.compilerOptionsKotlinLanguageVersion.disallowChanges()
        // Ignore the type mismatch warning because the Gradle docs say "May return null"
        this.kotlinOptionsKotlinLanguageVersion.set(
            kotlinCompileTaskProvider.flatMap { kotlinCompileTask ->
                // languageVersion is defined as a String? so it's ok to wrap it in a Provider
                // as no task dependency needs to be carried over.
                runCatching { kotlinCompileTask.kotlinOptions.languageVersion }.getOrNull()?.let {
                    project.provider { it }
                } ?: project.provider { null }
            }
        )
        this.kotlinOptionsKotlinLanguageVersion.disallowChanges()
        this.defaultKotlinLanguageVersion.setDisallowChanges(
            runCatching { DEFAULT.version }.getOrNull()
        )
    }
}

class LintFromMaven(val files: FileCollection, val version: String) {

    companion object {
        @JvmStatic
        fun from(
            project: Project,
            projectOptions: ProjectOptions,
            issueReporter: IssueReporter,
        ): LintFromMaven {
            val lintVersion =
                getLintMavenArtifactVersion(
                    projectOptions[StringOption.LINT_VERSION_OVERRIDE]?.trim(),
                    issueReporter
                )
            val config =  project.configurations.detachedConfiguration(
                project.dependencies.create(
                    mapOf(
                        "group" to "com.android.tools.lint",
                        "name" to "lint-gradle",
                        "version" to lintVersion,
                    )
                )
            )
            config.isTransitive = true
            config.isCanBeConsumed = false
            config.isCanBeResolved = true
            return LintFromMaven(config, lintVersion)
        }
    }
}


/**
 * The lint binary uses the same version numbers as AGP (see LintCliClient#getClientRevision()
 * which is called when you run lint --version, as well as in release notes, etc etc).
 *
 * However, for historical reasons, the maven artifacts for its various libraries used in AGP are
 * using the older tools-base version numbers, which are 23 higher, so lint 7.0.0 is published
 * at com.android.tools.lint:lint-gradle:30.0.0
 *
 * This function maps from the user-oriented lint version specified by the user to the maven lint
 * library version number for the artifact to load.
 *
 * Returns the actual lint version to use, the given [versionOverride] if valid, otherwise the default,
 * reporting any issues as a side effect.
 */

internal fun getLintMavenArtifactVersion(
    versionOverride: String?,
    reporter: IssueReporter?,
    defaultVersion: String = com.android.Version.ANDROID_TOOLS_BASE_VERSION,
    agpVersion: String = com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION
): String {
    if (versionOverride == null) {
        return defaultVersion
    }
    // Only verify versions that parse. If it is not valid, it will fail later anyway.
    val parsed = AgpVersion.tryParse(versionOverride)
    if (parsed == null) {
        reporter?.reportError(
            IssueReporter.Type.GENERIC,
            """
                    Could not parse lint version override '$versionOverride'
                    Recommendation: Remove or update the gradle property ${StringOption.LINT_VERSION_OVERRIDE.propertyName} to be at least $agpVersion
                    """.trimIndent()
        )
        return defaultVersion
    }

    val default = AgpVersion.parse(defaultVersion)

    // Heuristic when given an AGP version, find the corresponding lint version (that's 23 higher)
    val normalizedOverride: String = (parsed.major + 23).toString() + versionOverride.removePrefix(parsed.major.toString())
    val normalizedParsed = AgpVersion.tryParse(normalizedOverride) ?: error("Unexpected parse error")

    if (normalizedParsed < default) {
        reporter?.reportError(
            IssueReporter.Type.GENERIC,
            """
                    Lint must be at least version $agpVersion
                    Recommendation: Remove or update the gradle property ${StringOption.LINT_VERSION_OVERRIDE.propertyName} to be at least $agpVersion
                    """.trimIndent()
        )
        return defaultVersion
    }
    return normalizedOverride
}

/**
 * A class to wrap [KotlinMultiplatformExtension]. If there are method parameters with type
 * [KotlinMultiplatformExtension] in task or task input classes, Gradle will fail at runtime for
 * projects without the Kotlin Gradle plugin applied. Using this wrapper class works around that
 * constraint.
 *
 * When using this class, perform a runtime check that the Kotlin Gradle plugin is applied.
 */
class KotlinMultiplatformExtensionWrapper(val kotlinExtension: KotlinMultiplatformExtension)

/**
 * A class to wrap [KotlinCompilation]. If there are method parameters with type [KotlinCompilation]
 * in task or task input classes, Gradle will fail at runtime for projects without the Kotlin
 * Gradle plugin applied. Using this wrapper class works around that constraint.
 *
 * When using this class, perform a runtime check that the Kotlin Gradle plugin is applied.
 */
class KotlinCompilationWrapper(val kotlinCompilation: KotlinCompilation<KotlinCommonOptions>)

enum class LintMode {
    ANALYSIS,
    EXTRACT_ANNOTATIONS,
    MODEL_WRITING,
    REPORTING,
    UPDATE_BASELINE,
}

const val LINT_XML_CONFIG_FILE_NAME = "lint.xml"
