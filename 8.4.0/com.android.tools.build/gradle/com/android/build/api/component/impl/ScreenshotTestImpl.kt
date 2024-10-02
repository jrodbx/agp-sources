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
import com.android.build.api.component.UnitTest
import com.android.build.api.variant.ComponentIdentity
import com.android.build.gradle.internal.component.HostTestCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.HostTestComponentDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import javax.inject.Inject

open class ScreenshotTestImpl @Inject constructor(
    componentIdentity: ComponentIdentity,
    buildFeatureValues: BuildFeatureValues,
    dslInfo: HostTestComponentDslInfo,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    variantData: BaseVariantData,
    taskContainer: MutableTaskContainer,
    testedVariant: VariantCreationConfig,
    internalServices: VariantServices,
    taskCreationServices: TaskCreationServices,
    global: GlobalTaskCreationConfig
) : HostTestImpl(
    componentIdentity,
    buildFeatureValues,
    dslInfo,
    variantDependencies,
    variantSources,
    paths,
    artifacts,
    variantData,
    taskContainer,
    testedVariant,
    internalServices,
    taskCreationServices,
    global
), UnitTest, HostTestCreationConfig {

    // these would normally be public but not for unit-test. They are there to feed the
    // manifest but aren't actually used.
    override val isUnitTestCoverageEnabled: Boolean
        get() = false

    override val isScreenshotTestCoverageEnabled: Boolean
        get() = dslInfo.isScreenshotTestCoverageEnabled

    override fun configureTestTask(action: (Test) -> Unit) {
        throw RuntimeException("Screenshot testing variants do not have a Test task")
    }

    override fun runTestTaskConfigurationActions(testTask: TaskProvider<out Test>) {
        // nothing to do until we have a Test task.
    }
}
