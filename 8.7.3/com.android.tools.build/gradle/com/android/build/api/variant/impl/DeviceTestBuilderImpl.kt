/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.api.variant.impl

import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.DeviceTestBuilder
import com.android.build.api.variant.PropertyAccessNotAllowedException
import com.android.build.gradle.internal.core.dsl.ComponentDslInfo.DslDefinedDeviceTest
import com.android.build.gradle.internal.services.VariantBuilderServices
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.ComponentType
import com.android.builder.core.ComponentTypeImpl

open class DeviceTestBuilderImpl(
    variantBuilderServices: VariantBuilderServices,
    private val globalVariantBuilderConfig: GlobalVariantBuilderConfig,
    private val defaultTargetSdkVersionProvider: () -> AndroidVersion,
    val componentType: ComponentType,
    enableMultiDex: Boolean?,
    enableCodeCoverage: Boolean,
): DeviceTestBuilder {

    companion object {
        fun create(
            dslDefinedDeviceTests: List<DslDefinedDeviceTest>,
            variantBuilderServices: VariantBuilderServices,
            globalVariantBuilderConfig: GlobalVariantBuilderConfig,
            defaultTargetSdkVersionProvider: () -> AndroidVersion,
            enableMultiDex: Boolean?,
        ): Map<String, DeviceTestBuilderImpl> =
            dslDefinedDeviceTests.associate { dslDeviceTest ->
                dslDeviceTest.type to
                    if (dslDeviceTest.type == DeviceTestBuilder.ANDROID_TEST_TYPE) {
                        DeviceTestBuilderImpl(
                            variantBuilderServices,
                            globalVariantBuilderConfig,
                            defaultTargetSdkVersionProvider,
                            ComponentTypeImpl.ANDROID_TEST,
                            enableMultiDex,
                            dslDeviceTest.codeCoverageEnabled,
                        )
                    } else {
                        throw RuntimeException("Unknown device test type : ${dslDeviceTest.type}")
                    }
            }
    }

    // target sdk version to be used in the Variant API
    internal val targetSdkVersion: AndroidVersion
        get() = mutableTargetSdk?.sanitize()
            ?: globalVariantBuilderConfig.deviceTestOptions.targetSdkVersion
            ?: defaultTargetSdkVersionProvider()

    // backing property for [targetSdk] and [targetSdkPreview]
    private var mutableTargetSdk: MutableAndroidVersion? = null

    override var targetSdk: Int?
        get() {
            return mutableTargetSdk?.api
        }
        set(value) {
            val target =
                mutableTargetSdk ?: MutableAndroidVersion(null, null).also {
                    mutableTargetSdk = it
                }
            target.codename = null
            target.api = value
        }

    override var targetSdkPreview: String?
        get() {
            return mutableTargetSdk?.codename
        }
        set(value) {
            val target =
                mutableTargetSdk ?: MutableAndroidVersion(null, null).also {
                    mutableTargetSdk = it
                }
            target.codename = value
            target.api = null
        }

    override var enable = !variantBuilderServices.projectOptions[BooleanOption.ENABLE_NEW_TEST_DSL]

    internal var _enableMultiDex: Boolean? = enableMultiDex
    override var enableMultiDex: Boolean?
        get() =  throw PropertyAccessNotAllowedException("enableMultiDex", "DeviceTestBuilder")
        set(value) {
            _enableMultiDex = value
        }

    internal var _enableCoverage: Boolean = enableCodeCoverage

    override var enableCodeCoverage: Boolean
        get() = throw PropertyAccessNotAllowedException("enableCodeCoverage", "DeviceTestBuilder")
        set(value) {
            _enableCoverage = value
        }
}
