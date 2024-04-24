/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.api.component.impl

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.impl.features.ManifestPlaceholdersCreationConfigImpl
import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.UnitTest
import com.android.build.api.variant.impl.KmpVariantImpl
import com.android.build.api.variant.impl.KotlinMultiplatformAndroidCompilation
import com.android.build.gradle.internal.component.UnitTestCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.component.features.ManifestPlaceholdersCreationConfig
import com.android.build.gradle.internal.core.dsl.impl.DEFAULT_TEST_RUNNER
import com.android.build.gradle.internal.core.dsl.impl.KmpUnitTestDslInfoImpl
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.VariantPathHelper
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import java.io.File
import javax.inject.Inject

open class KmpUnitTestImpl @Inject constructor(
    dslInfo: KmpUnitTestDslInfoImpl,
    internalServices: VariantServices,
    buildFeatures: BuildFeatureValues,
    variantDependencies: VariantDependencies,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    taskContainer: MutableTaskContainer,
    services: TaskCreationServices,
    global: GlobalTaskCreationConfig,
    androidKotlinCompilation: KotlinMultiplatformAndroidCompilation,
    override val mainVariant: KmpVariantImpl,
    manifestFile: File
): KmpComponentImpl<KmpUnitTestDslInfoImpl>(
    dslInfo,
    internalServices,
    buildFeatures,
    variantDependencies,
    paths,
    artifacts,
    taskContainer,
    services,
    global,
    androidKotlinCompilation,
    manifestFile
), UnitTestCreationConfig, UnitTest {

    override fun <T> onTestedVariant(action: (VariantCreationConfig) -> T): T {
        return action.invoke(mainVariant)
    }

    override val instrumentationRunner: Provider<String>
        get() = services.provider { DEFAULT_TEST_RUNNER }
    override val testedApplicationId: Provider<String>
        get() = mainVariant.applicationId
    override val targetSdkVersion: AndroidVersion
        get() = minSdk

    override val manifestPlaceholdersCreationConfig: ManifestPlaceholdersCreationConfig by lazy(LazyThreadSafetyMode.NONE) {
        ManifestPlaceholdersCreationConfigImpl(
            // no dsl for this
            emptyMap(),
            internalServices
        )
    }

    override val manifestPlaceholders: MapProperty<String, String>
        get() = manifestPlaceholdersCreationConfig.placeholders

    override val isUnitTestCoverageEnabled: Boolean
        get() = dslInfo.isUnitTestCoverageEnabled

    override fun <ParamT : InstrumentationParameters> transformClassesWith(
        classVisitorFactoryImplClass: Class<out AsmClassVisitorFactory<ParamT>>,
        scope: InstrumentationScope,
        instrumentationParamsConfig: (ParamT) -> Unit
    ) {
        instrumentation.transformClassesWith(
            classVisitorFactoryImplClass,
            scope,
            instrumentationParamsConfig
        )
    }

    override fun setAsmFramesComputationMode(mode: FramesComputationMode) {
        instrumentation.setAsmFramesComputationMode(mode)
    }
}
