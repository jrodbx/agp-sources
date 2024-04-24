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
import com.android.build.gradle.internal.cxx.logging.logStructured
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxModuleModel
import com.android.build.gradle.internal.cxx.model.createCxxVariantModel
import com.android.build.gradle.internal.cxx.prefab.PrefabPublication
import com.android.build.gradle.internal.cxx.prefab.createPrefabPublication
import com.android.build.gradle.internal.cxx.settings.calculateConfigurationArguments
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JNI
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.PREFAB_PACKAGE
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.tasks.factory.TaskFactory
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.android.build.gradle.internal.variant.ComponentInfo
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.tasks.ExternalNativeBuildTask
import com.android.build.gradle.tasks.PrefabPackageConfigurationTask
import com.android.build.gradle.tasks.PrefabPackageTask
import com.android.build.gradle.tasks.createCxxConfigureTask
import com.android.build.gradle.tasks.createRepublishCxxBuildTask
import com.android.build.gradle.tasks.createVariantCxxCleanTask
import com.android.build.gradle.tasks.createWorkingCxxBuildTask
import com.android.builder.errors.IssueReporter
import com.android.prefs.AndroidLocationsProvider
import com.android.utils.appendCapitalized
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.TaskProvider

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
        project: Project) {
    if (variants.isEmpty()) return
    val providers = project.providers
    val layout = project.layout
    IssueReporterLoggingEnvironment(
        issueReporter = issueReporter,
        rootBuildGradleFolder = variants.first().variant.services.projectInfo.rootDir,
        allowStructuredLogging = false, // Don't want to write files during configuration phase
        cxxFolder = null
    ).use {
        val configurationParameters = variants
                .mapNotNull { tryCreateConfigurationParameters(
                    projectOptions,
                    it.variant,
                ) }
        if (configurationParameters.isEmpty()) return
        NativeLocationsBuildService.register(project)

        val abis = createInitialCxxModel(sdkComponents,
                    configurationParameters,
                    providers,
                    layout
            )

        val variantMap = variants.associate { it.variant.name to it.variant }

        val taskModel = createFoldedCxxTaskDependencyModel(abis)

        val globalConfig = variants.first().variant.global

        for ((name, task) in taskModel.tasks) {
            when (task) {
                is Configure -> {
                    val variant = variantMap.getValue(task.representative.variant.variantName)
                    val configureTask = taskFactory.register(createCxxConfigureTask(
                        project,
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
                        val publication = createPrefabPublication(
                            configuration,
                            variant,
                            variant.nativeBuildCreationConfig!!
                        )

                        // Write the header-only publication.
                        writeHeaderOnlyPublicationFile(providers, publication)

                        createPrefabConfigurePackageTask(
                            taskFactory,
                            publication,
                            configureTask,
                            variant)
                    }
                }
                is Build -> {
                    val coveredVariantConfigurations =  task.coveredVariants.map { variantMap.getValue(it.variantName) }
                    val buildTask = taskFactory.register(createWorkingCxxBuildTask(
                        coveredVariantConfigurations,
                        globalConfig,
                        task.representative,
                        name))
                    for(variant in task.coveredVariants) {
                        val variantConfiguration = variantMap.getValue(task.representative.variant.variantName)

                        // Make sure any prefab dependencies are built first
                        buildTask.dependsOn(
                            variantConfiguration.variantDependencies.getArtifactCollection(
                                COMPILE_CLASSPATH,
                                ALL,
                                PREFAB_PACKAGE
                            ).artifactFiles
                        )
                    }
                }
                is BuildGroup -> {
                    taskFactory.register(name)
                }
                is VariantBuild -> {
                    val configuration = task.representatives.toConfigurationModel()
                    val variant = variantMap.getValue(configuration.variant.variantName)
                    val buildTask = taskFactory.register(
                        createRepublishCxxBuildTask(
                            configuration,
                            variant,
                            variant.computeTaskName("externalNativeBuild")
                        )
                    )
                    variant.taskContainer.cxxConfigurationModel = configuration
                    variant.taskContainer.externalNativeBuildTask = buildTask
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
                        val publication = createPrefabPublication(
                            configuration,
                            variant,
                            variant.nativeBuildCreationConfig!!
                        )

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
    val variantsByName = globalAbis.map { it.variant }.distinct().associateBy { it.variantName }
    val tasks = mutableMapOf<String, CxxGradleTaskModel>()
    val edges = mutableListOf<Pair<String, String>>()
    val namer = CxxConfigurationFolding(globalAbis)

    val variantAbis = globalAbis
            .groupBy { it.variant.variantName }

    namer.configureAbis.forEach { (taskName, target) ->
        val (variantsCovered, abi) = target
        tasks[taskName] = Configure(variantsCovered.map { variantsByName.getValue(it) }, abi)
    }
    namer.configureGroups.forEach { (groupingTask, configureTasks) ->
        tasks[groupingTask] = ConfigureGroup
        configureTasks.forEach { configureTask ->
            edges += groupingTask to configureTask
        }
    }
    namer.buildAbis.forEach { (taskName, target) ->
        val (variantsCovered, abi) = target
        tasks[taskName] = Build(variantsCovered.map { variantsByName.getValue(it) }, abi)
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
    configurationParameters: List<CxxConfigurationParameters>,
    providers: ProviderFactory,
    layout: ProjectLayout
) : List<CxxAbiModel> {

    return configurationParameters.flatMap { parameters ->
        // Log the fact that configuration parameters were constructed.
        logStructured { encoder ->
            CreateCxxModel.newBuilder()
                .setGradlePath(parameters.gradleModulePathName)
                .setVariantName(parameters.variantName)
                .build()
                .encode(encoder)
        }

        val module =
            createCxxModuleModel(
                sdkComponents,
                parameters,
            )
        val variant = createCxxVariantModel(parameters, module)
        module.ndkMetaAbiList
            .map { abi -> createCxxAbiModel(sdkComponents, parameters, variant, abi.name)
                            .calculateConfigurationArguments(providers, layout)
        }
    }
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
