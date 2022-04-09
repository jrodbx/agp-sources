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

package com.android.build.gradle.internal.variant

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.DataBinding
import com.android.build.api.dsl.DynamicFeatureBuildFeatures
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.impl.DynamicFeatureVariantBuilderImpl
import com.android.build.api.variant.impl.DynamicFeatureVariantImpl
import com.android.build.api.variant.impl.GlobalVariantBuilderConfig
import com.android.build.api.variant.impl.VariantOutputConfigurationImpl
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.BuildFeatureValuesImpl
import com.android.build.gradle.internal.scope.TestFixturesBuildFeaturesValuesImpl
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantBuilderServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.ComponentTypeImpl

internal class DynamicFeatureVariantFactory(
    projectServices: ProjectServices,
) : AbstractAppVariantFactory<DynamicFeatureVariantBuilderImpl, DynamicFeatureVariantImpl>(
    projectServices,
) {

    override fun createVariantBuilder(
        globalVariantBuilderConfig: GlobalVariantBuilderConfig,
        componentIdentity: ComponentIdentity,
        variantDslInfo: VariantDslInfo,
        variantBuilderServices: VariantBuilderServices
    ): DynamicFeatureVariantBuilderImpl {
        return projectServices
            .objectFactory
            .newInstance(
                DynamicFeatureVariantBuilderImpl::class.java,
                globalVariantBuilderConfig,
                variantDslInfo,
                componentIdentity,
                variantBuilderServices
            )
    }

    override fun createVariant(
        variantBuilder: DynamicFeatureVariantBuilderImpl,
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
        ): DynamicFeatureVariantImpl {
        val variantProperties = projectServices
            .objectFactory
            .newInstance(
                DynamicFeatureVariantImpl::class.java,
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
                variantServices,
                taskCreationServices,
                globalConfig,
            )

        // create default output
        variantProperties.addVariantOutput(
            variantOutputConfiguration = VariantOutputConfigurationImpl()
        )

        return variantProperties
    }

    override fun createBuildFeatureValues(
        buildFeatures: BuildFeatures,
        projectOptions: ProjectOptions
    ): BuildFeatureValues {
        buildFeatures as? DynamicFeatureBuildFeatures
            ?: throw RuntimeException("buildFeatures not of type DynamicFeatureBuildFeatures")

        return BuildFeatureValuesImpl(buildFeatures, projectOptions)
    }

    override fun createTestFixturesBuildFeatureValues(
        buildFeatures: BuildFeatures,
        projectOptions: ProjectOptions,
        androidResourcesEnabled: Boolean
    ): BuildFeatureValues {
        buildFeatures as? DynamicFeatureBuildFeatures
            ?: throw RuntimeException("buildFeatures not of type DynamicFeatureBuildFeatures")

        return TestFixturesBuildFeaturesValuesImpl(
            buildFeatures,
            projectOptions,
            androidResourcesEnabled
        )
    }

    override fun createTestBuildFeatureValues(
        buildFeatures: BuildFeatures,
        dataBinding: DataBinding,
        projectOptions: ProjectOptions
    ): BuildFeatureValues {
        buildFeatures as? DynamicFeatureBuildFeatures
            ?: throw RuntimeException("buildFeatures not of type DynamicFeatureBuildFeatures")

        return BuildFeatureValuesImpl(
            buildFeatures,
            projectOptions,
            dataBindingOverride = if (!dataBinding.isEnabledForTests) {
                false
            } else {
                null // means whatever is default.
            },
            mlModelBindingOverride = false
        )
    }

    override val componentType
        get() = ComponentTypeImpl.OPTIONAL_APK
}
