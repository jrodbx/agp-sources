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

import com.android.build.api.component.impl.ComponentIdentityImpl
import com.android.build.api.variant.impl.KmpPredefinedAndroidCompilation
import com.android.build.gradle.internal.core.dsl.KmpComponentDslInfo
import com.android.build.gradle.internal.core.dsl.KmpVariantDslInfo
import com.android.build.gradle.internal.core.dsl.UnitTestComponentDslInfo
import com.android.build.gradle.internal.core.dsl.features.ManifestPlaceholdersDslInfo
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtension
import com.android.build.gradle.internal.services.VariantServices
import com.android.builder.core.ComponentTypeImpl
import org.gradle.api.provider.Provider

class KmpUnitTestDslInfoImpl(
    extension: KotlinMultiplatformAndroidExtension,
    services: VariantServices,
    override val mainVariantDslInfo: KmpVariantDslInfo
): KmpComponentDslInfoImpl(
    extension, services
), UnitTestComponentDslInfo, KmpComponentDslInfo {

    override val componentType = ComponentTypeImpl.UNIT_TEST
    override val componentIdentity = ComponentIdentityImpl(
        KmpPredefinedAndroidCompilation.UNIT_TEST.getNamePrefixedWithTarget()
    )

    override val namespace: Provider<String> by lazy {
        extension.testNamespace?.let { services.provider { it } }
            ?: extension.namespace?.let { services.provider {"$it.test" } }
            ?: mainVariantDslInfo.namespace.map { testedVariantNamespace ->
                "$testedVariantNamespace.test"
            }
    }

    override val isUnitTestCoverageEnabled: Boolean
        get() = extension.enableUnitTestCoverage
}
