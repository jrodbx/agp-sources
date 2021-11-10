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
     * Returns the minimum SDK version for this variant, potentially overridden by a property passed
     * by the IDE.
     *
     * @see .getMinSdkVersion
     */
    override val minSdkVersionWithTargetDeviceApi: AndroidVersion
        get() {
            val targetApiLevel = variantDslInfo.minSdkVersionFromIDE
            return if (targetApiLevel != null && config.isMultiDexEnabled && isDebuggable) {
                // Consider runtime API passed from the IDE only if multi-dex is enabled and the app is
                // debuggable.
                val minVersion: Int =
                        if (config.targetSdkVersion.apiLevel > 1) Integer.min(
                                config.targetSdkVersion.apiLevel,
                                targetApiLevel
                        ) else targetApiLevel
                AndroidVersionImpl(minVersion)
            } else {
                config.minSdkVersion
            }
        }
}
