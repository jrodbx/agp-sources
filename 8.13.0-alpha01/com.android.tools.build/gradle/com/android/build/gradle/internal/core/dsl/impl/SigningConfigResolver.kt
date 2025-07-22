/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.internal.core.MergedFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.services.BaseServices
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.BuilderConstants

class SigningConfigResolver(
    val dslSigningConfig: SigningConfig?,
    val signingConfigOverride: SigningConfig?,
    val debugSigningConfig: SigningConfig?,
    val services: BaseServices
) {
    companion object {
        fun create(
            buildType: BuildType,
            mergedFlavor: MergedFlavor,
            signingConfigOverride: SigningConfig?,
            extension: CommonExtension<*, *, *, *, *, *>,
            services: BaseServices
        ):SigningConfigResolver {
            val dslSigningConfig = (buildType as? ApplicationBuildType)?.signingConfig
                ?: mergedFlavor.signingConfig

            val singingConfigOverride = signingConfigOverride?.let {
                // use enableV1 and enableV2 from the DSL if the override values are null
                if (it.enableV1Signing == null) {
                    it.enableV1Signing = dslSigningConfig?.enableV1Signing
                }
                if (it.enableV2Signing == null) {
                    it.enableV2Signing = dslSigningConfig?.enableV2Signing
                }
                // use enableV3 and enableV4 from the DSL because they're not injectable
                it.enableV3Signing = dslSigningConfig?.enableV3Signing
                it.enableV4Signing = dslSigningConfig?.enableV4Signing
                it
            }
            val debugConfig = extension.signingConfigs.findByName(BuilderConstants.DEBUG) as SigningConfig?
            return SigningConfigResolver(dslSigningConfig as SigningConfig?, singingConfigOverride, debugConfig, services)
        }
    }

    fun resolveConfig(profileable: Boolean?, debuggable: Boolean?): SigningConfig? {
        return signingConfigOverride
            ?: if (services.projectOptions[BooleanOption.ENABLE_DEFAULT_DEBUG_SIGNING_CONFIG] &&
                dslSigningConfig == null &&
                (profileable == true || debuggable == true)
            ) {
                return debugSigningConfig
            } else dslSigningConfig
    }

}
