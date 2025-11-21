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

package com.android.build.gradle.internal.core.dsl.impl.features

import com.android.build.api.variant.AndroidVersion
import com.android.build.gradle.internal.core.dsl.features.UnitTestOptionsDslInfo
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtensionImpl
import com.android.build.gradle.internal.plugins.KotlinMultiplatformAndroidPlugin
import com.android.build.gradle.internal.utils.createTargetSdkVersion
import org.gradle.api.tasks.testing.Test

internal class KmpUnitTestOptionsDslInfoImpl(
    private val extension: KotlinMultiplatformAndroidExtensionImpl,
): UnitTestOptionsDslInfo {

    private val testOnJvmConfig
        get() = extension.androidTestOnJvmOptions ?: throw RuntimeException(
            "Android tests on jvm are not enabled. (use `kotlin.${KotlinMultiplatformAndroidPlugin.ANDROID_EXTENSION_ON_KOTLIN_EXTENSION_NAME}.withAndroidTestOnJvm()` to enable)"
        )

    override val isIncludeAndroidResources: Boolean
        get() = testOnJvmConfig.isIncludeAndroidResources
    override val isReturnDefaultValues: Boolean
        get() = testOnJvmConfig.isReturnDefaultValues

    override val targetSdkVersion: AndroidVersion?
        get() = extension.run { createTargetSdkVersion(compileSdk, compileSdkPreview) }
    override fun applyConfiguration(task: Test) { }
}
