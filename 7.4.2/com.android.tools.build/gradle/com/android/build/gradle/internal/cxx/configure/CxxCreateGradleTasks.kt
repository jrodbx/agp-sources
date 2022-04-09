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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.api.variant.ComponentBuilder
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.configure.CxxGradleTaskModel.Build
import com.android.build.gradle.internal.cxx.configure.CxxGradleTaskModel.BuildGroup
import com.android.build.gradle.internal.cxx.configure.CxxGradleTaskModel.Configure
import com.android.build.gradle.internal.cxx.configure.CxxGradleTaskModel.ConfigureGroup
import com.android.build.gradle.internal.cxx.configure.CxxGradleTaskModel.VariantBuild
import com.android.build.gradle.internal.cxx.configure.CxxGradleTaskModel.VariantConfigure
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationParameters
import com.android.build.gradle.internal.cxx.gradle.generator.tryCreateConfigurationParameters
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxModuleModel
import com.android.build.gradle.internal.cxx.model.createCxxVariantModel
import com.android.build.gradle.internal.cxx.prefab.PrefabPublication
import com.android.build.gradle.internal.cxx.prefab.PrefabPublicationType.HeaderOnly
import com.android.build.gradle.internal.cxx.prefab.createPrefabPublication
import com.android.build.gradle.internal.cxx.prefab.writePublicationFile
import com.android.build.gradle.tasks.PrefabPackageConfigurationTask
import com.android.build.gradle.tasks.PrefabPackageTask
import com.android.build.gradle.internal.cxx.settings.calculateConfigurationArguments
import com.android.build.gradle.internal.cxx.timing.TimingEnvironment
import com.android.build.gradle.internal.cxx.timing.time
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JNI
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.tasks.factory.TaskFactory
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.android.build.gradle.internal.variant.ComponentInfo
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.tasks.createCxxConfigureTask
import com.android.build.gradle.tasks.createRepublishCxxBuildTask
import com.android.build.gradle.tasks.createVariantCxxCleanTask
import com.android.build.gradle.tasks.createWorkingCxxBuildTask
import com.android.builder.errors.IssueReporter
import com.android.prefs.AndroidLocationsProvider
import com.android.utils.appendCapitalized
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.PREFAB_PACKAGE
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.tasks.ExternalNativeBuildTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import java.io.File

/**
 * Create just the externalNativeBuild per-variant task.
 * This is done earlier so that callers of [taskContainer.externalNativeBuildTask]
 */
fun createCxxVariantBuildTask(
    taskFactory: TaskFactory,
    variant: VariantCreationConfig,
    providers: ProviderFactory,
    layout: ProjectLayout) {
    val configuration = tryCreateConfigurationParameters(
        variant.services.projectOptions,
        variant) ?: return
    val sdkComponentsBuildService: Provider<SdkComponentsBuildService> =
        getBuildService(variant.services.buildServiceRegistry)
    val androidLocationBuildService: Provider<AndroidLocationsBuildService> =
        getBuildService(variant.services.buildServiceRegistry)
    val configurationModel  = createInitialCxxModel(
        sdkComponentsBuildService.get(),
        androidLocationBuildService.get(),
        listOf(configuration),
        providers.createConfigurationTimeVersionExecutor(),
        providers,
        layout
    ).toConfigurationModel()
    variant.taskContainer.cxxConfigurationModel = configurationModel
    variant.taskContainer.externalNativeBuildTask =
        taskFactory.register(
            createRepublishCxxBuildTask(
                configurationModel,
                variant,
                variant.computeTaskName("externalNativeBuild")
            )
        )
}

/**
 * Construct gradle tasks for C/C++ configuration and build.
 */
fun <VariantBuilderT : ComponentBuilder, VariantT : VariantCreationConfig> createCxxTasks(
        androidLocationsProvider: AndroidLocationsProvider,
        sdkComponents: SdkComponentsBuildService,
        issueReporter: IssueReporter,
        taskFactory: TaskFactory,
        projectOptions: ProjectOptions,
        variants: Collection<ComponentInfo<VariantBuilderT, VariantT>>,
        providers: ProviderFactory,
        layout: ProjectLayout) {
    if (variants.isEmpty()) return
    IssueReporterLoggingEnvironment(
        issueReporter,
        variants.first().variant.services.projectInfo.rootDir,
        null).use {
        val configurationParameters = variants
                .mapNotNull { tryCreateConfigurationParameters(
                    projectOptions,
                    it.variant) }
        if (configurationParameters.isEmpty()) return
            TimingEnvironment(
                configurationParameters.first().intermediatesFolder.resolve("cxx"),
                "create_cxx_tasks").use {
            val abis = time("create-initial-cxx-model") {
                createInitialCxxModel(sdkComponents,
                        androidLocationsProvider,
                        configurationParameters,
                        providers.createConfigurationTimeVersionExecutor(),
                        providers,
                        layout
                )
            }
            val taskModel = createFoldedCxxTaskDependencyModel(abis)

            val globalConfig = variants.first().variant.global

            val variantMap = variants.associate { it.variant.name to it.variant }

            for ((name, task) in taskModel.tasks) {
                when (task) {
                    is Configure -> {
                        val variant = variantMap.getValue(task.representative.variant.variantName)
                        val configureTask = taskFactory.register(createCxxConfigureTask(
                            globalConfig,
                            variant,
                            task.representative,
                            name))
                        // Make sure any prefab configurations are generated first
                        configureTask.dependsOn(
                            variant.variantDependencies.getArtifactCollection(
                                COMPILE_CLASSPATH,
                                ALL,
                                AndroidArtifacts.ArtifactType.PREFAB_PACKAGE_CONFIGURATION
                            ).artifactFiles
                        )
                        configureTask.dependsOn(variant.taskContainer.preBuildTask)
                    }
                    is ConfigureGroup -> {
                        taskFactory.register(name)
                    }
                    is VariantConfigure -> {
                        val configuration = task.representatives.toConfigurationModel()
                        val variant = variantMap.getValue(configuration.variant.variantName)
                        val configureTask = taskFactory.register(name)
                        // Add prefab configure task
                        if (variant is LibraryCreationConfig &&
                            variant.buildFeatures.prefabPublishing) {
                            val publication = createPrefabPublication(configuration, variant)
                            if (configuration.variant.module.project.isBuildOnlyTargetAbiEnabled) {
                                // Write the header-only publication only if this is an IDE build.
                                HeaderOnly.writePublicationFile(publication)
                            }
                            createPrefabConfigurePackageTask(
                                taskFactory,
                                publication,
                                configureTask,
                                variant)
                        }
                    }
                    is Build -> {
                        val variant = variantMap.getValue(task.representative.variant.variantName)
                        val buildTask = taskFactory.register(createWorkingCxxBuildTask(
                            globalConfig,
                            task.representative,
                            name))
                        // Make sure any prefab dependencies are built first
                        buildTask.dependsOn(
                            variant.variantDependencies.getArtifactCollection(
                                COMPILE_CLASSPATH,
                                ALL,
                                PREFAB_PACKAGE
                            ).artifactFiles
                        )
                    }
                    is BuildGroup -> {
                        taskFactory.register(name)
                    }
                    is VariantBuild -> {
                        val configuration = task.representatives.toConfigurationModel()
                        val variant = variantMap.getValue(configuration.variant.variantName)
                        val buildTask = variant.taskContainer.externalNativeBuildTask!!
                        variant.taskContainer.compileTask.dependsOn(buildTask)
                        buildTask.dependsOn(variant.variantDependencies.getArtifactFileCollection(
                                RUNTIME_CLASSPATH,
                                ALL,
                                JNI))
                        val cleanTask =
                                taskFactory.register(createVariantCxxCleanTask(configuration,
                                        variant))
                        taskFactory.named("clean").dependsOn(cleanTask)

                        // Add prefab package task
                        if (variant is LibraryCreationConfig &&
                            variant.buildFeatures.prefabPublishing) {
                            val publication = createPrefabPublication(configuration, variant)

                            createPrefabPackageTask(
                                taskFactory,
                                publication,
                                buildTask,
                                variant)
                        }
                    }
                }
            }

            // Establish dependency edges
            for((dependant, dependee) in taskModel.edges) {
                taskFactory.named(dependant).dependsOn(taskFactory.named(dependee))
            }
        }
    }
}

private fun createPrefabConfigurePackageTask(
    taskFactory: TaskFactory,
    publication: PrefabPublication,
    configureTask: TaskProvider<Task>,
    libraryVariant: LibraryCreationConfig) {
    if (publication.packageInfo.modules.isNotEmpty()) {
        val task = taskFactory.register(
            PrefabPackageConfigurationTask.CreationAction(
                publication,
                libraryVariant.prefabConfigurePackageTaskName(),
                libraryVariant))
        task.dependsOn(configureTask)
    }
}

private fun createPrefabPackageTask(
    taskFactory: TaskFactory,
    publication: PrefabPublication,
    buildTask: TaskProvider<out ExternalNativeBuildTask>,
    libraryVariant: LibraryCreationConfig) {
    if (publication.packageInfo.modules.isNotEmpty()) {
        val packageTask = taskFactory.register(
            PrefabPackageTask.CreationAction(
                publication,
                libraryVariant.prefabPackageTaskName(),
                libraryVariant))
        packageTask
            .get()
            .dependsOn(libraryVariant.prefabConfigurePackageTaskName())
            .dependsOn(buildTask)
    }
}

/**
 * Create the folded task dependency model. C/C++ build outputs are placed in a folder that is
 * unique for the given C++ configuration. If two tasks map to the same configuration then they
 * will have the same task name and duplicates are automatically removed.
 *
 * The logic that actually determines the output folder names is in
 * [CxxAbiModel::getAndroidGradleSettings].
 */
fun createFoldedCxxTaskDependencyModel(globalAbis: List<CxxAbiModel>) : CxxTaskDependencyModel {
    if (globalAbis.isEmpty()) return CxxTaskDependencyModel(tasks = mapOf(), edges=listOf())
    val tasks = mutableMapOf<String, CxxGradleTaskModel>()
    val edges = mutableListOf<Pair<String, String>>()
    val namer = CxxConfigurationFolding(globalAbis)

    val variantAbis = globalAbis
            .groupBy { it.variant.variantName }

    namer.configureAbis.forEach { (taskName, abi) ->
        tasks[taskName] = Configure(abi)
    }
    namer.configureGroups.forEach { (groupingTask, configureTasks) ->
        tasks[groupingTask] = ConfigureGroup
        configureTasks.forEach { configureTask ->
            edges += groupingTask to configureTask
        }
    }
    namer.buildAbis.forEach { (taskName, abi) ->
        tasks[taskName] = Build(abi)
    }
    namer.buildGroups.forEach { (groupingTask, buildTasks) ->
        tasks[groupingTask] = BuildGroup
        buildTasks.forEach { buildTask ->
            edges += groupingTask to buildTask
        }
    }
    namer.variantToConfiguration.forEach { (variantName, configureTasks) ->
        val taskName = "generateJsonModel".appendCapitalized(variantName)
        tasks[taskName] = VariantConfigure(variantAbis.getValue(variantName))
        edges += configureTasks.map { configureTask -> taskName to configureTask }
    }
    namer.variantToBuild.forEach { (variantName, buildTasks) ->
        val taskName = "externalNativeBuild".appendCapitalized(variantName)
        tasks[taskName] = VariantBuild(variantAbis.getValue(variantName))
        edges += buildTasks.map { buildTask -> taskName to buildTask }
    }
    edges += namer.buildConfigureEdges

    return CxxTaskDependencyModel(
            tasks = tasks,
            edges = edges.distinct()
    )
}

/**
 * Create the [CxxAbiModel]s for a given build.
 */
fun createInitialCxxModel(
    sdkComponents: SdkComponentsBuildService,
    androidLocationsProvider: AndroidLocationsProvider,
    configurationParameters: List<CxxConfigurationParameters>,
    versionExecutor: (File) -> String,
    providers: ProviderFactory,
    layout: ProjectLayout
) : List<CxxAbiModel> {

    return configurationParameters.flatMap { parameters ->
        val module = time("create-module-model") {
            createCxxModuleModel(
                sdkComponents,
                androidLocationsProvider,
                versionExecutor,
                parameters)
        }
        val variant = time("create-variant-model") {
            createCxxVariantModel(parameters, module)
        }
        Abi.getDefaultValues().map { abi ->
            time("create-$abi-model") {
                createCxxAbiModel(sdkComponents, parameters, variant, abi)
                        .calculateConfigurationArguments(providers, layout)
            }
        }
    }
}

private fun ProviderFactory.createConfigurationTimeVersionExecutor() : (File) -> String = { exe ->
    exec { spec ->
        spec.commandLine(exe.path, "--version")
    }.standardOutput.asText.get()
}

private fun List<CxxAbiModel>.toConfigurationModel() =
        CxxConfigurationModel(
                variant = first().variant,
                activeAbis = filter { it.isActiveAbi },
                unusedAbis = filter { !it.isActiveAbi },
        )

/**
 * Return the name of the prefab[Variant]Package task.
 */
private fun ComponentCreationConfig.prefabPackageTaskName() =
    computeTaskName("prefab", "Package")

/**
 * Return the name of the prefab[ConfigurePackage]Package task.
 */
private fun ComponentCreationConfig.prefabConfigurePackageTaskName() =
    computeTaskName("prefab", "ConfigurePackage")
