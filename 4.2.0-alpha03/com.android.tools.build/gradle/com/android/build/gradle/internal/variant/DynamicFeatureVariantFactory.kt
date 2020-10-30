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
import com.android.build.api.component.ComponentIdentity
import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.DynamicFeatureBuildFeatures
import com.android.build.api.variant.impl.DynamicFeatureVariantImpl
import com.android.build.api.variant.impl.DynamicFeatureVariantPropertiesImpl
import com.android.build.api.variant.impl.VariantOutputConfigurationImpl
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.DataBindingOptions
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.BuildFeatureValuesImpl
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantApiServices
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.VariantType
import com.android.builder.core.VariantTypeImpl

internal class DynamicFeatureVariantFactory(
    projectServices: ProjectServices,
    globalScope: GlobalScope
) : AbstractAppVariantFactory<DynamicFeatureVariantImpl, DynamicFeatureVariantPropertiesImpl>(
    projectServices,
    globalScope
) {

    override fun createVariantObject(
        componentIdentity: ComponentIdentity,
        variantDslInfo: VariantDslInfo,
        variantApiServices: VariantApiServices
    ): DynamicFeatureVariantImpl {
        return projectServices
            .objectFactory
            .newInstance(
                DynamicFeatureVariantImpl::class.java,
                variantDslInfo,
                componentIdentity,
                variantApiServices
            )
    }

    override fun createVariantPropertiesObject(
        variant: DynamicFeatureVariantImpl,
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
        variantPropertiesApiServices: VariantPropertiesApiServices,
        taskCreationServices: TaskCreationServices
    ): DynamicFeatureVariantPropertiesImpl {
        val variantProperties = projectServices
            .objectFactory
            .newInstance(
                DynamicFeatureVariantPropertiesImpl::class.java,
                componentIdentity,
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
                globalScope
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
        val features = buildFeatures as? DynamicFeatureBuildFeatures
            ?: throw RuntimeException("buildFeatures not of type DynamicFeatureBuildFeatures")

        return BuildFeatureValuesImpl(
            buildFeatures,
            dataBinding = features.dataBinding ?: projectOptions[BooleanOption.BUILD_FEATURE_DATABINDING],
            mlModelBinding = features.mlModelBinding ?: projectOptions[BooleanOption.BUILD_FEATURE_MLMODELBINDING],
            projectOptions = projectOptions)
    }

    override fun createTestBuildFeatureValues(
        buildFeatures: BuildFeatures,
        dataBindingOptions: DataBindingOptions,
        projectOptions: ProjectOptions
    ): BuildFeatureValues {
        val features = buildFeatures as? DynamicFeatureBuildFeatures
            ?: throw RuntimeException("buildFeatures not of type DynamicFeatureBuildFeatures")

        val dataBinding =
            features.dataBinding ?: projectOptions[BooleanOption.BUILD_FEATURE_DATABINDING]

        return BuildFeatureValuesImpl(
            buildFeatures,
            dataBinding = dataBinding && dataBindingOptions.isEnabledForTests,
            projectOptions = projectOptions)
    }

    override fun getVariantType(): VariantType {
        return VariantTypeImpl.OPTIONAL_APK
    }
}