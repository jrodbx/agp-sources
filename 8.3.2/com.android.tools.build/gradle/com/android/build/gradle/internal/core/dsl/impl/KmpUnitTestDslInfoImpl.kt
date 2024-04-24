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
import com.android.build.api.dsl.KotlinMultiplatformAndroidExtension
import com.android.build.gradle.internal.core.dsl.KmpComponentDslInfo
import com.android.build.gradle.internal.core.dsl.KmpVariantDslInfo
import com.android.build.gradle.internal.core.dsl.HostTestComponentDslInfo
import com.android.build.api.variant.ResValue
import com.android.build.gradle.internal.core.dsl.features.AndroidResourcesDslInfo
import com.android.build.gradle.internal.dsl.AaptOptions
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtensionImpl
import com.android.build.gradle.internal.plugins.KotlinMultiplatformAndroidPlugin.Companion.getNamePrefixedWithAndroidTarget
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.builder.core.ComponentTypeImpl
import com.android.builder.core.DefaultVectorDrawablesOptions
import com.android.builder.model.VectorDrawablesOptions
import com.google.common.collect.ImmutableSet
import org.gradle.api.provider.Provider

class KmpUnitTestDslInfoImpl(
    extension: KotlinMultiplatformAndroidExtension,
    services: VariantServices,
    override val mainVariantDslInfo: KmpVariantDslInfo,
    withJava: Boolean,
    dslServices: DslServices
): KmpComponentDslInfoImpl(
    extension, services, withJava
), HostTestComponentDslInfo, KmpComponentDslInfo {

    private val testOnJvmConfig = (extension as KotlinMultiplatformAndroidExtensionImpl).androidTestOnJvmOptions!!

    override val componentType = ComponentTypeImpl.UNIT_TEST
    override val componentIdentity = ComponentIdentityImpl(
        (extension as KotlinMultiplatformAndroidExtensionImpl).androidTestOnJvmBuilder!!.compilationName.getNamePrefixedWithAndroidTarget()
    )

    override val namespace: Provider<String> by lazy {
        extension.testNamespace?.let { services.provider { it } }
            ?: extension.namespace?.let { services.provider {"$it.test" } }
            ?: mainVariantDslInfo.namespace.map { testedVariantNamespace ->
                "$testedVariantNamespace.test"
            }
    }

    override val isUnitTestCoverageEnabled: Boolean
        get() = testOnJvmConfig.enableCoverage

    override val androidResourcesDsl: AndroidResourcesDslInfo? by lazy {
        if (testOnJvmConfig.isIncludeAndroidResources) {
            object : AndroidResourcesDslInfo {
                override val androidResources = dslServices.newDecoratedInstance(
                    AaptOptions::class.java,
                    dslServices
                )
                override val resourceConfigurations: ImmutableSet<String> = ImmutableSet.of()
                override val vectorDrawables: VectorDrawablesOptions =
                    DefaultVectorDrawablesOptions()
                override val isPseudoLocalesEnabled: Boolean = false
                override val isCrunchPngs: Boolean = false
                override val isCrunchPngsDefault: Boolean = false

                override fun getResValues(): Map<ResValue.Key, ResValue> {
                    return emptyMap()
                }
            }
        } else {
            null
        }
    }
}
