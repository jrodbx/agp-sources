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
import com.android.utils.appendCapitalized

/**
 * Data about a test component in a normal plugin
 *
 *
 * For the test plugin, ApplicationVariantData is used.
 */
class TestVariantData(
    componentIdentity: ComponentIdentity,
    variantDslInfo: VariantDslInfo<*>,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    val testedVariantData: TestedVariantData,
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
) {

    override val description: String
        get() {
            val variantType = variantDslInfo.variantType

            val prefix = if (variantType.isApk) {
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

}
