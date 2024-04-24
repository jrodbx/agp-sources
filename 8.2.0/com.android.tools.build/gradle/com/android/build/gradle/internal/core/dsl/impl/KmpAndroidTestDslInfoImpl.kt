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
import com.android.build.api.variant.ResValue
import com.android.build.gradle.internal.core.dsl.AndroidTestComponentDslInfo
import com.android.build.gradle.internal.core.dsl.KmpComponentDslInfo
import com.android.build.gradle.internal.core.dsl.KmpVariantDslInfo
import com.android.build.gradle.internal.core.dsl.features.AndroidResourcesDslInfo
import com.android.build.gradle.internal.core.dsl.features.BuildConfigDslInfo
import com.android.build.gradle.internal.core.dsl.features.DexingDslInfo
import com.android.build.gradle.internal.core.dsl.features.ManifestPlaceholdersDslInfo
import com.android.build.gradle.internal.core.dsl.features.OptimizationDslInfo
import com.android.build.gradle.internal.core.dsl.features.RenderscriptDslInfo
import com.android.build.gradle.internal.core.dsl.features.ShadersDslInfo
import com.android.build.gradle.internal.dsl.AaptOptions
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtensionImpl
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.manifest.ManifestDataProvider
import com.android.build.gradle.internal.plugins.KotlinMultiplatformAndroidPlugin.Companion.getNamePrefixedWithAndroidTarget
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.builder.core.ComponentTypeImpl
import com.android.builder.core.DefaultVectorDrawablesOptions
import com.android.builder.dexing.DexingType
import com.android.builder.dexing.isLegacyMultiDexMode
import com.android.builder.model.VectorDrawablesOptions
import com.google.common.collect.ImmutableSet
import org.gradle.api.provider.Provider
import java.io.File

class KmpAndroidTestDslInfoImpl(
    extension: KotlinMultiplatformAndroidExtension,
    services: VariantServices,
    private val dataProvider: ManifestDataProvider,
    override val mainVariantDslInfo: KmpVariantDslInfo,
    signingConfigOverride: SigningConfig?,
    dslServices: DslServices,
    withJava: Boolean,
): KmpComponentDslInfoImpl(
    extension, services, withJava
), AndroidTestComponentDslInfo, KmpComponentDslInfo {

    private val testOnDeviceConfig = (extension as KotlinMultiplatformAndroidExtensionImpl).androidTestOnDeviceOptions!!

    override val componentType = ComponentTypeImpl.ANDROID_TEST
    override val componentIdentity = ComponentIdentityImpl(
        (extension as KotlinMultiplatformAndroidExtensionImpl).androidTestOnDeviceBuilder!!.compilationName.getNamePrefixedWithAndroidTarget()
    )

    override val namespace: Provider<String> by lazy {
        extension.testNamespace?.let { services.provider { it } }
            ?: extension.namespace?.let { services.provider {"$it.test" } }
            ?: mainVariantDslInfo.namespace.map { testedVariantNamespace ->
                "$testedVariantNamespace.test"
            }
    }

    override val isDebuggable: Boolean
        get() = true

    override val signingConfig: SigningConfig by lazy {
        val dslSigningConfig =
            (extension as KotlinMultiplatformAndroidExtensionImpl).signingConfig
        signingConfigOverride?.let {
            // use enableV1 and enableV2 from the DSL if the override values are null
            if (it.enableV1Signing == null) {
                it.enableV1Signing = dslSigningConfig.enableV1Signing
            }
            if (it.enableV2Signing == null) {
                it.enableV2Signing = dslSigningConfig.enableV2Signing
            }
            // use enableV3 and enableV4 from the DSL because they're not injectable
            it.enableV3Signing = dslSigningConfig.enableV3Signing
            it.enableV4Signing = dslSigningConfig.enableV4Signing
            it
        } ?: dslSigningConfig
    }
    override val isSigningReady: Boolean
        get() = signingConfig.isSigningReady

    override val androidResourcesDsl = object: AndroidResourcesDslInfo {
        override val androidResources = dslServices.newDecoratedInstance(
            AaptOptions::class.java,
            dslServices
        )
        override val resourceConfigurations: ImmutableSet<String> = ImmutableSet.of()
        override val vectorDrawables: VectorDrawablesOptions = DefaultVectorDrawablesOptions()
        override val isPseudoLocalesEnabled: Boolean = false
        override val isCrunchPngs: Boolean = false
        override val isCrunchPngsDefault: Boolean = false

        override fun getResValues(): Map<ResValue.Key, ResValue> {
            return emptyMap()
        }
    }
    override val optimizationDslInfo: OptimizationDslInfo
        get() = mainVariantDslInfo.optimizationDslInfo

    override val dexingDslInfo = object: DexingDslInfo {
        override val isMultiDexEnabled =
            testOnDeviceConfig.multidex.enable.takeIf { testOnDeviceConfig.multidex.enableSet }
        override val multiDexKeepProguard: File? =
            testOnDeviceConfig.multidex.mainDexKeepRules.files.getOrNull(0)
        override val multiDexKeepFile: File? = null
    }

    override val isAndroidTestCoverageEnabled: Boolean
        get() = testOnDeviceConfig.enableCoverage

    override fun getInstrumentationRunner(dexingType: DexingType): Provider<String> {
        // first check whether the DSL has the info
        return testOnDeviceConfig.instrumentationRunner?.let {
            services.provider { it }
        } // else return the value from the Manifest
            ?: dataProvider.manifestData.map {
                it.instrumentationRunner
                    ?: if (dexingType.isLegacyMultiDexMode()) {
                        MULTIDEX_TEST_RUNNER
                    } else {
                        DEFAULT_TEST_RUNNER
                    }
            }
    }

    override val instrumentationRunnerArguments: Map<String, String>
        get() = testOnDeviceConfig.instrumentationRunnerArguments
    override val handleProfiling: Provider<Boolean>
        get() =
            // first check whether the DSL has the info
            testOnDeviceConfig.handleProfiling?.let {
                services.provider { it }
            } // else return the value from the Manifest
                ?: dataProvider.manifestData.map { it.handleProfiling ?: DEFAULT_HANDLE_PROFILING }

    override val functionalTest: Provider<Boolean>
        get() =
            // first check whether the DSL has the info
            testOnDeviceConfig.functionalTest?.let {
                services.provider { it }
            } // else return the value from the Manifest
                ?: dataProvider.manifestData.map { it.functionalTest ?: DEFAULT_FUNCTIONAL_TEST }

    override val testLabel: Provider<String?>
        get() {
            // there is actually no DSL value for this.
            return dataProvider.manifestData.map { it.testLabel }
        }

    // Unsupported features
    // TODO(b/243387425): Figure out if we should have a full instrumented test component similar
    //  to libs
    override val shadersDslInfo: ShadersDslInfo? = null
    override val buildConfigDslInfo: BuildConfigDslInfo? = null
    override val renderscriptDslInfo: RenderscriptDslInfo? = null
    override val manifestPlaceholdersDslInfo: ManifestPlaceholdersDslInfo? = null
}
