/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import com.android.builder.core.VariantType
import com.android.utils.appendCapitalized
import com.android.utils.capitalizeAndAppend

/**
 * Data about a variant that produce an application APK.
 *
 *
 * This includes application, dynamic-feature and standalone Test plugins.
 */
class ApplicationVariantData(
    componentIdentity: ComponentIdentity,
    variantDslInfo: VariantDslInfo<*>,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    services: VariantPropertiesApiServices,
    globalScope: GlobalScope,
    taskContainer: MutableTaskContainer
) : ApkVariantData(
    componentIdentity,
    variantDslInfo,
    variantDependencies,
    variantSources,
    paths,
    artifacts,
    services,
    globalScope,
    taskContainer
), TestedVariantData {

    private val testVariants: MutableMap<VariantType, TestVariantData> = mutableMapOf()

    override fun setTestVariantData(testVariantData: TestVariantData, type: VariantType) {
        testVariants[type] = testVariantData
    }

    override fun getTestVariantData(type: VariantType): TestVariantData? {
        return testVariants[type]
    }

    override val description: String
        get() = if (componentIdentity.productFlavors.isNotEmpty()) {
            val sb = StringBuilder(50)
            componentIdentity.buildType?.let { sb.appendCapitalized(it) }
            sb.appendCapitalized(componentIdentity.name)
            sb.append(" build")
            sb.toString()
        } else {
            variantDslInfo.componentIdentity.buildType!!.capitalizeAndAppend(" build")
        }
}
