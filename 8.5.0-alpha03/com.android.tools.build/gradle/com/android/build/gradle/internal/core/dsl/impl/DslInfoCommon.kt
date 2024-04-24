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

import com.android.build.api.dsl.ProductFlavor
import com.android.build.gradle.internal.core.dsl.ComponentDslInfo
import com.android.build.gradle.internal.core.dsl.TestComponentDslInfo
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.InternalTestedExtension
import com.android.build.gradle.internal.services.VariantServices
import com.android.builder.errors.IssueReporter
import org.gradle.api.provider.Provider

internal fun TestComponentDslInfo.getTestComponentNamespace(
    extension: InternalTestedExtension<*, *, *, *, *, *>,
    services: VariantServices
): Provider<String> {
    return extension.testNamespace?.let {
        services.provider {
            if (extension.testNamespace == extension.namespace) {
                services.issueReporter
                    .reportError(
                        IssueReporter.Type.GENERIC,
                        "namespace and testNamespace have the same value (\"$it\"), which is not allowed."
                    )
            }
            it
        }
    } ?: extension.namespace?.let { services.provider {"$it.test" } }
    ?: mainVariantDslInfo.namespace.map { "$it.test" }
}

// Special case for test components and separate test sub-projects
internal fun ComponentDslInfo.initTestApplicationId(
    productFlavorList: List<ProductFlavor>,
    defaultConfig: DefaultConfig,
    services: VariantServices,
): Provider<String> {
    // get first non null testAppId from flavors/default config
    val testAppIdFromFlavors =
        productFlavorList.asSequence().map { it.testApplicationId }
            .firstOrNull { it != null }
            ?: defaultConfig.testApplicationId

    return if (testAppIdFromFlavors != null) {
        services.provider { testAppIdFromFlavors }
    } else if (this is TestComponentDslInfo) {
        this.mainVariantDslInfo.applicationId.map {
            "$it.test"
        }
    } else {
        namespace
    }
}
