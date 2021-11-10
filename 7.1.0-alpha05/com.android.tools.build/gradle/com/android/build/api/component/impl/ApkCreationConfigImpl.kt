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
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.ProjectOptions

open class ApkCreationConfigImpl(
    override val config: ApkCreationConfig,
    projectOptions: ProjectOptions,
    globalScope: GlobalScope,
    dslInfo: VariantDslInfo<*>
): ConsumableCreationConfigImpl(config, projectOptions, globalScope, dslInfo) {

    val isDebuggable = variantDslInfo.isDebuggable

    override val needsShrinkDesugarLibrary: Boolean
        get() {
            if (!isCoreLibraryDesugaringEnabled(config)) {
                return false
            }
            // Assume Java8LangSupport is either D8 or R8 as we checked that in
            // isCoreLibraryDesugaringEnabled()
            return !(getJava8LangSupportType() == VariantScope.Java8LangSupport.D8 && config.debuggable)
        }

    /**
     * Returns the minimum SDK version for which we want to deploy this variant on.
     * In most cases this will be equal the minSdkVersion, but when the IDE is deploying to a
     * device running a higher API level than the minSdkVersion this will have that value and
     * can be used to enable some optimizations to build the APK faster.
     *
     * This has no relation with targetSdkVersion from build.gradle/manifest.
     */
    override val targetDeployApi: AndroidVersion
        get() {
            val targetDeployApiFromIDE = variantDslInfo.targetDeployApiFromIDE ?: 1
            return if (targetDeployApiFromIDE > config.minSdkVersion.getFeatureLevel()) {
                AndroidVersionImpl(targetDeployApiFromIDE)
            } else {
                config.minSdkVersion
            }
        }
}
