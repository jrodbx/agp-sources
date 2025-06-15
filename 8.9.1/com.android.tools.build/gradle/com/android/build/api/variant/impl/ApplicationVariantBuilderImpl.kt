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

import com.android.build.api.component.analytics.AnalyticsEnabledApplicationVariantBuilder
import com.android.build.api.variant.AndroidTestBuilder
import com.android.build.api.variant.ApplicationAndroidResourcesBuilder
import com.android.build.api.variant.ApplicationVariantBuilder
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.DependenciesInfoBuilder
import com.android.build.api.variant.DeviceTestBuilder
import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.PropertyAccessNotAllowedException
import com.android.build.api.variant.VariantBuilder
import com.android.build.gradle.internal.core.dsl.ApplicationVariantDslInfo
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.VariantBuilderServices
import com.android.builder.errors.IssueReporter
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import javax.inject.Inject

open class ApplicationVariantBuilderImpl @Inject constructor(
    globalVariantBuilderConfig: GlobalVariantBuilderConfig,
    dslInfo: ApplicationVariantDslInfo,
    componentIdentity: ComponentIdentity,
    variantBuilderServices: VariantBuilderServices
) : VariantBuilderImpl(
    globalVariantBuilderConfig,
    dslInfo,
    componentIdentity,
    variantBuilderServices
), ApplicationVariantBuilder {

    override val debuggable: Boolean =
        (dslInfo as? ApplicationVariantDslInfo)?.isDebuggable ?: false

    override var androidTestEnabled: Boolean
        get() = androidTest.enable
        set(value) {
            androidTest.enable = value
        }

    override var enableAndroidTest: Boolean
        get() = androidTest.enable
        set(value) {
            androidTest.enable = value
        }

    internal var _profileable = dslInfo.isProfileable

    override var profileable: Boolean
        get() = throw PropertyAccessNotAllowedException("profileable", "ApplicationVariantBuilder")
        set(value) {
            if (debuggable && value) {
                val message =
                    "Variant '$name' can only have debuggable or profileable enabled.\n" +
                            "Only one of these options can be used at a time.\n" +
                            "Recommended action: Only set one of profileable=true via variant API \n" +
                            "or debuggable=true via DSL"
                variantBuilderServices.issueReporter.reportWarning(
                    IssueReporter.Type.GENERIC,
                    message
                )
            } else {
                _profileable = value
            }
        }

    override var enableTestFixtures: Boolean = dslInfo.testFixtures?.enable ?: false

    // only instantiate this if this is needed. This allows non-built variant to not do too much work.
    override val dependenciesInfo: DependenciesInfoBuilder by lazy {
        variantBuilderServices.newInstance(
            DependenciesInfoBuilderImpl::class.java,
            variantBuilderServices,
            globalVariantBuilderConfig.dependenciesInfo
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T: VariantBuilder> createUserVisibleVariantObject(
        projectServices: ProjectServices,
        stats: GradleBuildVariant.Builder?,
    ): T =
        if (stats == null) {
            this as T
        } else {
            projectServices.objectFactory.newInstance(
                AnalyticsEnabledApplicationVariantBuilder::class.java,
                this,
                stats
            ) as T
        }

    override var isMinifyEnabled: Boolean =
        dslInfo.optimizationDslInfo.postProcessingOptions.codeShrinkerEnabled()
        set(value) = setMinificationIfPossible("minifyEnabled", value) { field = it }

    override var shrinkResources: Boolean =
        dslInfo.optimizationDslInfo.postProcessingOptions.resourcesShrinkingEnabled()
        set(value) = setMinificationIfPossible("shrinkResources", value) { field = it }

    internal var _enableMultiDex = dslInfo.dexingDslInfo.isMultiDexEnabled
    override var enableMultiDex: Boolean?
        get() {
            throw PropertyAccessNotAllowedException("enableMultiDex", "ApplicationVariantBuilder")
        }
        set(value) {
            _enableMultiDex = value
        }


    override val deviceTests: Map<String, DeviceTestBuilderImpl> =
        DeviceTestBuilderImpl.create(
            dslInfo.dslDefinedDeviceTests,
            variantBuilderServices,
            globalVariantBuilderConfig,
            { targetSdkVersion },
            _enableMultiDex,
            debuggable,
        )

    override val androidTest: AndroidTestBuilder by lazy(LazyThreadSafetyMode.NONE) {
        val androidTest = deviceTests.get(DeviceTestBuilder.ANDROID_TEST_TYPE)

        if (androidTest != null) {
            AndroidTestBuilderImpl(androidTest)
        } else {
            variantBuilderServices.issueReporter.reportWarning(
                IssueReporter.Type.GENERIC,
                "androidTest test suite not defined for this variant : $name"
            )
            // return an empty shell during sync, otherwise an exception will be generated.
            object: AndroidTestBuilder {
                override var enable: Boolean
                    get() = false
                    set(value) {}
                override var enableMultiDex: Boolean?
                    get() = false
                    set(value) {}
                override var enableCodeCoverage: Boolean
                    get() = false
                    set(value) {}
                override var targetSdk: Int?
                    get() = null
                    set(value) {}
                override var targetSdkPreview: String?
                    get() = null
                    set(value) {}
                override var debuggable: Boolean
                    get() = false
                    set(value) {}
            }
        }
    }

    override val androidResources: ApplicationAndroidResourcesBuilder =
        ApplicationAndroidResourcesBuilderImpl(dslInfo.generateLocaleConfig)

    override val hostTests: Map<String, HostTestBuilder> =
        HostTestBuilderImpl.create(
            dslInfo.dslDefinedHostTests,
            variantBuilderServices,
            dslInfo.experimentalProperties,
        )
}
