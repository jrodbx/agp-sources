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

import com.android.build.api.component.impl.ComponentBuilderImpl
import com.android.build.api.variant.impl.LibraryVariantImpl
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.configure.CxxGradleTaskModel.Anchor
import com.android.build.gradle.internal.cxx.configure.CxxGradleTaskModel.Build
import com.android.build.gradle.internal.cxx.configure.CxxGradleTaskModel.Configure
import com.android.build.gradle.internal.cxx.configure.CxxGradleTaskModel.VariantBuild
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationParameters
import com.android.build.gradle.internal.cxx.gradle.generator.tryCreateConfigurationParameters
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxModuleModel
import com.android.build.gradle.internal.cxx.model.createCxxVariantModel
import com.android.build.gradle.internal.cxx.settings.calculateConfigurationArguments
import com.android.build.gradle.internal.cxx.timing.TimingEnvironment
import com.android.build.gradle.internal.cxx.timing.time
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JNI
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.tasks.PrefabModuleTaskData
import com.android.build.gradle.internal.tasks.PrefabPackageTask
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

/**
 * Construct gradle tasks for C/C++ configuration and build.
 */
fun <VariantBuilderT : ComponentBuilderImpl, VariantT : VariantImpl> createCxxTasks(
        androidLocationsProvider: AndroidLocationsProvider,
        sdkComponents: SdkComponentsBuildService,
        issueReporter: IssueReporter,
        taskFactory: TaskFactory,
        projectOptions: ProjectOptions,
        variants: List<ComponentInfo<VariantBuilderT, VariantT>>) {
    if (variants.isEmpty()) return
    IssueReporterLoggingEnvironment(
        issueReporter,
        variants.first().variant.services.projectInfo.getProject().rootDir).use {
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
                        configurationParameters)
            }
            val taskModel = createFoldedCxxTaskDependencyModel(abis)

            val global = variants.first().variant.globalScope

            val anchors = mutableMapOf<String, TaskProvider<Task>>()
            fun anchor(name: String) = anchors.computeIfAbsent(name) { taskFactory.register(name) }

            val variantMap = variants.map { it.variant.name to it.variant }.toMap()
            for ((name, task) in taskModel.tasks) {
                when (task) {
                    is Configure -> {
                        taskFactory.register(createCxxConfigureTask(
                                global,
                                task.representatives.toConfigurationModel(),
                                name))
                    }
                    is Build -> {
                        taskFactory.register(createWorkingCxxBuildTask(
                                global,
                                task.representatives.toConfigurationModel(),
                                name))
                    }
                    is VariantBuild -> {
                        val variant = variantMap.getValue(task.variantName)
                        val configuration = task.representatives.toConfigurationModel()
                        val task =
                                if (task.isRepublishOnly) {
                                    createRepublishCxxBuildTask(task.representatives.toConfigurationModel(),
                                            variant,
                                            name)
                                } else {
                                    createWorkingCxxBuildTask(global,
                                            task.representatives.toConfigurationModel(),
                                            name)
                                }
                        val buildTask = taskFactory.register(task)
                        variant.taskContainer.cxxConfigurationModel = configuration
                        variant.taskContainer.externalNativeBuildTask = buildTask
                        variant.taskContainer.compileTask.dependsOn(buildTask)
                        buildTask.dependsOn(variant.variantDependencies.getArtifactFileCollection(
                                RUNTIME_CLASSPATH,
                                ALL,
                                JNI))
                        val cleanTask =
                                taskFactory.register(createVariantCxxCleanTask(configuration,
                                        variant))
                        taskFactory.named("clean").dependsOn(cleanTask)
                    }
                    is Anchor -> anchor(name)
                }
            }

            for((dependant, dependee) in taskModel.edges) {
                taskFactory.named(dependant).dependsOn(taskFactory.named(dependee))
            }

            // Set up prefab publishing tasks if they are indicated.
            for(variant in variants) {
                val libraryVariant = variant.variant
                if (libraryVariant !is LibraryVariantImpl) continue
                createPrefabTasks(taskFactory, libraryVariant)
            }
        }
    }
}

fun createPrefabTasks(taskFactory: TaskFactory, libraryVariant: LibraryVariantImpl) {
    if (!libraryVariant.buildFeatures.prefabPublishing) return
    val global = libraryVariant.globalScope
    val extension = global.extension as LibraryExtension
    val project = libraryVariant.services.projectInfo.getProject()
    val modules = extension.prefab.map { options ->
        val headers = options.headers?.let { headers ->
            project.layout
                    .projectDirectory
                    .dir(headers)
                    .asFile
        }
        PrefabModuleTaskData(options.name, headers, options.libraryName)
    }
    if (modules.isNotEmpty()) {
        val packageTask= taskFactory.register(
                PrefabPackageTask.CreationAction(
                        modules,
                        global.sdkComponents.get(),
                        libraryVariant.taskContainer.cxxConfigurationModel!!,
                        libraryVariant))
        packageTask
                .get()
                .dependsOn(libraryVariant.taskContainer.externalNativeBuildTask)
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

    namer.configureAbis.forEach { (taskName, abis) ->
        tasks[taskName] = Configure(abis)
    }
    namer.buildAbis.forEach { (taskName, abis) ->
        tasks[taskName] = Build(abis)
    }
    namer.variantToConfiguration.forEach { (variantName, configureTasks) ->
        val taskName = "generateJsonModel".appendCapitalized(variantName)
        tasks[taskName] = Anchor(variantName)
        edges += configureTasks.map { configureTask -> taskName to configureTask }
    }
    namer.variantToBuild.forEach { (variantName, buildTasks) ->
        val taskName = "externalNativeBuild".appendCapitalized(variantName)
        tasks[taskName] = VariantBuild(variantName, true, variantAbis.getValue(variantName))
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
    configurationParameters: List<CxxConfigurationParameters>
) : List<CxxAbiModel> {
    return configurationParameters.flatMap { parameters ->
        val module = time("create-module-model") {
            createCxxModuleModel(sdkComponents, androidLocationsProvider, parameters)
        }
        val variant = time("create-variant-model") {
            createCxxVariantModel(parameters, module)
        }
        Abi.getDefaultValues().map { abi ->
            time("create-$abi-model") {
                createCxxAbiModel(sdkComponents, parameters, variant, abi)
                        .calculateConfigurationArguments()
            }
        }
    }
}

fun CxxAbiModel.toConfigurationModel() = listOf(this).toConfigurationModel()

private fun List<CxxAbiModel>.toConfigurationModel() =
        CxxConfigurationModel(
                variant = first().variant,
                activeAbis = filter { it.isActiveAbi },
                unusedAbis = filter { !it.isActiveAbi },
        )
