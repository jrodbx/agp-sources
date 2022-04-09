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

import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.cxx.configure.NativeModelBuilderOutcome
import com.android.build.gradle.internal.cxx.configure.NativeModelBuilderOutcome.Outcome.FAILED_DURING_GENERATE
import com.android.build.gradle.internal.cxx.configure.NativeModelBuilderOutcome.Outcome.NO_CONFIGURATION_MODELS
import com.android.build.gradle.internal.cxx.configure.NativeModelBuilderOutcome.Outcome.SUCCESS
import com.android.build.gradle.internal.cxx.configure.encode
import com.android.build.gradle.internal.cxx.gradle.generator.CxxMetadataGenerator
import com.android.build.gradle.internal.cxx.gradle.generator.createCxxMetadataGenerator
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.logStructured
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.additionalProjectFilesIndexFile
import com.android.build.gradle.internal.cxx.model.buildFileIndexFile
import com.android.build.gradle.internal.cxx.model.compileCommandsJsonBinFile
import com.android.build.gradle.internal.cxx.model.symbolFolderIndexFile
import com.android.build.gradle.internal.errors.SyncIssueReporter
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.variant.VariantModel
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.builder.model.v2.models.ndk.NativeBuildSystem.CMAKE
import com.android.builder.model.v2.models.ndk.NativeBuildSystem.NDK_BUILD
import com.android.builder.model.v2.models.ndk.NativeBuildSystem.NINJA
import com.android.builder.model.v2.models.ndk.NativeModelBuilderParameter
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.builder.model.v2.models.ndk.NativeVariant
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
import java.lang.RuntimeException

class NativeModelBuilder(
    private val project: Project,
    private val issueReporter: SyncIssueReporter,
    private val projectOptions: ProjectOptions,
    private val variantModel: VariantModel,
) : ParameterizedToolingModelBuilder<NativeModelBuilderParameter> {
    private val ops = object : ExecOperations {
        override fun exec(action: Action<in ExecSpec>) =
            project.exec(action)

        override fun javaexec(action: Action<in JavaExecSpec>) =
                project.javaexec(action)
    }
    private val ideRefreshExternalNativeModel
        get() =
            projectOptions.get(BooleanOption.IDE_REFRESH_EXTERNAL_NATIVE_MODEL)
    private val scopes: List<ComponentCreationConfig> by lazy {
        (variantModel.variants + variantModel.testComponents)
            .filter { it.taskContainer.cxxConfigurationModel != null }
    }
    private val configurationModels by lazy {
        scopes.map { scope -> scope.name to scope.taskContainer.cxxConfigurationModel!! }.distinct()
    }
    private val generators =
            mutableMapOf<CxxAbiModel, CxxMetadataGenerator>()

    fun createGenerator(abi: CxxAbiModel) : CxxMetadataGenerator {
        return generators.computeIfAbsent(abi) { model ->
            val analyticsService =
                getBuildService(project.gradle.sharedServices, AnalyticsService::class.java).get()
            IssueReporterLoggingEnvironment(issueReporter, analyticsService, abi.variant).use {
                createCxxMetadataGenerator(
                    abi,
                    analyticsService
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
    ): NativeModule? {
        // Nested IssueReporterLoggingEnvironment is due to the fact that we always want to
        // capture the structured log outcome. If there are no configuration models then we
        // log NO_CONFIGURATION_MODELS outcome but we need an IssueReporterLoggingEnvironment
        // on the stack so that logStructured { } will not be NOP.
        IssueReporterLoggingEnvironment(
            issueReporter,
            project.rootProject.rootDir,
            null).use {
            val outcome = NativeModelBuilderOutcome.newBuilder().setGradlePath(project.path)
            try {
                val analyticsService = getBuildService(
                    project.gradle.sharedServices, AnalyticsService::class.java
                ).get()
                if (configurationModels.isEmpty()) {
                    outcome.outcome = NO_CONFIGURATION_MODELS
                    return null
                }
                val configurationModel = configurationModels.first().second
                IssueReporterLoggingEnvironment(
                    issueReporter,
                    analyticsService,
                    configurationModel.variant
                ).use {
                    params?.abisToGenerateBuildInformation?.let {
                        outcome.addAllRequestedAbis(it)
                    }
                    params?.variantsToGenerateBuildInformation?.let {
                        outcome.addAllRequestedVariants(it)
                    }
                    val cxxModuleModel = configurationModel.variant.module

                    val buildSystem = when (cxxModuleModel.buildSystem) {
                        NativeBuildSystem.NINJA -> NINJA
                        NativeBuildSystem.CMAKE -> CMAKE
                        NativeBuildSystem.NDK_BUILD -> NDK_BUILD
                        else -> error("${cxxModuleModel.buildSystem}")
                    }

                    val variants: List<NativeVariant> = configurationModels
                        .flatMap { (variantName, model) -> model.activeAbis.map { variantName to it } }
                        .groupBy { (variantName, abi) -> variantName }
                        .map { (variantName, abis) ->
                            NativeVariantImpl(variantName, abis.map { (_, abi) ->
                                NativeAbiImpl(
                                    abi.abi.tag,
                                    abi.compileCommandsJsonBinFile.canonicalFile,
                                    abi.symbolFolderIndexFile.canonicalFile,
                                    abi.buildFileIndexFile.canonicalFile,
                                    abi.additionalProjectFilesIndexFile
                                )
                            })
                        }

                    return NativeModuleImpl(
                        project.name,
                        variants,
                        buildSystem,
                        cxxModuleModel.ndkVersion.toString(),
                        cxxModuleModel.makeFile.canonicalFile
                    ).also {
                        // Generate build files and compile_commands.json on request.
                        generateBuildFilesAndCompileCommandsJson(outcome, params.asPredicate())
                    }
                }
            } finally {
                logStructured { encoder ->
                    outcome.build().encode(encoder)
                }
            }
        }
    }

    private fun NativeModelBuilderParameter?.asPredicate() = { variant: String, abi: String ->
        this?.variantsToGenerateBuildInformation?.contains(variant) ?: true &&
                this?.abisToGenerateBuildInformation?.contains(abi) ?: true
    }

    /**
     * Configure C/C++ projects that match [filter].
     * The first configure exception, if there is one, is thrown after attempting to configure
     * all C/C++ configurations.
     */
    private fun generateBuildFilesAndCompileCommandsJson(
        outcome: NativeModelBuilderOutcome.Builder,
        filter: (variant: String, abi: String) -> Boolean) {

        var firstException : Throwable? = null
        outcome.outcome = SUCCESS
        configurationModels
                .flatMap { (variantName, model) -> model.activeAbis.map { abi -> variantName to abi } }
                .onEach { (variantName, abi) ->
                    outcome.addAvailableVariantAbis("$variantName:${abi.abi.tag}")
                }
                .filter { (variantName, abi) -> filter(variantName, abi.abi.tag) }
                .forEach { (variantName, abi) ->
                    try {
                        createGenerator(abi).configure(ops, ideRefreshExternalNativeModel)
                        outcome.addSuccessfullyConfiguredVariantAbis("$variantName:${abi.abi.tag}")
                    } catch (e :Throwable) {
                        firstException = firstException ?: e
                        val variantAbi = "$variantName:${abi.abi.tag}"
                        val message = "${outcome.gradlePath} $variantAbi failed to configure C/C++\n${e.message}\n" +
                                "${e.stackTraceToString()}\n"
                        outcome.outcome = FAILED_DURING_GENERATE
                        outcome.addFailedConfigureVariantAbis(variantAbi)
                        outcome.addFailedConfigureMessages(message)
                    }
                }
        if (firstException != null) {
            throw RuntimeException(outcome.failedConfigureMessagesList.first(), firstException!!)
        }
    }
}
