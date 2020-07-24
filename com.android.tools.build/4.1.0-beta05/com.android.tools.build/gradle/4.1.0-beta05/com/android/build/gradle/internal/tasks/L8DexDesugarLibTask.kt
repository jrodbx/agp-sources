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

import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.AndroidJarInput
import com.android.build.gradle.internal.dependency.getDexingArtifactConfiguration
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.getDesugarLibConfig
import com.android.build.gradle.internal.utils.getDesugarLibJarFromMaven
import com.android.builder.dexing.KeepRulesConfig
import com.android.builder.dexing.runL8
import com.android.builder.model.CodeShrinker
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.Serializable
import java.nio.file.Path
import javax.inject.Inject

/**
 * A task using L8 tool to dex and shrink (if needed) desugar library with keep rules.
 */
@CacheableTask
abstract class L8DexDesugarLibTask : NonIncrementalTask() {

    @get:Input
    abstract val libConfiguration: Property<String>

    @get:Input
    abstract val minSdkVersion: Property<Int>

    @get:Classpath
    abstract val desugarLibJar: ConfigurableFileCollection

    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val keepRulesFiles: ConfigurableFileCollection

    @get:Input
    abstract val keepRulesConfigurations: ListProperty<String>

    @get:Input
    abstract val debuggable: Property<Boolean>

    @get:Nested
    abstract val androidJarInput: AndroidJarInput

    @get:OutputDirectory
    abstract val desugarLibDex: DirectoryProperty

    override fun doTaskAction() {
        getWorkerFacadeWithWorkers().use {
            it.submit(
                L8DexRunnable::class.java,
                L8DexParams(
                    desugarLibJar.files,
                    desugarLibDex.get().asFile,
                    libConfiguration.get(),
                    androidJarInput.getAndroidJar().get(),
                    minSdkVersion.get(),
                    keepRulesFiles.files,
                    keepRulesConfigurations.orNull,
                    debuggable.get()
                )
            )
        }
    }

    class CreationAction(
        componentProperties: ComponentPropertiesImpl,
        private val enableDexingArtifactTransform: Boolean,
        private val separateFileDependenciesDexingTask: Boolean
    ) : VariantTaskCreationAction<L8DexDesugarLibTask, ComponentPropertiesImpl>(
        componentProperties
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
        }

        override fun configure(
            task: L8DexDesugarLibTask
        ) {
            super.configure(task)
            task.libConfiguration.set(getDesugarLibConfig(creationConfig.globalScope.project))
            task.desugarLibJar.from(getDesugarLibJarFromMaven(creationConfig.globalScope.project))
            task.androidJarInput.sdkBuildService.set(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
            task.minSdkVersion.set(creationConfig.variantDslInfo.minSdkVersionWithTargetDeviceApi.featureLevel)

            setKeepRules(task)

            task.debuggable.set(creationConfig.variantDslInfo.isDebuggable)
        }

        private fun setKeepRules(task: L8DexDesugarLibTask) {
            if (!creationConfig.variantScope.needsShrinkDesugarLibrary) {
                return;
            }

            val attributes = getDexingArtifactConfiguration(creationConfig).getAttributes()
            if (enableDexingArtifactTransform) {
                task.keepRulesFiles.from(
                    creationConfig.variantDependencies.getArtifactCollection(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        AndroidArtifacts.ArtifactType.KEEP_RULES,
                        attributes
                    ).artifactFiles
                )

                val artifactScope = if (separateFileDependenciesDexingTask) {
                    AndroidArtifacts.ArtifactScope.REPOSITORY_MODULE
                } else {
                    AndroidArtifacts.ArtifactScope.EXTERNAL
                }

                task.keepRulesFiles.from(
                    creationConfig.variantDependencies.getArtifactCollection(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                        artifactScope,
                        AndroidArtifacts.ArtifactType.KEEP_RULES,
                        attributes
                    ).artifactFiles
                )
            } else if (creationConfig.variantScope.codeShrinker != CodeShrinker.R8) {
                task.keepRulesFiles.from(
                    creationConfig.artifacts.get(
                        InternalArtifactType.DESUGAR_LIB_SUBPROJECT_KEEP_RULES
                    ),
                    creationConfig.artifacts.get(
                        InternalArtifactType.DESUGAR_LIB_EXTERNAL_LIBS_KEEP_RULES
                    ),
                    creationConfig.artifacts.get(
                        InternalArtifactType.DESUGAR_LIB_EXTERNAL_LIBS_ARTIFACT_TRANSFORM_KEEP_RULES
                    ),
                    creationConfig.artifacts.get(
                        InternalArtifactType.DESUGAR_LIB_MIXED_SCOPE_KEEP_RULES
                    )
                )
            }

            task.keepRulesFiles.from(
                creationConfig.artifacts.get(
                    InternalArtifactType.DESUGAR_LIB_PROJECT_KEEP_RULES
                )
            )

            if (separateFileDependenciesDexingTask) {
                task.keepRulesFiles.from(
                    creationConfig.artifacts.get(
                        InternalArtifactType.DESUGAR_LIB_EXTERNAL_FILE_LIB_KEEP_RULES))
            }

            val nonMinified = creationConfig.variantScope.java8LangSupportType == VariantScope.Java8LangSupport.D8
            if (creationConfig.globalScope.hasDynamicFeatures() && nonMinified) {
                task.keepRulesFiles.from(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.DESUGAR_LIB_PROJECT_KEEP_RULES)
                )
                task.keepRulesFiles.from(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        AndroidArtifacts.ArtifactType.DESUGAR_LIB_SUBPROJECT_KEEP_RULES)
                )
                task.keepRulesFiles.from(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.DESUGAR_LIB_MIXED_SCOPE_KEEP_RULES)
                )
                task.keepRulesFiles.from(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                        AndroidArtifacts.ArtifactScope.REPOSITORY_MODULE,
                        AndroidArtifacts.ArtifactType.DESUGAR_LIB_EXTERNAL_LIBS_KEEP_RULES)
                )
                task.keepRulesFiles.from(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                        AndroidArtifacts.ArtifactScope.FILE,
                        AndroidArtifacts.ArtifactType.DESUGAR_LIB_EXTERNAL_FILE_KEEP_RULES)
                )
                task.keepRulesFiles.from(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                        AndroidArtifacts.ArtifactScope.FILE,
                        AndroidArtifacts.ArtifactType.DESUGAR_LIB_EXTERNAL_LIBS_ARTIFACT_TRANSFORM_KEEP_RULES)
                )
            }
            // make sure non-minified release build is not obfuscated
            if (nonMinified) {
                task.keepRulesConfigurations.set(listOf("-dontobfuscate"))
            }
        }
    }
}

@VisibleForTesting
class L8DexParams(
    val desugarLibJar: Collection<File>,
    val desugarLibDex: File,
    val libConfiguration: String,
    val androidJar: File,
    val minSdkVersion: Int,
    val keepRulesFiles: Set<File>,
    val keepRulesConfigurations: List<String>?,
    val debuggable: Boolean
) : Serializable

@VisibleForTesting
class L8DexRunnable @Inject constructor(val params: L8DexParams) : Runnable {
    override fun run() {
        params.desugarLibDex.mkdir()
        val keepRulesConfig =
            KeepRulesConfig(getAllFilesUnderDirectories(params.keepRulesFiles), params.keepRulesConfigurations ?: emptyList())
        runL8(
            params.desugarLibJar.map { it.toPath() },
            params.desugarLibDex.toPath(),
            params.libConfiguration,
            listOf(params.androidJar.toPath()),
            params.minSdkVersion,
            keepRulesConfig,
            params.debuggable
        )
    }

    private fun getAllFilesUnderDirectories(dirs: Set<File>) : List<Path> {
        val files = mutableListOf<File>()
        dirs.forEach { dir ->
            dir.walk().filter {
                it.isFile
            }.forEach {
                files.add(it)
            }
        }
        return files.map { it.toPath() }
    }
}