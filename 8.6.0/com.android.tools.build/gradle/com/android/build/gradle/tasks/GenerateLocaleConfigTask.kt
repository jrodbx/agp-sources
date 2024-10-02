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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.AndroidJarInput
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.aapt.WorkerExecutorResourceCompilationService
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.initialize
import com.android.build.gradle.internal.process.GradleProcessExecutor
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.services.getAapt2Executable
import com.android.build.gradle.internal.services.getLeasingAapt2
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.utils.toImmutableSet
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.core.ApkInfoParser
import com.android.builder.core.ComponentTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.builder.internal.aapt.AaptUtils
import com.android.ide.common.resources.CompileResourceRequest
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.generateLocaleString
import com.android.ide.common.resources.readSupportedLocales
import com.android.ide.common.resources.writeLocaleConfig
import com.android.utils.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

const val LOCALE_CONFIG_FILE_NAME = "_generated_res_locale_config"

/**
 * Task to generate locale configuration from res qualifiers
 *
 * Additionally, if non-density resource configurations are present in the project, we must filter
 * the locales in the config to only include the locales that these res configs filter into the APK.
 * This requires us to create a mini-AAPT2 workflow that compiles project resources and links them
 * along with the res configs to get the locales which will be in the APK. We cannot use
 * PROCESSED_RES since this creates a circular dependency with this task and MergeResources.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ANDROID_RESOURCES)
abstract class GenerateLocaleConfigTask : NonIncrementalTask() {

    // A generated xml res file containing the supported locales
    @get:OutputDirectory
    abstract val localeConfig: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val appLocales: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dependencyLocales: ConfigurableFileCollection

    // The below properties are optional because they are only used if non-density resource
    // configurations are specified
    @get:Optional
    @get:Input
    abstract val resConfigs: SetProperty<String>

    @get:Internal
    abstract val resApkDir: DirectoryProperty

    @get:Internal
    abstract val tempProjectDir: DirectoryProperty

    @get:Internal
    abstract val compiledResOutput: DirectoryProperty

    // These properties are only used with res configs as well, but they cannot be optional because
    // they use `Nested` And `Inject`
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Nested
    abstract val aapt2: Aapt2Input

    @get:Nested
    abstract val androidJarInput: AndroidJarInput

    public override fun doTaskAction() {

        workerExecutor.noIsolation().submit(GenerateLocaleWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.appLocales.set(appLocales)
            it.dependencyLocales.setFrom(dependencyLocales)
            it.localeConfig.set(localeConfig)
            it.resConfigs.set(resConfigs)
            it.aapt2.set(aapt2)
            it.resApkDir.set(resApkDir)
            it.compiledResOutput.set(compiledResOutput)
            it.tempProjectDir.set(tempProjectDir)
            it.androidJarInput.set(androidJarInput)
        }
    }

    /** [WorkParameters] for [GenerateLocaleWorkAction] */
    abstract class GenerateLocaleWorkParameters : ProfileAwareWorkAction.Parameters() {

        abstract val appLocales: RegularFileProperty
        abstract val dependencyLocales: ConfigurableFileCollection
        abstract val localeConfig: DirectoryProperty
        abstract val resConfigs: ListProperty<String>
        abstract val aapt2: Property<Aapt2Input>
        abstract val resApkDir: DirectoryProperty
        abstract val compiledResOutput: DirectoryProperty
        abstract val tempProjectDir: DirectoryProperty
        abstract val androidJarInput: Property<AndroidJarInput>
    }

    abstract class GenerateLocaleWorkAction: ProfileAwareWorkAction<GenerateLocaleWorkParameters>() {

        @get:Inject
        abstract val workerExecutor: WorkerExecutor

        @get:Inject
        abstract val execOperations: ExecOperations

        override fun run() {
            // List of locales merged from app and dependencies
            val folderLocales = mutableSetOf<String>()
            // Add app locales
            val appLocales = readSupportedLocales(parameters.appLocales.get().asFile)
            folderLocales.addAll(appLocales.folderLocales)
            // Add locales from dependencies
            parameters.dependencyLocales.files.forEach {
                folderLocales.addAll(it.readLines())
            }

            var finalLocales = mutableSetOf(appLocales.defaultLocale)
            val mergedLocaleQualifiers = folderLocales.map {
                FolderConfiguration.getConfigFromQualifiers(it.split("-")).localeQualifier
            }
            mergedLocaleQualifiers.forEach {
                finalLocales.add(generateLocaleString(it))
            }

            if (parameters.resConfigs.isPresent && parameters.resConfigs.get().isNotEmpty()) {
                compileResFilesWithAapt2(folderLocales)
                runAapt2Link()

                // Get the contents of the res apk and extract the locales
                val parser = ApkInfoParser(
                    parameters.aapt2.get().getAapt2Executable().toFile(),
                    GradleProcessExecutor(execOperations::exec)
                )
                val apkAaptOutput =
                    parser.getAaptOutput(parameters.resApkDir.get().file("res.apk").asFile)
                val apkLocales = ApkInfoParser.getLocalesFromApkContents(apkAaptOutput)

                if (!apkLocales.isNullOrEmpty()) {
                    finalLocales = finalLocales.filter {
                        apkLocales.contains(it) || it == appLocales.defaultLocale
                    }.toImmutableSet()
                }
            }

            val localeConfigFolder = parameters.localeConfig.get().asFile
            val localeConfigFile =
                File(localeConfigFolder, "xml${File.separator}$LOCALE_CONFIG_FILE_NAME.xml")
            localeConfigFile.parentFile.mkdirs()

            writeLocaleConfig(output = localeConfigFile, finalLocales)
        }

        // Create temp res files from the project and dependencies and compiles with AAPT2 to
        // generate compiled res output
        private fun compileResFilesWithAapt2(folderLocales: Set<String>) {
            parameters.compiledResOutput.get().asFile.mkdirs()
            WorkerExecutorResourceCompilationService(
                parameters.projectPath,
                parameters.taskOwner.get(),
                workerExecutor,
                parameters.analyticsService,
                parameters.aapt2.get(),
                await = true
            ).use { compilationService ->
                // Add a default values folder/strings file
                val defaultValuesFolder = FileUtils.join(
                    parameters.tempProjectDir.get().asFile,
                    "res",
                    "values"
                )
                aapt2CompileFile(defaultValuesFolder, compilationService)

                // Add strings for each folder locale in the project
                folderLocales.forEach { folderLocale ->
                    val valuesFolder = FileUtils.join(
                        parameters.tempProjectDir.get().asFile,
                        "res",
                        "values-$folderLocale"
                    )
                    aapt2CompileFile(valuesFolder, compilationService)
                }
            }
        }

        private fun aapt2CompileFile(
            valuesFolder: File,
            compilationService: WorkerExecutorResourceCompilationService
        ) {
            val tempStringFileContent =
                """
                    <resources>
                        <string name="placeholder_name">Placeholder</string>
                    </resources>
                """.trimIndent()
            valuesFolder.mkdirs()
            val tempStringFile = File(valuesFolder, "strings.xml")
            tempStringFile.createNewFile()
            tempStringFile.writeText(tempStringFileContent)
            val request = CompileResourceRequest(
                tempStringFile,
                parameters.compiledResOutput.get().asFile
            )
            compilationService.submitCompile(request)
        }

        // Runs AAPT2 link with a temp manifest which generates the res APK within this task
        private fun runAapt2Link() {
            val manifest = File(parameters.tempProjectDir.get().asFile, "AndroidManifest.xml")
            manifest.parentFile.mkdirs()
            manifest.createNewFile()
            manifest.writeText(
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        xmlns:dist="http://schemas.android.com/apk/distribution"
                        package="com.example.app">
                    </manifest>
                """.trimIndent()
            )

            val resApk = parameters.resApkDir.get().file("res.apk").asFile
            val aaptPackageConfig = AaptPackageConfig.Builder()
                .setManifestFile(manifest)
                .setOptions(AaptOptions())
                .setComponentType(ComponentTypeImpl.BASE_APK)
                .setAndroidJarPath(parameters.androidJarInput.get().getAndroidJar().get().absolutePath)
                .setResourceConfigs(parameters.resConfigs.get().toImmutableSet())
                .setResourceOutputApk(resApk)
                .addResourceDir(parameters.compiledResOutput.get().asFile)
                .build()

            val logger = Logging.getLogger(GenerateLocaleConfigTask::class.java)
            parameters.aapt2.get().getLeasingAapt2().link(aaptPackageConfig, LoggerWrapper(logger))
        }
    }

    class CreationAction(
        creationConfig: ComponentCreationConfig
    ) :
        VariantTaskCreationAction<GenerateLocaleConfigTask, ComponentCreationConfig>(
            creationConfig
        ) {

        override val name: String
            get() = computeTaskName("generate", "LocaleConfig")
        override val type: Class<GenerateLocaleConfigTask>
            get() = GenerateLocaleConfigTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<GenerateLocaleConfigTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                GenerateLocaleConfigTask::localeConfig
            ).atLocation(
                creationConfig.paths.getGeneratedResourcesDir("localeConfig")
                    .get().asFile.absolutePath
            ).on(InternalArtifactType.GENERATED_LOCALE_CONFIG)
        }

        override fun configure(
            task: GenerateLocaleConfigTask
        ) {
            super.configure(task)

            task.dependencyLocales.fromDisallowChanges(
                creationConfig.variantDependencies.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    AndroidArtifacts.ArtifactType.SUPPORTED_LOCALE_LIST
                )
            )
            task.appLocales.setDisallowChanges(
                creationConfig.artifacts.get(InternalArtifactType.SUPPORTED_LOCALE_LIST))

            creationConfig.services.initializeAapt2Input(task.aapt2)
            task.androidJarInput.initialize(creationConfig)

            val resConfigs = creationConfig.androidResourcesCreationConfig?.resourceConfigurations
            if (!resConfigs.isNullOrEmpty()) {
                val nonDensityResConfigs = AaptUtils.getNonDensityResConfigs(resConfigs).toSet()
                if (nonDensityResConfigs.isNotEmpty()) {
                    task.resConfigs.setDisallowChanges(nonDensityResConfigs)
                    task.compiledResOutput.set(
                        creationConfig.paths.getIncrementalDir("${task.name}_compiledResOutput"))
                    task.resApkDir.set(
                        creationConfig.paths.getIncrementalDir("${task.name}_resApkDir"))
                    task.tempProjectDir.set(
                        creationConfig.paths.getIncrementalDir("${task.name}_tempProject"))
                }
            }
        }
    }
}
