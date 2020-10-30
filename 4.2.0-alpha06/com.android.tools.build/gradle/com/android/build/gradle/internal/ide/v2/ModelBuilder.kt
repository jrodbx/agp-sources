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

import com.android.Version
import com.android.build.api.dsl.AndroidSourceSet
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.DefaultConfig
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.dsl.SigningConfig
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantProperties
import com.android.build.gradle.internal.cxx.configure.ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.errors.SyncIssueReporter
import com.android.build.gradle.internal.errors.SyncIssueReporterImpl.GlobalSyncIssueService
import com.android.build.gradle.internal.ide.verifyIDEIsNotOld
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.variant.VariantModel
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.VariantTypeImpl
import com.android.builder.model.SyncIssue
import com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag
import com.android.builder.model.v2.ide.BuildTypeContainer
import com.android.builder.model.v2.ide.ProductFlavorContainer
import com.android.builder.model.v2.ide.ProjectType
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.GlobalLibraryMap
import com.android.builder.model.v2.models.ModelBuilderParameter
import com.android.builder.model.v2.models.ProjectSyncIssues
import com.android.builder.model.v2.models.VariantDependencies
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
import com.android.builder.model.v2.ide.Variant as V2IdeVariant

class ModelBuilder<
        AndroidSourceSetT : AndroidSourceSet,
        BuildFeaturesT : BuildFeatures,
        BuildTypeT : BuildType,
        DefaultConfigT : DefaultConfig,
        ProductFlavorT : ProductFlavor,
        SigningConfigT : SigningConfig,
        VariantT : Variant<VariantPropertiesT>,
        VariantPropertiesT : VariantProperties,
        ExtensionT : CommonExtension<
                AndroidSourceSetT,
                BuildFeaturesT,
                BuildTypeT,
                DefaultConfigT,
                ProductFlavorT,
                SigningConfigT,
                VariantT,
                VariantPropertiesT>>(
    private val globalScope: GlobalScope,
    private val variantModel: VariantModel,
    private val extension: ExtensionT,
    private val syncIssueReporter: SyncIssueReporter,
    private val projectType: ProjectType
) : ParameterizedToolingModelBuilder<ModelBuilderParameter> {

    override fun getParameterType(): Class<ModelBuilderParameter> {
        return ModelBuilderParameter::class.java
    }

    override fun canBuild(className: String): Boolean {
        return className == AndroidProject::class.java.name
                || className == GlobalLibraryMap::class.java.name
                || className == VariantDependencies::class.java.name
                || className == ProjectSyncIssues::class.java.name
    }

    /**
     * Non-parameterized model query. Valid for all but the VariantDependencies model
     */
    override fun buildAll(className: String, project: Project): Any = when (className) {
        AndroidProject::class.java.name -> buildAndroidProjectModel(project)
        GlobalLibraryMap::class.java.name -> buildGlobalLibraryMapModel(project)
        ProjectSyncIssues::class.java.name -> buildProjectSyncIssueModel(project)
        VariantDependencies::class.java.name -> throw RuntimeException(
            "Please use parameterized Tooling API to obtain VariantDependencies model."
        )
        else -> throw RuntimeException("Does not support model '$className'")
    }

    /**
     * Non-parameterized model query. Valid only for the VariantDependencies model
     */
    override fun buildAll(
        className: String,
        parameter: ModelBuilderParameter,
        project: Project
    ): Any = when (className) {
        VariantDependencies::class.java.name -> buildVariantDependenciesModel(project, parameter)
        AndroidProject::class.java.name,
        GlobalLibraryMap::class.java.name,
        ProjectSyncIssues::class.java.name -> throw RuntimeException(
            "Please use non-parameterized Tooling API to obtain $className model."
        )
        else -> throw RuntimeException("Does not support model '$className'")
    }

    private fun buildAndroidProjectModel(project: Project): AndroidProject {
        // Cannot be injected, as the project might not be the same as the project used to construct
        // the model builder e.g. when lint explicitly builds the model.
        val projectOptions = ProjectOptions(project)

        verifyIDEIsNotOld(projectOptions)

        val sdkComponents = globalScope.sdkComponents.get()
        val sdkSetupCorrectly = sdkComponents.sdkSetupCorrectly.get()

        // Get the boot classpath. This will ensure the target is configured.
        val bootClasspath = if (sdkSetupCorrectly) {
            globalScope.filteredBootClasspath.get().map { it.asFile }
        } else {
            // SDK not set up, error will be reported as a sync issue.
            emptyList()
        }

        val dependenciesInfo =
            if (extension is ApplicationExtension<*, *, *, *, *>) {
                DependenciesInfoImpl(
                    extension.dependenciesInfo.includeInApk,
                    extension.dependenciesInfo.includeInBundle
                )
            } else null

        val variantInputs = variantModel.inputs

        val variants = variantModel.variants

        // for now grab the first buildFeatureValues as they cannot be different.
        val buildFeatures = variants.first().buildFeatures

        // gather the default config
        val defaultConfigData = variantInputs.defaultConfigData
        val defaultConfig = ProductFlavorContainerImpl(
            productFlavor = defaultConfigData.defaultConfig.convert(),
            sourceProvider = defaultConfigData.sourceSet.convert(buildFeatures),
            androidTestSourceProvider = defaultConfigData.getTestSourceSet(VariantTypeImpl.ANDROID_TEST)?.convert(buildFeatures),
            unitTestSourceProvider = defaultConfigData.getTestSourceSet(VariantTypeImpl.UNIT_TEST)?.convert(buildFeatures)
        )

        // gather all the build types
        val buildTypes = mutableListOf<BuildTypeContainer>()
        for (buildType in variantInputs.buildTypes.values) {
            buildTypes.add(
                BuildTypeContainerImpl(
                    buildType = buildType.buildType.convert(),
                    sourceProvider = buildType.sourceSet.convert(buildFeatures),
                    androidTestSourceProvider = buildType.getTestSourceSet(VariantTypeImpl.ANDROID_TEST)?.convert(buildFeatures),
                    unitTestSourceProvider = buildType.getTestSourceSet(VariantTypeImpl.UNIT_TEST)?.convert(buildFeatures)
                )
            )
        }

        // gather product flavors
        val productFlavors = mutableListOf<ProductFlavorContainer>()
        for (flavor in variantInputs.productFlavors.values) {
            productFlavors.add(
                ProductFlavorContainerImpl(
                    productFlavor = flavor.productFlavor.convert(),
                    sourceProvider = flavor.sourceSet.convert(buildFeatures),
                    androidTestSourceProvider = flavor.getTestSourceSet(VariantTypeImpl.ANDROID_TEST)?.convert(buildFeatures),
                    unitTestSourceProvider = flavor.getTestSourceSet(VariantTypeImpl.UNIT_TEST)?.convert(buildFeatures)
                )
            )
        }

        // gather variants
        val variantList = mutableListOf<V2IdeVariant>()
//        TODO


        return AndroidProjectImpl(
            modelVersion = Version.ANDROID_GRADLE_PLUGIN_VERSION,
            apiVersion = Version.BUILDER_MODEL_API_VERSION,

            path = project.path,
            buildFolder = project.layout.buildDirectory.get().asFile,

            projectType = projectType,
            buildToolsVersion = extension.buildToolsVersion,

            groupId = project.group.toString(),

            defaultConfig = defaultConfig,
            buildTypes = buildTypes,
            flavorDimensions = extension.flavorDimensions,
            productFlavors = productFlavors,

            variants = variantList,
            defaultVariant = variantModel.defaultVariant,

            compileTarget = extension.compileSdk.toString(), // FIXME
            bootClasspath = bootClasspath,

            signingConfigs = extension.signingConfigs.map { it.convert() },
            aaptOptions = extension.aaptOptions.convert(),
            lintOptions = extension.lintOptions.convert(),
            javaCompileOptions = extension.compileOptions.convert(),
            resourcePrefix = extension.resourcePrefix,
            dynamicFeatures = (extension as? BaseAppModuleExtension)?.dynamicFeatures,
            viewBindingOptions = ViewBindingOptionsImpl(
                variantModel.variants.any { it.buildFeatures.viewBinding }
            ),
            dependenciesInfo = dependenciesInfo,

            flags = getFlags(),
            lintRuleJars = globalScope.localCustomLintChecks.files.toList()
        )
    }

    private fun buildGlobalLibraryMapModel(project: Project): GlobalLibraryMap {
        throw RuntimeException("Not yet implemented")
    }

    private fun buildProjectSyncIssueModel(project: Project): ProjectSyncIssues {
        syncIssueReporter.lockHandler()

        val allIssues = ImmutableSet.builder<SyncIssue>()
        allIssues.addAll(syncIssueReporter.syncIssues)
        allIssues.addAll(
            getBuildService(project.gradle.sharedServices, GlobalSyncIssueService::class.java)
                .get()
                .getAllIssuesAndClear()
        )

        // For now we have to convert from the v1 to the v2 model.
        // FIXME: separate the internal-AGP and builder-model version of the SyncIssue classes
        val issues = allIssues.build().map {
            SyncIssueImpl(
                it.severity,
                it.type,
                it.data,
                it.message,
                it.multiLineMessage
            )
        }

        return ProjectSyncIssuesImpl(issues)
    }

    private fun buildVariantDependenciesModel(
        project: Project,
        parameter: ModelBuilderParameter
    ): VariantDependencies {
        throw RuntimeException("Not yet implemented")
    }

    private fun getFlags(): AndroidGradlePluginProjectFlagsImpl {
        val flags =
            ImmutableMap.builder<BooleanFlag, Boolean>()

        val finalResIds = !globalScope.projectOptions[BooleanOption.USE_NON_FINAL_RES_IDS]

        flags.put(BooleanFlag.APPLICATION_R_CLASS_CONSTANT_IDS, finalResIds)
        flags.put(BooleanFlag.TEST_R_CLASS_CONSTANT_IDS, finalResIds)
        flags.put(
            BooleanFlag.JETPACK_COMPOSE,
            variantModel.variants.any { it.buildFeatures.compose }
        )
        flags.put(
            BooleanFlag.ML_MODEL_BINDING,
            variantModel.variants.any { it.buildFeatures.mlModelBinding }
        )
        flags.put(
            BooleanFlag.TRANSITIVE_R_CLASS,
            !globalScope.projectOptions[BooleanOption.NON_TRANSITIVE_R_CLASS]
        )

        return AndroidGradlePluginProjectFlagsImpl(flags.build())
    }
}
