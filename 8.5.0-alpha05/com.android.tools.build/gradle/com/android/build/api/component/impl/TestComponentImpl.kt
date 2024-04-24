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

package com.android.build.api.component.impl

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.TestComponent
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.TestComponentDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.utils.appendCapitalized
import javax.inject.Inject

abstract class TestComponentImpl<DslInfoT: TestComponentDslInfo> @Inject constructor(
    componentIdentity: ComponentIdentity,
    buildFeatureValues: BuildFeatureValues,
    dslInfo: DslInfoT,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    variantData: BaseVariantData,
    taskContainer: MutableTaskContainer,
    override val mainVariant: VariantCreationConfig,
    variantServices: VariantServices,
    taskCreationServices: TaskCreationServices,
    global: GlobalTaskCreationConfig,
) : ComponentImpl<DslInfoT>(
    componentIdentity,
    buildFeatureValues,
    dslInfo,
    variantDependencies,
    variantSources,
    paths,
    artifacts,
    variantData,
    taskContainer,
    variantServices,
    taskCreationServices,
    global
), TestComponent, TestComponentCreationConfig {

    override val description: String
        get() {
            val componentType = dslInfo.componentType

            val prefix = if (componentType.isApk) {
                "android (on device) tests"
            } else {
                "unit tests"
            }

            return if (componentIdentity.productFlavors.isNotEmpty()) {
                val sb = StringBuilder(50)
                sb.append(prefix)
                sb.append(" for the ")
                componentIdentity.flavorName?.let { sb.appendCapitalized(it) }
                componentIdentity.buildType?.let { sb.appendCapitalized(it) }
                sb.append(" build")
                sb.toString()
            } else {
                val sb = StringBuilder(50)
                sb.append(prefix)
                sb.append(" for the ")
                sb.appendCapitalized(componentIdentity.buildType!!)
                sb.append(" build")
                sb.toString()
            }
        }

    override fun <T> onTestedVariant(action: (VariantCreationConfig) -> T): T {
        return action(mainVariant)
    }
}
