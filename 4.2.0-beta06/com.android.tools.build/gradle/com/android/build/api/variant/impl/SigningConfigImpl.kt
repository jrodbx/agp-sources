/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.build.api.variant.SigningConfig
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import com.android.build.gradle.internal.signing.SigningConfigVersions
import java.util.concurrent.Callable

class SigningConfigImpl(
    dslSigningConfig: com.android.build.gradle.internal.dsl.SigningConfig?,
    variantPropertiesApiServices: VariantPropertiesApiServices,
    minSdk: Int,
    targetApi: Int?
) : SigningConfig {

    override val enableV4Signing =
        variantPropertiesApiServices.propertyOf(
            Boolean::class.java,
            when {
                // Don't sign with v4 if we're installing on a device that doesn't support it.
                targetApi != null && targetApi < SigningConfigVersions.MIN_V4_SDK -> false
                // Otherwise, sign with v4 if it's enabled explicitly.
                else -> dslSigningConfig?.enableV4Signing ?: false
            },
            "enableV4Signing"
        )

    override val enableV3Signing =
        variantPropertiesApiServices.propertyOf(
            Boolean::class.java,
            when {
                // Don't sign with v3 if we're installing on a device that doesn't support it.
                targetApi != null && targetApi < SigningConfigVersions.MIN_V3_SDK -> false
                // Otherwise, sign with v3 if it's enabled explicitly.
                else -> dslSigningConfig?.enableV3Signing ?: false
            },
            "enableV3Signing"
        )

    override val enableV2Signing =
        variantPropertiesApiServices.propertyOf(
            Boolean::class.java,
            Callable {
                val enableV2Signing = dslSigningConfig?.enableV2Signing
                val effectiveMinSdk = targetApi ?: minSdk
                return@Callable when {
                    // Don't sign with v2 if we're installing on a device that doesn't support it.
                    targetApi != null && targetApi < SigningConfigVersions.MIN_V2_SDK -> false
                    // Otherwise, sign with v2 if it's enabled explicitly.
                    enableV2Signing != null -> enableV2Signing
                    // We sign with v2 if minSdk < MIN_V3_SDK, even if we're also signing with v1,
                    // because v2 signatures can be verified faster than v1 signatures.
                    effectiveMinSdk < SigningConfigVersions.MIN_V3_SDK -> true
                    // If minSdk >= MIN_V3_SDK, sign with v2 only if we're not signing with v3.
                    else -> !enableV3Signing.get()
                }
            },
            "enableV2Signing"
        )

    override val enableV1Signing =
        variantPropertiesApiServices.propertyOf(
            Boolean::class.java,
            Callable {
                val enableV1Signing = dslSigningConfig?.enableV1Signing
                val effectiveMinSdk = targetApi ?: minSdk
                return@Callable when {
                    // Sign with v1 if it's enabled explicitly.
                    enableV1Signing != null -> enableV1Signing
                    // We need v1 if minSdk < MIN_V2_SDK.
                    effectiveMinSdk < SigningConfigVersions.MIN_V2_SDK -> true
                    // We don't need v1 if minSdk >= MIN_V2_SDK and we're signing with v2.
                    enableV2Signing.get() -> false
                    // We need v1 if we're not signing with v2 and minSdk < MIN_V3_SDK.
                    effectiveMinSdk < SigningConfigVersions.MIN_V3_SDK -> true
                    // If minSdk >= MIN_V3_SDK, sign with v1 only if we're not signing with v3.
                    else -> !enableV3Signing.get()
                }
            },
            "enableV1Signing"
        )
}
