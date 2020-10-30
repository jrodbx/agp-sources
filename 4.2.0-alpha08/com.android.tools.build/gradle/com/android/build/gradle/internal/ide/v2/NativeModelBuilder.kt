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

package com.android.build.gradle.internal.ide.v2

import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.cxx.gradle.generator.CxxMetadataGenerator
import com.android.build.gradle.internal.cxx.gradle.generator.createCxxMetadataGenerator
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment
import com.android.build.gradle.internal.cxx.model.buildFileIndexFile
import com.android.build.gradle.internal.cxx.model.compileCommandsJsonBinFile
import com.android.build.gradle.internal.cxx.model.symbolFolderIndexFile
import com.android.build.gradle.internal.errors.SyncIssueReporter
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.variant.VariantModel
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.v2.models.ndk.NativeBuildSystem
import com.android.builder.model.v2.models.ndk.NativeModelBuilderParameter
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.builder.model.v2.models.ndk.NativeVariant
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class NativeModelBuilder(
    private val issueReporter: SyncIssueReporter,
    private val globalScope: GlobalScope,
    private val variantModel: VariantModel
) : ParameterizedToolingModelBuilder<NativeModelBuilderParameter> {
    private val ops = object : ExecOperations {
        override fun exec(action: Action<in ExecSpec>) =
            globalScope.project.exec(action)

        override fun javaexec(action: Action<in JavaExecSpec>) =
            globalScope.project.javaexec(action)
    }
    private val projectOptions get() = globalScope.projectOptions
    private val ideRefreshExternalNativeModel
        get() =
            projectOptions.get(BooleanOption.IDE_REFRESH_EXTERNAL_NATIVE_MODEL)
    private val enableParallelNativeJsonGen
        get() =
            projectOptions.get(BooleanOption.ENABLE_PARALLEL_NATIVE_JSON_GEN)
    private val scopes: List<ComponentPropertiesImpl> by lazy {
        (variantModel.variants + variantModel.testComponents)
            .filter { it.taskContainer.cxxConfigurationModel != null }
    }
    private val generators: List<CxxMetadataGenerator> by lazy {
        IssueReporterLoggingEnvironment(issueReporter).use {
            scopes.map { scope ->
                createCxxMetadataGenerator(
                    getBuildService<SdkComponentsBuildService>(globalScope.project.gradle.sharedServices).get(),
                    scope.taskContainer.cxxConfigurationModel!!,
                    getBuildService<AnalyticsService>(globalScope.project.gradle.sharedServices).get()
                )
            }
        }
    }

    override fun getParameterType(): Class<NativeModelBuilderParameter> =
        NativeModelBuilderParameter::class.java

    override fun canBuild(modelName: String): Boolean = modelName == NativeModule::class.java.name

    override fun buildAll(modelName: String, project: Project): NativeModule? =
        buildAll(modelName, null, project)

    override fun buildAll(
        unusedModelName: String,
        params: NativeModelBuilderParameter?,
        project: Project
    ): NativeModule? = IssueReporterLoggingEnvironment(issueReporter).use {
        if (generators.isEmpty()) return@use null
        val cxxModuleModel = generators.first().variant.module
        val buildSystem = when (cxxModuleModel.buildSystem) {
            com.android.build.gradle.tasks.NativeBuildSystem.CMAKE -> NativeBuildSystem.CMAKE
            com.android.build.gradle.tasks.NativeBuildSystem.NDK_BUILD -> NativeBuildSystem.NDK_BUILD
        }
        val variants: List<NativeVariant> = generators.map { generator ->
            NativeVariantImpl(generator.variant.variantName, generator.abis.map { cxxAbiModel ->
                NativeAbiImpl(
                    cxxAbiModel.abi.tag,
                    cxxAbiModel.compileCommandsJsonBinFile.canonicalFile,
                    cxxAbiModel.symbolFolderIndexFile.canonicalFile,
                    cxxAbiModel.buildFileIndexFile.canonicalFile
                )
            })
        }
        NativeModuleImpl(
            project.name,
            variants,
            buildSystem,
            cxxModuleModel.ndkVersion.toString(),
            cxxModuleModel.makeFile.canonicalFile
        ).also {
            // Generate build files and compile_commands.json on request.
            generateBuildFilesAndCompileCommandsJson(params.asPredicate())
        }
    }

    private fun NativeModelBuilderParameter?.asPredicate() = { variant: String, abi: String ->
        this?.variantsToGenerateBuildInformation?.contains(variant) ?: true &&
                this?.abisToGenerateBuildInformation?.contains(abi) ?: true
    }

    private fun generateBuildFilesAndCompileCommandsJson(filter: (variant: String, abi: String) -> Boolean) {
        val buildSteps = generators.flatMap { generator ->
            generator.abis.flatMap { cxxAbiModel ->
                if (filter(generator.variant.variantName, cxxAbiModel.abi.tag)) {
                    generator.getMetadataGenerators(
                        ops,
                        ideRefreshExternalNativeModel,
                        cxxAbiModel.abi.tag
                    )
                } else {
                    emptyList()
                }
            }
        }
        if (enableParallelNativeJsonGen) {
            val cpuCores = Runtime.getRuntime().availableProcessors()
            val threadNumber = cpuCores.coerceAtMost(8)
            val nativeJsonGenExecutor = Executors.newFixedThreadPool(threadNumber)

            try {
                // Need to get each result even if we're not using the output because that's how we
                // propagate exceptions.
                nativeJsonGenExecutor.invokeAll(
                    buildSteps.map { step ->
                        Callable {
                            IssueReporterLoggingEnvironment(issueReporter).use {
                                step.call()
                            }
                        }
                    }
                ).map { it.get() }
            } catch (e: InterruptedException) {
                throw RuntimeException(
                    "Thread was interrupted while native build JSON generation was in progress.",
                    e
                )
            }
        } else {
            buildSteps.forEach { it.call() }
        }
    }
}
