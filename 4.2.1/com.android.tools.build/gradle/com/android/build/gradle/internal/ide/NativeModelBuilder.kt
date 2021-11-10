/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.ide

import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.cxx.gradle.generator.CxxMetadataGenerator
import com.android.build.gradle.internal.cxx.gradle.generator.NativeAndroidProjectBuilder
import com.android.build.gradle.internal.cxx.gradle.generator.createCxxMetadataGenerator
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.errors.SyncIssueReporter
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.variant.VariantModel
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.ModelBuilderParameter
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeVariantAbi
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Builder for the custom Native Android model.
 */
class NativeModelBuilder(
    private val issueReporter: SyncIssueReporter,
    private val globalScope: GlobalScope,
    private val variantModel: VariantModel
) : ParameterizedToolingModelBuilder<ModelBuilderParameter> {
    private val nativeAndroidProjectClass = NativeAndroidProject::class.java.name
    private val nativeVariantAbiClass = NativeVariantAbi::class.java.name
    private val projectOptions get() = globalScope.projectOptions
    private val ops = object : ExecOperations {
        override fun exec(action: Action<in ExecSpec>) =
            globalScope.project.exec(action)
        override fun javaexec(action: Action<in JavaExecSpec>) =
            globalScope.project.javaexec(action)
    }
    private val ideRefreshExternalNativeModel get() =
        projectOptions.get(BooleanOption.IDE_REFRESH_EXTERNAL_NATIVE_MODEL)
    private val enableParallelNativeJsonGen get() =
        projectOptions.get(BooleanOption.ENABLE_PARALLEL_NATIVE_JSON_GEN)
    private val scopes
        get() = (variantModel.variants + variantModel.testComponents)
        .filter { it.taskContainer.cxxConfigurationModel != null }
    private val generators get() = scopes.map { scope ->
        IssueReporterLoggingEnvironment(issueReporter).use {
            createCxxMetadataGenerator(
                scope.taskContainer.cxxConfigurationModel!!,
                getBuildService<AnalyticsService>(globalScope.project.gradle.sharedServices).get()
            )
        }
    }

    /**
     * Indicates which model classes that buildAll can support.
     */
    override fun canBuild(modelName: String) :Boolean {
        return modelName == nativeAndroidProjectClass || modelName == nativeVariantAbiClass
    }

    /**
     * This is the original buildAll method that works non-incrementally. In our case, it should
     * be called only for NativeAndroidProject and the result will be the full model information.
     */
    override fun buildAll(modelName: String, project: Project): Any? {
        IssueReporterLoggingEnvironment(issueReporter).use {
            return buildFullNativeAndroidProject(project)
        }
    }

    /**
     * This is a more recent version of buildAll that allows a parameter to be passed. This is
     * used for incremental sync (single variant/single ABI). Typically the calling pattern is:
     *
     * (1) buildAll("NativeAndroidProject", {shouldBuildVariant=false}) and we return a
     *     NativeAndroidProject which has only fast-to-compute information like the ABIs for
     *     each variant.
     *
     * (2) buildAll("NativeVariantAbi", {variantName="MyVariant", abiName="x86}) and we return
     *     NativeVariantAbi which has slow-to-compute information about a particular variant + ABI.
     *
     * Step (2) can occur multiple times for different variants and ABIs.
     *
     * We may also receive a request for all information:
     *     buildAll("NativeAndroidProject", {shouldBuildVariant=true}) and we return the full
     *     information about all variants and ABIs.
     */
    override fun buildAll(
        modelName: String,
        parameter: ModelBuilderParameter,
        project: Project
    ): Any? {
        IssueReporterLoggingEnvironment(issueReporter).use {
            // Prevents parameter interface evolution from breaking the model builder.
            val modelBuilderParameter = FailsafeModelBuilderParameter(parameter)
            return when (modelName) {
                nativeAndroidProjectClass ->
                    if (modelBuilderParameter.shouldBuildVariant) buildFullNativeAndroidProject(project)
                    else buildNativeAndroidProjectWithJustVariantInfos(project)
                nativeVariantAbiClass -> buildNativeVariantAbi(
                    project,
                    modelBuilderParameter.variantName!!,
                    modelBuilderParameter.abiName!!
                )
                else -> throw RuntimeException("Unexpected model $modelName")
            }
        }
    }

    override fun getParameterType(): Class<ModelBuilderParameter> {
        return ModelBuilderParameter::class.java
    }

    /**
     * Build a fast-to-compute NativeAndroidProject that mostly just has variants and ABIs.
     */
    private fun buildNativeAndroidProjectWithJustVariantInfos(project: Project): NativeAndroidProject? {
        val builder = NativeAndroidProjectBuilder(project.name)
        generators.onEach { generator ->
            buildInexpensiveNativeAndroidProjectInformation(builder, generator)
        }
        return builder.buildNativeAndroidProject()
    }

    /**
     * Build a fast-to-compute NativeAndroidProject that mostly just has variants and ABIs.
     * Updates a pre-existing NativeAndroidProjectBuilder.
     */
    private fun buildInexpensiveNativeAndroidProjectInformation(
        builder: NativeAndroidProjectBuilder,
        generator: CxxMetadataGenerator
    ) {
        builder.addBuildSystem(generator.variant.module.buildSystem.tag)
        val abis = generator.abis.map { it.abi.tag }
        val buildFolders = generator.abis.map { it.jsonFile.parentFile }
        // We don't have the full set of build files at this point.
        // Add the root one that we do know.
        builder.addBuildFile(generator.variant.module.makeFile)
        builder.addVariantInfo(
            generator.variant.variantName,
            abis,
            (abis zip buildFolders).toMap()
        )
    }

    /**
     * SLOW: Builds a NativeVariantAbi for a specific variant and ABI.
     */
    private fun buildNativeVariantAbi(
        project: Project,
        variantName: String,
        abiName: String
    ): NativeVariantAbi {
        val builder = NativeAndroidProjectBuilder(project.name, abiName)
        generators
            .filter { generator -> generator.variant.variantName == variantName }
            .onEach { generator ->
                generator.getMetadataGenerators(ops, ideRefreshExternalNativeModel, abiName).forEach {
                    it.call()
                }
                buildInexpensiveNativeAndroidProjectInformation(builder, generator)
                generator.addCurrentMetadata(builder)
            }
        return builder.buildNativeVariantAbi(variantName)!!
    }

    /**
     * SLOW: Builds a full NativeAndroidProject with all variants and ABIs computed.
     */
    private fun buildFullNativeAndroidProject(project: Project): NativeAndroidProject? {
        regenerateNativeJson()
        val builder = NativeAndroidProjectBuilder(project.name)
        generators.onEach { generator ->
            buildInexpensiveNativeAndroidProjectInformation(builder, generator)
            generator.addCurrentMetadata(builder)
        }
        return builder.buildNativeAndroidProject()
    }

    /**
     * SLOW: Utility function which triggers generation of all JSON. This function may execute
     * generation on multiple threads but it will block until all threads complete.
     */
    private fun regenerateNativeJson() {
        val buildSteps = generators.map { generator ->
            // This will generate any out-of-date or non-existent JSONs.
            // When refreshExternalNativeModel() is true it will also
            // force update all JSONs.
            generator.getMetadataGenerators(ops, ideRefreshExternalNativeModel)
        }.flatten()

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
            buildSteps.forEach { buildStep -> buildStep.call() }
        }
    }
}
