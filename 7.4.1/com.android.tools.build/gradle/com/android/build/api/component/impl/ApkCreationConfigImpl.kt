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
package com.android.build.api.component.impl

import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.impl.AndroidVersionImpl
import com.android.build.api.variant.impl.getFeatureLevel
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.core.dsl.ApkProducingComponentDslInfo
import com.android.build.gradle.internal.scope.Java8LangSupport
import com.android.sdklib.AndroidVersion.VersionCodes
import kotlin.math.max

open class ApkCreationConfigImpl<T: ApkCreationConfig>(
    config: T,
    override val dslInfo: ApkProducingComponentDslInfo
): ConsumableCreationConfigImpl<T>(config, dslInfo) {

    val isDebuggable: Boolean
        get() = dslInfo.isDebuggable

    override val needsShrinkDesugarLibrary: Boolean
        get() {
            if (!isCoreLibraryDesugaringEnabled(config)) {
                return false
            }
            // Assume Java8LangSupport is either D8 or R8 as we checked that in
            // isCoreLibraryDesugaringEnabled()
            return !(getJava8LangSupportType() == Java8LangSupport.D8 && config.debuggable)
        }

    /**
     * Returns the minimum SDK version which we want to use for dexing.
     * In most cases this will be equal the minSdkVersion, but when the IDE is deploying to:
     * - device running API 24+, the min sdk version for dexing is max(24, minSdkVersion)
     * - device running API 23-, the min sdk version for dexing is minSdkVersion
     * - there is no device, the min sdk version for dexing is minSdkVersion
     * It is used to enable some optimizations to build the APK faster.
     *
     * This has no relation with targetSdkVersion from build.gradle/manifest.
     */
    override val minSdkVersionForDexing: AndroidVersion
        get() {
            val targetDeployApiFromIDE = dslInfo.targetDeployApiFromIDE ?: 1

            val minForDexing = if (targetDeployApiFromIDE >= VersionCodes.N) {
                    max(24, config.minSdkVersion.getFeatureLevel())
                } else {
                    config.minSdkVersion.getFeatureLevel()
                }
            return AndroidVersionImpl(minForDexing)
        }
}
