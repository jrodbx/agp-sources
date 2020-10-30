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

import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.ExternalNativeJsonGenerator
import com.android.builder.model.ModelBuilderParameter
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeVariantAbi
import com.android.builder.profile.ProcessProfileWriter
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
import java.io.IOException
import java.util.ArrayList
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Builder for the custom Native Android model.
 */
class NativeModelBuilder(
    private val globalScope: GlobalScope,
    private val variantManager: VariantManager
) : ParameterizedToolingModelBuilder<ModelBuilderParameter> {
    private val nativeAndroidProjectClass = NativeAndroidProject::class.java.name
    private val nativeVariantAbiClass = NativeVariantAbi::class.java.name
    private val projectOptions get() = globalScope.projectOptions
    private val ideRefreshExternalNativeModel get() =
        projectOptions.get(BooleanOption.IDE_REFRESH_EXTERNAL_NATIVE_MODEL)
    private val enableParallelNativeJsonGen get() =
        projectOptions.get(BooleanOption.ENABLE_PARALLEL_NATIVE_JSON_GEN)
    private val scopes get() = variantManager.variantScopes
        .filter { it.taskContainer.externalNativeJsonGenerator != null }
        .filterNotNull()
    private val generators get() = scopes.map { it.taskContainer.externalNativeJsonGenerator!!.get() }

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
        return buildFullNativeAndroidProject(project)
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
        generator: ExternalNativeJsonGenerator
    ) {
        builder.addBuildSystem(generator.nativeBuildSystem.tag)
        val abis = generator.abis.map { it.tag }
        val buildFolders = generator.nativeBuildConfigurationsJsons.map { it.parentFile }
        builder.addVariantInfo(
            generator.variantName,
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
        var built = 0
        generators
            .filter { generator -> generator.variantName == variantName }
            .onEach { generator ->
                generator.buildForOneAbiName(
                    ideRefreshExternalNativeModel,
                    abiName,
                    globalScope.project::exec,
                    globalScope.project::javaexec
                )
                buildInexpensiveNativeAndroidProjectInformation(builder, generator)
                ++built
                try {
                    generator.forEachNativeBuildConfiguration { jsonReader ->
                        try {
                            builder.addJson(jsonReader, generator.variantName)
                        } catch (e: IOException) {
                            throw RuntimeException("Failed to read native JSON data", e)
                        }
                    }
                } catch (e: IOException) {
                    throw RuntimeException("Failed to read native JSON data", e)
                }

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
            val stats = ProcessProfileWriter.getOrCreateVariant(project.path, generator.variantName)
            val config = GradleBuildVariant.NativeBuildConfigInfo.newBuilder()

            if (stats.nativeBuildConfigCount == 0) {
                // Do not include stats if they were gathered during build.
                stats.addNativeBuildConfig(config)
            }
            try {
                generator.forEachNativeBuildConfiguration { jsonReader ->
                    try {
                        builder.addJson(jsonReader, generator.variantName, config)
                    } catch (e: IOException) {
                        throw RuntimeException("Failed to read native JSON data", e)
                    }
                }
            } catch (e: IOException) {
                throw RuntimeException("Failed to read native JSON data", e)
            }

        }
        return builder.buildNativeAndroidProject()
    }

    /**
     * SLOW: Utility function which triggers generation of all JSON. This function may execute
     * generation on multiple threads but it will block until all threads complete.
     */
    private fun regenerateNativeJson() {
        if (enableParallelNativeJsonGen) {
            val cpuCores = Runtime.getRuntime().availableProcessors()
            val threadNumber = Math.min(cpuCores, 8)
            val nativeJsonGenExecutor = Executors.newFixedThreadPool(threadNumber)
            val buildSteps = ArrayList<Callable<Void>>()
            for (variantScope in variantManager.variantScopes) {
                val generator = variantScope
                    .taskContainer
                    .externalNativeJsonGenerator?.orNull
                if (generator != null) {
                    // This will generate any out-of-date or non-existent JSONs.
                    // When refreshExternalNativeModel() is true it will also
                    // force update all JSONs.
                    buildSteps.addAll(
                        generator.parallelBuild(
                            ideRefreshExternalNativeModel,
                            globalScope.project::exec,
                            globalScope.project::javaexec
                        )
                    )
                }
            }
            try {
                // Need to get each result even if we're not using the output because that's how we
                // propagate exceptions.
                nativeJsonGenExecutor.invokeAll(buildSteps).map { it.get() }
            } catch (e: InterruptedException) {
                throw RuntimeException(
                    "Thread was interrupted while native build JSON generation was in progress.",
                    e
                )
            }

        } else {
            for (generator in generators) {
                generator.build(
                    ideRefreshExternalNativeModel,
                    globalScope.project::exec,
                    globalScope.project::javaexec
                )
            }
        }
    }

}
