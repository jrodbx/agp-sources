/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.build.gradle.internal.variant

import com.android.build.api.variant.impl.LibraryVariantBuilderImpl
import com.android.build.api.component.ComponentIdentity
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.services.VariantApiServices
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.builder.core.BuilderConstants
import com.android.build.api.variant.impl.VariantOutputConfigurationImpl
import com.android.build.api.dsl.BuildFeatures
import com.android.build.gradle.options.ProjectOptions
import com.android.build.api.dsl.LibraryBuildFeatures
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.impl.LibraryVariantImpl
import java.lang.RuntimeException
import com.android.build.gradle.internal.api.BaseVariantImpl
import com.android.builder.core.VariantTypeImpl
import com.android.builder.errors.IssueReporter
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.*
import com.android.build.gradle.internal.plugins.DslContainerProvider
import com.android.build.gradle.internal.scope.*
import com.android.build.gradle.internal.services.ProjectServices
import com.google.common.collect.ImmutableList

class LibraryVariantFactory(
        projectServices: ProjectServices, globalScope: GlobalScope) : BaseVariantFactory<LibraryVariantBuilderImpl, LibraryVariantImpl>(
        projectServices,
        globalScope) {

    override fun createVariantBuilder(
            componentIdentity: ComponentIdentity,
            variantDslInfo: VariantDslInfo<*>,
            variantApiServices: VariantApiServices): LibraryVariantBuilderImpl {
        return projectServices
                .objectFactory
                .newInstance(
                        LibraryVariantBuilderImpl::class.java,
                        variantDslInfo,
                        componentIdentity,
                        variantApiServices)
    }

    override fun createVariant(
            variantBuilder: LibraryVariantBuilderImpl,
            componentIdentity: ComponentIdentity,
            buildFeatures: BuildFeatureValues,
            variantDslInfo: VariantDslInfo<*>,
            variantDependencies: VariantDependencies,
            variantSources: VariantSources,
            paths: VariantPathHelper,
            artifacts: ArtifactsImpl,
            variantScope: VariantScope,
            variantData: BaseVariantData,
            transformManager: TransformManager,
            variantPropertiesApiServices: VariantPropertiesApiServices,
            taskCreationServices: TaskCreationServices): LibraryVariantImpl {
        val libVariant = projectServices
                .objectFactory
                .newInstance(
                        LibraryVariantImpl::class.java,
                        variantBuilder,
                        buildFeatures,
                        variantDslInfo,
                        variantDependencies,
                        variantSources,
                        paths,
                        artifacts,
                        variantScope,
                        variantData,
                        transformManager,
                        variantPropertiesApiServices,
                        taskCreationServices,
                        globalScope)

        // create default output
        val name = "${libVariant.services.projectInfo.getProjectBaseName()}-${libVariant.baseName}.${BuilderConstants.EXT_LIB_ARCHIVE}"
        libVariant.addVariantOutput(
                VariantOutputConfigurationImpl(false, ImmutableList.of()), name)
        return libVariant
    }

    override fun createBuildFeatureValues(
            buildFeatures: BuildFeatures, projectOptions: ProjectOptions): BuildFeatureValues {
        return if (buildFeatures is LibraryBuildFeatures) {
            BuildFeatureValuesImpl(
                    buildFeatures,
                    projectOptions,
                    null /*dataBindingOverride*/,
                    null /*mlModelBindingOverride*/)
        } else {
            throw RuntimeException("buildFeatures not of type DynamicFeatureBuildFeatures")
        }
    }

    override fun createTestFixturesBuildFeatureValues(
        buildFeatures: BuildFeatures, projectOptions: ProjectOptions): BuildFeatureValues {
        return if (buildFeatures is LibraryBuildFeatures) {
            TestFixturesBuildFeaturesValuesImpl(
                buildFeatures,
                projectOptions,
                dataBindingOverride = null,
                mlModelBindingOverride = null
            )
        } else {
            throw RuntimeException("buildFeatures not of type DynamicFeatureBuildFeatures")
        }
    }

    override fun createTestBuildFeatureValues(
            buildFeatures: BuildFeatures,
            dataBindingOptions: DataBindingOptions,
            projectOptions: ProjectOptions): BuildFeatureValues {
        return BuildFeatureValuesImpl(
                buildFeatures,
                projectOptions,
                null,  /* dataBindingOverride */
                false /* mlModelBindingOverride */)
    }

    override fun createVariantData(
            componentIdentity: ComponentIdentity,
            variantDslInfo: VariantDslInfo<*>,
            variantDependencies: VariantDependencies,
            variantSources: VariantSources,
            paths: VariantPathHelper,
            artifacts: ArtifactsImpl,
            services: VariantPropertiesApiServices,
            globalScope: GlobalScope,
            taskContainer: MutableTaskContainer): BaseVariantData {
        return LibraryVariantData(
                componentIdentity,
                variantDslInfo,
                variantDependencies,
                variantSources,
                paths,
                artifacts,
                services,
                globalScope,
                taskContainer)
    }

    override val variantImplementationClass: Class<out BaseVariantImpl?>
        get() {
            return com.android.build.gradle.internal.api.LibraryVariantImpl::class.java
        }

    override val variantType
        get() = VariantTypeImpl.LIBRARY

    /** * Prevent customization of applicationId or applicationIdSuffix.  */
    override fun validateModel(
            model: VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>) {
        super.validateModel(model)
        val issueReporter: IssueReporter = projectServices.issueReporter
        val defaultConfig = model.defaultConfigData.defaultConfig
        if (defaultConfig.applicationId != null) {
            val applicationId = defaultConfig.applicationId!!
            issueReporter.reportError(
                    IssueReporter.Type.GENERIC,
                    "Library projects cannot set applicationId. applicationId is set to" +
                            " '$applicationId' in default config.",
                    applicationId)
        }
        if (defaultConfig.applicationIdSuffix != null) {
            val applicationIdSuffix = defaultConfig.applicationIdSuffix!!
            issueReporter.reportError(
                    IssueReporter.Type.GENERIC,
                    "Library projects cannot set applicationIdSuffix. applicationIdSuffix is " +
                            "set to '$applicationIdSuffix' in default config.",
                    applicationIdSuffix)
        }
        for (buildType in model.buildTypes.values) {
            if (buildType.buildType.applicationIdSuffix != null) {
                val applicationIdSuffix = buildType.buildType.applicationIdSuffix!!
                issueReporter.reportError(
                        IssueReporter.Type.GENERIC,
                        "Library projects cannot set applicationIdSuffix. applicationIdSuffix " +
                                "is set to '$applicationIdSuffix' in build type " +
                                "'${buildType.buildType.name}'.",
                        applicationIdSuffix)
            }
        }
        for (productFlavor in model.productFlavors.values) {
            if (productFlavor.productFlavor.applicationId != null) {
                val applicationId = productFlavor.productFlavor.applicationId!!
                issueReporter.reportError(
                        IssueReporter.Type.GENERIC,
                        "Library projects cannot set applicationId. applicationId is set to " +
                                "'$applicationId' in flavor '${productFlavor.productFlavor.name}'.",
                        applicationId)
            }
            if (productFlavor.productFlavor.applicationIdSuffix != null) {
                val applicationIdSuffix = productFlavor.productFlavor.applicationIdSuffix!!
                issueReporter.reportError(
                        IssueReporter.Type.GENERIC,
                        "Library projects cannot set applicationIdSuffix. applicationIdSuffix " +
                                "is set to '$applicationIdSuffix' in flavor" +
                                " '${productFlavor.productFlavor.name}'.",
                        applicationIdSuffix)
            }
        }
    }

    override fun createDefaultComponents(
            dslContainers: DslContainerProvider<DefaultConfig, BuildType, ProductFlavor, SigningConfig>) {
        // must create signing config first so that build type 'debug' can be initialized
        // with the debug signing config.
        val signingConfig = dslContainers.signingConfigContainer.create(BuilderConstants.DEBUG)
        dslContainers.buildTypeContainer.create(BuilderConstants.DEBUG)
        dslContainers.buildTypeContainer.create(BuilderConstants.RELEASE)
        dslContainers.defaultConfig.signingConfig = signingConfig
    }
}
