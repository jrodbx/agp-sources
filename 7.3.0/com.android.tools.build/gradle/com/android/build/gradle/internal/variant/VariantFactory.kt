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

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.impl.AndroidTestImpl
import com.android.build.api.component.impl.ComponentImpl
import com.android.build.api.component.impl.TestFixturesImpl
import com.android.build.api.component.impl.UnitTestImpl
import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.DataBinding
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.api.variant.impl.GlobalVariantBuilderConfig
import com.android.build.api.variant.impl.VariantBuilderImpl
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.gradle.internal.api.BaseVariantImpl
import com.android.build.gradle.internal.api.ReadOnlyObjectProvider
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.plugins.DslContainerProvider
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.BaseServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantBuilderServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.ComponentType
import org.gradle.api.Project

/**
 * Interface for Variant Factory.
 *
 *
 * While VariantManager is the general variant management, implementation of this interface
 * provides variant type (app, lib) specific implementation.
 */
interface VariantFactory<VariantBuilderT : VariantBuilderImpl, VariantT : VariantImpl> {

    fun createVariantBuilder(
        globalVariantBuilderConfig: GlobalVariantBuilderConfig,
        componentIdentity: ComponentIdentity,
        variantDslInfo: VariantDslInfo,
        variantBuilderServices: VariantBuilderServices
    ): VariantBuilderT

    fun createVariant(
        variantBuilder: VariantBuilderT,
        componentIdentity: ComponentIdentity,
        buildFeatures: BuildFeatureValues,
        variantDslInfo: VariantDslInfo,
        variantDependencies: VariantDependencies,
        variantSources: VariantSources,
        paths: VariantPathHelper,
        artifacts: ArtifactsImpl,
        variantScope: VariantScope,
        variantData: BaseVariantData,
        transformManager: TransformManager,
        variantServices: VariantServices,
        taskCreationServices: TaskCreationServices,
        globalConfig: GlobalTaskCreationConfig,
    ): VariantT

    fun createTestFixtures(
        componentIdentity: ComponentIdentity,
        buildFeatures: BuildFeatureValues,
        variantDslInfo: VariantDslInfo,
        variantDependencies: VariantDependencies,
        variantSources: VariantSources,
        paths: VariantPathHelper,
        artifacts: ArtifactsImpl,
        variantScope: VariantScope,
        variantData: TestFixturesVariantData,
        mainVariant: VariantImpl,
        transformManager: TransformManager,
        variantServices: VariantServices,
        taskCreationServices: TaskCreationServices,
        globalConfig: GlobalTaskCreationConfig
    ): TestFixturesImpl

    fun createUnitTest(
        componentIdentity: ComponentIdentity,
        buildFeatures: BuildFeatureValues,
        variantDslInfo: VariantDslInfo,
        variantDependencies: VariantDependencies,
        variantSources: VariantSources,
        paths: VariantPathHelper,
        artifacts: ArtifactsImpl,
        variantScope: VariantScope,
        variantData: TestVariantData,
        testedVariantProperties: VariantImpl,
        transformManager: TransformManager,
        variantServices: VariantServices,
        taskCreationServices: TaskCreationServices,
        globalConfig: GlobalTaskCreationConfig,
    ): UnitTestImpl

    fun createAndroidTest(
        componentIdentity: ComponentIdentity,
        buildFeatures: BuildFeatureValues,
        variantDslInfo: VariantDslInfo,
        variantDependencies: VariantDependencies,
        variantSources: VariantSources,
        paths: VariantPathHelper,
        artifacts: ArtifactsImpl,
        variantScope: VariantScope,
        variantData: TestVariantData,
        testedVariantProperties: VariantImpl,
        transformManager: TransformManager,
        variantServices: VariantServices,
        taskCreationServices: TaskCreationServices,
        globalConfig: GlobalTaskCreationConfig,
    ): AndroidTestImpl

    fun createVariantData(
        componentIdentity: ComponentIdentity,
        variantDslInfo: VariantDslInfo,
        variantDependencies: VariantDependencies,
        variantSources: VariantSources,
        paths: VariantPathHelper,
        artifacts: ArtifactsImpl,
        services: VariantServices,
        taskContainer: MutableTaskContainer
    ): BaseVariantData

    fun createBuildFeatureValues(
            buildFeatures: BuildFeatures, projectOptions: ProjectOptions): BuildFeatureValues

    fun createTestFixturesBuildFeatureValues(
        buildFeatures: BuildFeatures,
        projectOptions: ProjectOptions,
        androidResourcesEnabled: Boolean
    ): BuildFeatureValues

    fun createTestBuildFeatureValues(
            buildFeatures: BuildFeatures,
            dataBinding: DataBinding,
            projectOptions: ProjectOptions): BuildFeatureValues

    val variantImplementationClass: Class<out BaseVariantImpl?>

    fun createVariantApi(
            component: ComponentImpl,
            variantData: BaseVariantData,
            readOnlyObjectProvider: ReadOnlyObjectProvider): BaseVariantImpl?

    val servicesForOldVariantObjectsOnly: BaseServices
    val componentType: ComponentType

    /**
     * Callback before variant creation to allow extra work or validation
     *
     * @param project the Project
     * @param dslExtension the Extension
     * @param model the non-null model to validate, as implemented by the VariantManager.

     * @throws org.gradle.api.GradleException in case of failed validation
     */
    fun preVariantCallback(
        project: Project,
        dslExtension: CommonExtension<*, *, *, *>,
        model: VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
    )

    fun createDefaultComponents(
            dslContainers: DslContainerProvider<DefaultConfig, BuildType, ProductFlavor, SigningConfig>)
}
