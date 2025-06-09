/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.api.component.impl.ComponentBuilderImpl
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.VariantBuilder
import com.android.build.gradle.internal.core.dsl.VariantDslInfo
import com.android.build.gradle.internal.services.VariantBuilderServices
import com.android.builder.errors.IssueReporter

abstract class VariantBuilderImpl(
    globalVariantBuilderConfig: GlobalVariantBuilderConfig,
    variantDslInfo: VariantDslInfo,
    componentIdentity: ComponentIdentity,
    variantBuilderServices: VariantBuilderServices,
) :
    ComponentBuilderImpl(
        globalVariantBuilderConfig,
        componentIdentity,
        variantBuilderServices
    ),
    VariantBuilder, InternalVariantBuilder {

    /**
     * MinSdkVersion usable in the Variant API
     */
    internal val minSdkVersion: AndroidVersion
        get() = mutableMinSdk.sanitize()

    /** backing property for [minSdk] and [minSdkPreview] */
    private val mutableMinSdk: MutableAndroidVersion = variantDslInfo.minSdkVersion

    override var minSdk: Int?
        get() = mutableMinSdk.api
        set(value) {
            mutableMinSdk.codename = null
            mutableMinSdk.api = value
        }

    override var minSdkPreview: String?
        get() = mutableMinSdk.codename
        set(value) {
            mutableMinSdk.codename = value
            mutableMinSdk.api = null
        }

    /**
     * TargetSdkVersion usable in the Variant API
     */
    internal val targetSdkVersion: AndroidVersion
        get() = mutableTargetSdk?.sanitize() ?: minSdkVersion

    /**
     * backing property for [targetSdk] and [targetSdkPreview]
     * This could be null and will be instantiated on demand in the setter.
     */
    internal var mutableTargetSdk: MutableAndroidVersion? = variantDslInfo.targetSdkVersion

    @Deprecated("Will be removed in v9.0 - Use (variantBuilder as GeneratesApkBuilder).targetSdk")
    override var targetSdk: Int?
        get() {
            val target = mutableTargetSdk
            // we only return minSdk if target is null, no matter what the value of api is
            // (so no using the fancy elvis operator to simplify this)
            return if (target != null) {
                target.api
            } else {
                minSdk
            }
        }
        set(value) {
            val target =
                    mutableTargetSdk ?: MutableAndroidVersion(null, null).also {
                        mutableTargetSdk = it
                    }
            target.codename = null
            target.api = value
        }

    @Deprecated("Will be removed in v9.0 - Use (variantBuilder as GeneratesApkBuilder).targetSdkPreview")
    override var targetSdkPreview: String?
        get() {
            val target = mutableTargetSdk
            // we only return minSdk if target is null, no matter what the value of codename is
            // (so no using the fancy elvis operator to simplify this)
            return if (target != null) {
                target.codename
            } else {
                minSdkPreview
            }
        }
        set(value) {
            val target =
                    mutableTargetSdk ?: MutableAndroidVersion(null, null).also {
                        mutableTargetSdk = it
                    }
            target.codename = value
            target.api = null
        }

    override var maxSdk: Int? = variantDslInfo.maxSdkVersion

    private val renderscriptTargetInDsl =
        variantDslInfo.renderscriptDslInfo?.renderscriptTarget ?: -1

    override var renderscriptTargetApi: Int = -1
        get() {
            // if the field has been set, always use  that value, otherwise, calculate it each
            // time in case minSdkVersion changes.
            if (field != -1) return field
            val targetApi = renderscriptTargetInDsl
            // default to -1 if not in build.gradle file.
            val minSdk = mutableMinSdk.getFeatureLevel()
            return if (targetApi > minSdk) targetApi else minSdk
        }

    // This has to be defined here until the deprecated VariantBuilder APIs below are removed,
    // then it should be moved to the appropriate subtypes.
    abstract val hostTests: Map<String, HostTestBuilder>

    private val unitTest: HostTestBuilder
        get() = hostTests[HostTestBuilder.UNIT_TEST_TYPE]
                ?: throw RuntimeException("Invalid component, no unit test defined")

    @Deprecated("Will be removed in AGP 9.0 - Use (variantBuilder as HasHostTestsBuilder).get(HasHostTestsBuilder.UNIT_TEST_TYPE).enable")
    override var enableUnitTest: Boolean
        get() = unitTest.enable
        set(value) {
            unitTest.enable = value
        }
    @Deprecated("Will be removed in AGP 9.0 - Use (variantBuilder as HasHostTestsBuilder).get(HasHostTestsBuilder.UNIT_TEST_TYPE).enable")
    override var unitTestEnabled: Boolean
        get() = unitTest.enable
        set(value) {
            unitTest.enable = value
        }

    private val registeredExtensionDelegate= lazy {
        mutableMapOf<Class<out Any>, Any>()
    }

    override fun <T: Any> registerExtension(type: Class<out T>, instance: T) {
        registeredExtensionDelegate.value[type] = instance
    }

    fun getRegisteredExtensions(): Map<Class<out Any>, Any>? =
        if (registeredExtensionDelegate.isInitialized())
            registeredExtensionDelegate.value
        else null

    private val hasPostProcessingConfiguration =
        variantDslInfo.optimizationDslInfo.postProcessingOptions.hasPostProcessingConfiguration()

    internal fun setMinificationIfPossible(
        varName: String,
        newValue: Boolean,
        setter: (Boolean) -> Unit
    ) {
        if (hasPostProcessingConfiguration)
            variantBuilderServices.issueReporter.reportWarning(
                IssueReporter.Type.GENERIC,
                "You cannot set $varName via Variant API as build uses postprocessing{...} " +
                        "instead of buildTypes{...}"
            )
        else
            setter(newValue)
    }
}
