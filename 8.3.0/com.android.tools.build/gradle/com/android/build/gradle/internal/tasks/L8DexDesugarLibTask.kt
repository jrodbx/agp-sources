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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.DexingTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.DexingTaskCreationActionImpl
import com.android.build.gradle.internal.utils.getDesugarLibConfig
import com.android.build.gradle.internal.utils.getDesugarLibJarFromMaven
import com.android.build.gradle.internal.utils.getDesugaredDesugarLib
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.dexing.KeepRulesConfig
import com.android.builder.dexing.runL8
import com.android.builder.dexing.runTraceReferenceTool
import com.android.tools.r8.OutputMode
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.nio.file.Path

/**
 * A task using L8 to dex and shrink(if needed) desugar library with keep rules computed with trace
 * reference tool.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.DEXING)
abstract class L8DexDesugarLibTask : NonIncrementalTask() {

    @get:Input
    abstract val libConfiguration: Property<String>

    @get:Input
    abstract val minSdkVersion: Property<Int>

    @get:Classpath
    abstract val desugarLibJar: ConfigurableFileCollection

    @get:Classpath
    @get:Optional
    abstract val fullBootClasspath: ConfigurableFileCollection

    @get:Input
    abstract val debuggable: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val keepRulesConfigurations: ListProperty<String>

    /**
     * The pre-processed(desugared) desugar lib jar in classfile format which is used by
     * trace reference tool to generate keep rules for shrinking desugar lib jar into dex format.
     */
    @get:Classpath
    @get:Optional
    abstract val desugaredDesugarLibJar: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val dexFiles: ConfigurableFileCollection

    @get: [InputFiles Optional PathSensitive(PathSensitivity.NAME_ONLY)]
    abstract val inputArtProfile: RegularFileProperty

    @get:OutputDirectory
    abstract val desugarLibDex: DirectoryProperty

    @get:OutputFile
    @get:Optional
    abstract val keepRules: RegularFileProperty

    @get:OutputFile
    @get:Optional
    abstract val outputArtProfile: RegularFileProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(
            L8DexWorkAction::class.java
        ) {
            it.initializeFromAndroidVariantTask(this)
            it.desugarLibJar.from(desugarLibJar)
            it.desugarLibDex.set(desugarLibDex)
            it.libConfiguration.set(libConfiguration)
            it.fullBootClasspath.from(fullBootClasspath)
            it.minSdkVersion.set(minSdkVersion)
            it.keepRulesConfigurations.set(keepRulesConfigurations)
            it.debuggable.set(debuggable)
            it.desugaredDesugarLib.from(desugaredDesugarLibJar)
            it.dexFiles.from(dexFiles)
            it.outputKeepRules.set(keepRules)
            it.inputArtProfile.set(inputArtProfile)
            it.outputArtProfile.set(outputArtProfile)
        }
    }

    class CreationAction(
        creationConfig: ApkCreationConfig,
    ) : VariantTaskCreationAction<L8DexDesugarLibTask, ApkCreationConfig>(
        creationConfig
    ), DexingTaskCreationAction by DexingTaskCreationActionImpl(
        creationConfig
    ) {
        override val name = computeTaskName("l8DexDesugarLib")
        override val type = L8DexDesugarLibTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<L8DexDesugarLibTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts
                .setInitialProvider(taskProvider, L8DexDesugarLibTask::desugarLibDex)
                .on(InternalArtifactType.DESUGAR_LIB_DEX)
            creationConfig.artifacts.use(taskProvider).wiredWithFiles(
                L8DexDesugarLibTask::inputArtProfile,
                L8DexDesugarLibTask::outputArtProfile
            ).toTransform(InternalArtifactType.L8_ART_PROFILE)
            if (dexingCreationConfig.needsShrinkDesugarLibrary) {
                creationConfig.artifacts
                    .setInitialProvider(taskProvider, L8DexDesugarLibTask::keepRules)
                    .withName("keep_rules.txt")
                    .on(InternalArtifactType.DESUGAR_LIB_KEEP_RULES)
            }
        }

        override fun configure(
            task: L8DexDesugarLibTask
        ) {
            super.configure(task)
            task.libConfiguration.set(getDesugarLibConfig(creationConfig.services))
            task.desugarLibJar.from(getDesugarLibJarFromMaven(creationConfig.services))
            task.minSdkVersion.set(dexingCreationConfig.minSdkVersionForDexing)
            task.debuggable.set(creationConfig.debuggable)
            task.fullBootClasspath.from(creationConfig.global.fullBootClasspath)

            if (dexingCreationConfig.needsShrinkDesugarLibrary) {
                task.desugaredDesugarLibJar.from(getDesugaredDesugarLib(creationConfig))
                // when app is using D8, desugar library is shrunk to reduce apk but not obfuscated
                // or optimized
                if (!creationConfig.optimizationCreationConfig.minifiedEnabled) {
                    task.keepRulesConfigurations.set(listOf("-dontobfuscate", "-dontoptimize"))
                }

                if (creationConfig is ApplicationCreationConfig && creationConfig.consumesFeatureJars) {
                    task.dexFiles.from(creationConfig.artifacts.get(InternalArtifactType.BASE_DEX))
                } else {
                    task.dexFiles.from(creationConfig.artifacts.getAll(InternalMultipleArtifactType.DEX))
                }
                // For feature dex, it is produced by d8/r8 in feature module and published to
                // application(not for androidTest component). The reason why we don't use
                //  consumesFeatureJars is because that API is only for minified build.
                if (!creationConfig.componentType.isForTesting &&
                    creationConfig.global.hasDynamicFeatures) {
                    task.dexFiles.from(
                        creationConfig.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.FEATURE_PUBLISHED_DEX
                        )
                    )
                }
            }
        }
    }
}

abstract class L8DexWorkAction : ProfileAwareWorkAction<L8DexWorkAction.Params>() {
    abstract class Params: Parameters() {
        abstract val desugarLibJar: ConfigurableFileCollection
        abstract val desugarLibDex: DirectoryProperty
        abstract val libConfiguration: Property<String>
        abstract val fullBootClasspath: ConfigurableFileCollection
        abstract val minSdkVersion: Property<Int>
        abstract val keepRulesConfigurations: ListProperty<String>
        abstract val debuggable: Property<Boolean>
        abstract val desugaredDesugarLib: ConfigurableFileCollection
        abstract val dexFiles: ConfigurableFileCollection
        abstract val outputKeepRules: RegularFileProperty
        abstract val inputArtProfile: RegularFileProperty
        abstract val outputArtProfile: RegularFileProperty
    }

    override fun run() {
        val keepRuleFiles = mutableListOf<Path>()
        if (parameters.outputKeepRules.isPresent) {
            val outputKeepRule = parameters.outputKeepRules.get().asFile.toPath()
            keepRuleFiles.add(outputKeepRule)

            runTraceReferenceTool(
                parameters.fullBootClasspath.files.map { it.toPath() },
                parameters.desugaredDesugarLib.asFileTree.files.map { it.toPath() },
                parameters.dexFiles.asFileTree.files.map { it.toPath() },
                outputKeepRule
            )
        }
        val keepRulesConfig = KeepRulesConfig(
            keepRuleFiles,
            parameters.keepRulesConfigurations.orNull ?: emptyList())
        runL8(
            parameters.desugarLibJar.files.map { it.toPath() },
            parameters.desugarLibDex.get().asFile.toPath(),
            parameters.libConfiguration.get(),
            parameters.fullBootClasspath.files.map { it.toPath() },
            parameters.minSdkVersion.get(),
            keepRulesConfig,
            parameters.debuggable.get(),
            OutputMode.DexIndexed,
            parameters.inputArtProfile.orNull?.asFile?.toPath(),
            parameters.outputArtProfile.orNull?.asFile?.toPath()
        )
    }
}
