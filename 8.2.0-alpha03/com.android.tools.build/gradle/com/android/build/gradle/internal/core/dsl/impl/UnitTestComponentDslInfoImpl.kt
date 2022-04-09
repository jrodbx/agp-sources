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

package com.android.build.gradle.internal.core.dsl.impl

import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.impl.MutableAndroidVersion
import com.android.build.gradle.internal.core.dsl.TestedVariantDslInfo
import com.android.build.gradle.internal.core.dsl.UnitTestComponentDslInfo
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.InternalTestedExtension
import com.android.build.gradle.internal.services.VariantServices
import com.android.builder.core.ComponentType
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

internal class UnitTestComponentDslInfoImpl(
    componentIdentity: ComponentIdentity,
    componentType: ComponentType,
    defaultConfig: DefaultConfig,
    buildTypeObj: BuildType,
    productFlavorList: List<ProductFlavor>,
    services: VariantServices,
    buildDirectory: DirectoryProperty,
    override val mainVariantDslInfo: TestedVariantDslInfo,
    extension: InternalTestedExtension<*, *, *, *, *>
) : ComponentDslInfoImpl(
    componentIdentity,
    componentType,
    defaultConfig,
    buildTypeObj,
    productFlavorList,
    services,
    buildDirectory,
    extension
), UnitTestComponentDslInfo {

    override val namespace: Provider<String> by lazy {
        getTestComponentNamespace(extension, services)
    }

    override val applicationId: Property<String> =
        services.newPropertyBackingDeprecatedApi(
            String::class.java,
            initTestApplicationId(productFlavorList, defaultConfig, services)
        )

    override val minSdkVersion: MutableAndroidVersion
        get() = mainVariantDslInfo.minSdkVersion

    override val isUnitTestCoverageEnabled: Boolean
        get() = buildTypeObj.enableUnitTestCoverage || buildTypeObj.isTestCoverageEnabled
}
