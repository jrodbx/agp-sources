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

import com.android.build.api.component.impl.ComponentImpl
import com.android.build.gradle.internal.cxx.configure.toConfigurationModel
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.cxx.gradle.generator.CxxMetadataGenerator
import com.android.build.gradle.internal.cxx.gradle.generator.createCxxMetadataGenerator
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment
import com.android.build.gradle.internal.cxx.model.additionalProjectFilesIndexFile
import com.android.build.gradle.internal.cxx.model.buildFileIndexFile
import com.android.build.gradle.internal.cxx.model.compileCommandsJsonBinFile
import com.android.build.gradle.internal.cxx.model.ifCMake
import com.android.build.gradle.internal.cxx.model.symbolFolderIndexFile
import com.android.build.gradle.internal.errors.SyncIssueReporter
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.variant.VariantModel
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.model.v2.models.ndk.NativeBuildSystem.CMAKE
import com.android.builder.model.v2.models.ndk.NativeBuildSystem.NDK_BUILD
import com.android.builder.model.v2.models.ndk.NativeModelBuilderParameter
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.builder.model.v2.models.ndk.NativeVariant
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder

class NativeModelBuilder(
    private val issueReporter: SyncIssueReporter,
    private val projectOptions: ProjectOptions,
    private val globalScope: GlobalScope,
    private val variantModel: VariantModel,
    private val projectInfo: ProjectInfo
) : ParameterizedToolingModelBuilder<NativeModelBuilderParameter> {
    private val ops = object : ExecOperations {
        override fun exec(action: Action<in ExecSpec>) =
            projectInfo.getProject().exec(action)

        override fun javaexec(action: Action<in JavaExecSpec>) =
                projectInfo.getProject().javaexec(action)
    }
    private val ideRefreshExternalNativeModel
        get() =
            projectOptions.get(BooleanOption.IDE_REFRESH_EXTERNAL_NATIVE_MODEL)
    private val scopes: List<ComponentImpl> by lazy {
        (variantModel.variants + variantModel.testComponents)
            .filter { it.taskContainer.cxxConfigurationModel != null }
    }
    private val configurationModels by lazy {
        scopes.map { scope -> scope.name to scope.taskContainer.cxxConfigurationModel!! }.distinct()
    }
    private val generators =
            mutableMapOf<CxxConfigurationModel, CxxMetadataGenerator>()

    fun createGenerator(model: CxxConfigurationModel) : CxxMetadataGenerator {
        return generators.computeIfAbsent(model) { model ->
            val analyticsService =
                getBuildService<AnalyticsService>(projectInfo.getProject().gradle.sharedServices).get()
            IssueReporterLoggingEnvironment(issueReporter, analyticsService, model).use {
                createCxxMetadataGenerator(
                    model,
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
        if (configurationModels.isEmpty()) return null
        val analyticsService =
            getBuildService<AnalyticsService>(project.gradle.sharedServices).get()
        val configurationModel = configurationModels.first().second
        return IssueReporterLoggingEnvironment(
            issueReporter,
            analyticsService,
            configurationModel
        ).use {
            val cxxModuleModel = configurationModel.variant.module

            val buildSystem = cxxModuleModel.ifCMake { CMAKE } ?: NDK_BUILD

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
    }

    private fun NativeModelBuilderParameter?.asPredicate() = { variant: String, abi: String ->
        this?.variantsToGenerateBuildInformation?.contains(variant) ?: true &&
                this?.abisToGenerateBuildInformation?.contains(abi) ?: true
    }

    private fun generateBuildFilesAndCompileCommandsJson(filter: (variant: String, abi: String) -> Boolean) {
        configurationModels
                .flatMap { (variantName, model) -> model.activeAbis.map { abi -> variantName to abi } }
                .filter { (variantName, abi) -> filter(variantName, abi.abi.tag) }
                .map { (_, abi) -> abi }
                .forEach { abi ->
                    createGenerator(abi.toConfigurationModel()).generate(ops, ideRefreshExternalNativeModel)
                }
    }
}
