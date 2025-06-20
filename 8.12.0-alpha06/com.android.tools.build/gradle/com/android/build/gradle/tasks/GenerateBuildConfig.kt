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
package com.android.build.gradle.tasks

import com.android.build.api.variant.BuildConfigField
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.generators.BuildConfigByteCodeGenerator
import com.android.build.gradle.internal.generators.BuildConfigData
import com.android.build.gradle.internal.generators.BuildConfigGenerator
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.BuildConfigTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.BuildConfigTaskCreationActionImpl
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.compiling.GeneratedCodeFileCreator
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import com.google.common.collect.Lists
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.Serializable

@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.COMPILED_CLASSES, secondaryTaskCategories = [TaskCategory.SOURCE_GENERATION])
abstract class GenerateBuildConfig : NonIncrementalTask() {

    // ----- PUBLIC TASK API -----

    @get:OutputDirectory
    @get:Optional
    abstract val sourceOutputDir: DirectoryProperty

    @get:OutputFile
    @get:Optional
    abstract val bytecodeOutputFile: RegularFileProperty

    // ----- PRIVATE TASK API -----

    @get:Input
    @get:Optional
    abstract val buildTypeName: Property<String>

    @get:Input
    var isLibrary: Boolean = false
        private set

    @get:Input
    abstract val namespace: Property<String>

    @get:Input
    @get:Optional
    abstract val applicationId: Property<String>

    @get:Input
    abstract val debuggable: Property<Boolean>

    @get:Input
    abstract val flavorName: Property<String>

    @get:Input
    abstract val flavorNamesWithDimensionNames: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val versionName: Property<String?>

    @get:Input
    @get:Optional
    abstract val versionCode: Property<Int?>

    // just to know whether to set versionCode/Name in the build config field.
    // For apps we want to put it whether the info is there or not because it might be set
    // in release but not in debug but you need the code to compile in both variants.
    // And we cannot rely on Provider.isPresent as it does not disambiguate between missing value
    // and null value.
    @get:Internal
    abstract val hasVersionInfo: Property<Boolean>

    @get:Input
    abstract val items: MapProperty<String, BuildConfigField<out Serializable>>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedManifests: DirectoryProperty

    override fun doTaskAction() {
        val itemsToGenerate = items.get()

        val buildConfigData = BuildConfigData.Builder()
                .setNamespace(namespace.get())
                .apply {
                    if (sourceOutputDir.isPresent) {
                        addBooleanField("DEBUG", debuggable.get())
                    }

                    if (isLibrary) {
                        addStringField("LIBRARY_PACKAGE_NAME", namespace.get())
                    } else {
                        addStringField("APPLICATION_ID", applicationId.get())
                    }
                    buildTypeName.orNull?.let {
                        addStringField("BUILD_TYPE", it)
                    }
                    flavorName.get().let {
                        if (it.isNotEmpty()) {
                            addStringField("FLAVOR", it)
                        }
                    }
                    val flavors = flavorNamesWithDimensionNames.get()
                    val count = flavors.size
                    if (count > 1) {
                        var i = 0
                        while (i < count) {
                            val sanitizedDimensionName = sanitizeFlavorDimension(flavors[i + 1])
                            addStringField(
                                    "FLAVOR_$sanitizedDimensionName",
                                    "${flavors[i]}",
                                    if (sanitizedDimensionName == flavors[i + 1]) {
                                        null
                                    } else {
                                        "From flavor dimension ${flavors[i + 1]}"
                                    }
                            )
                            i += 2
                        }
                    }
                    if (hasVersionInfo.get()) {
                        versionCode.orNull?.let {
                            addIntField("VERSION_CODE", it)
                            addStringField("VERSION_NAME", "${versionName.getOrElse("")}")
                        }
                    }
                }

        val generator: GeneratedCodeFileCreator =
                if (bytecodeOutputFile.isPresent) {
                    FileUtils.deleteIfExists(bytecodeOutputFile.get().asFile)
                    val byteCodeBuildConfigData = buildConfigData
                            .setOutputPath(bytecodeOutputFile.get().asFile.parentFile.toPath())
                            .addBooleanField("DEBUG", debuggable.get())
                            .build()
                    BuildConfigByteCodeGenerator(byteCodeBuildConfigData)
                } else {
                    // must clear the folder in case the namespace changed, otherwise,
                    // there'll be two classes.
                    val destinationDir = sourceOutputDir.get().asFile
                    FileUtils.cleanOutputDir(destinationDir)
                    val sourceCodeBuildConfigData = buildConfigData
                            .setOutputPath(sourceOutputDir.get().asFile.toPath())
                            .apply {
                                // user generated items, order them by field name so generation
                                // is stable.
                                itemsToGenerate.toSortedMap().forEach { (name, buildConfigField)
                                    ->
                                    addItem(name, buildConfigField)
                                }
                            }
                            .build()
                    BuildConfigGenerator(sourceCodeBuildConfigData)
                }

        generator.generate()
    }

    private fun sanitizeFlavorDimension(name: String): String {
        Preconditions.checkArgument(name.isNotEmpty())
        // Replace invalid characters
        return name.replace("[^a-zA-Z0-9_\$]".toRegex(), "_")
    }

    // ----- Config Action -----

    internal class CreationAction(creationConfig: ConsumableCreationConfig) :
        VariantTaskCreationAction<GenerateBuildConfig, ConsumableCreationConfig>(
            creationConfig
        ), BuildConfigTaskCreationAction by BuildConfigTaskCreationActionImpl(
            creationConfig
        ) {

        override val name: String = computeTaskName("generate", "BuildConfig")

        override val type: Class<GenerateBuildConfig> = GenerateBuildConfig::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<GenerateBuildConfig>
        ) {
            super.handleProvider(taskProvider)
            val outputBytecode = creationConfig.services.projectOptions
                    .get(BooleanOption.ENABLE_BUILD_CONFIG_AS_BYTECODE)
            // TODO(b/224758957): This is wrong we need to check the final build config fields from
            //  the variant API
            val generateItems = buildConfigCreationConfig.dslBuildConfigFields.any()
            creationConfig.taskContainer.generateBuildConfigTask = taskProvider
            if (outputBytecode && !generateItems) {
                creationConfig.artifacts.setInitialProvider(
                                taskProvider,
                                GenerateBuildConfig::bytecodeOutputFile
                        ).withName("BuildConfig.jar")
                        .on(InternalArtifactType.COMPILE_BUILD_CONFIG_JAR)
            } else {
                creationConfig.artifacts.setInitialProvider(
                                taskProvider,
                                GenerateBuildConfig::sourceOutputDir
                        ).atLocation(creationConfig.paths.buildConfigSourceOutputDir)
                        .on(InternalArtifactType.GENERATED_BUILD_CONFIG_JAVA)
            }
        }

        override fun configure(
            task: GenerateBuildConfig
        ) {
            super.configure(task)

            val services = creationConfig.services
            task.namespace.setDisallowChanges(creationConfig.namespace)

            if (creationConfig is ApkCreationConfig) {
                task.applicationId.setDisallowChanges(creationConfig.applicationId)
            }

            if (creationConfig is ApplicationCreationConfig) {
                val mainSplit = creationConfig.outputs.getMainSplit()
                // check the variant API property first (if there is one) in case the variant
                // output version has been overridden, otherwise use the variant configuration
                task.versionCode.setDisallowChanges(mainSplit.versionCode)
                task.versionName.setDisallowChanges(mainSplit.versionName)
                task.hasVersionInfo.setDisallowChanges(true)
            } else {
                task.hasVersionInfo.setDisallowChanges(false)
            }

            task.debuggable.setDisallowChanges(creationConfig.debuggable)

            task.buildTypeName.setDisallowChanges(creationConfig.buildType)

            // no need to memoize, variant configuration does that already.
            task.flavorName.setDisallowChanges(
                services.provider { creationConfig.flavorName ?: "" })

            task.flavorNamesWithDimensionNames.setDisallowChanges(services.provider {
                creationConfig.getFlavorNamesWithDimensionNames()
            })

            task.items.set(buildConfigCreationConfig.buildConfigFields)

            if (creationConfig.componentType.isTestComponent) {
                creationConfig.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.PACKAGED_MANIFESTS, task.mergedManifests
                )
            }
            task.isLibrary = creationConfig.componentType.isAar
        }
    }
}

/**
 * Return the names of the applied flavors.
 *
 *
 * The list contains the dimension names as well.
 *
 * @return the list, possibly empty if there are no flavors.
 */
private fun ComponentCreationConfig.getFlavorNamesWithDimensionNames(): List<String> {
    if (productFlavorList.isEmpty()) {
        return emptyList()
    }
    val names: List<String>
    val count = productFlavorList.size
    if (count > 1) {
        names =
            Lists.newArrayListWithCapacity(count * 2)
        for (i in 0 until count) {
            names.add(productFlavorList[i].name)
            names.add(productFlavorList[i].dimension)
        }
    } else {
        names = listOf(productFlavorList[0].name)
    }
    return names
}
