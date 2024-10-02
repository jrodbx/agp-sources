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

import com.android.build.api.dsl.EmulatorControl
import com.android.build.api.dsl.EmulatorSnapshots
import com.android.build.api.dsl.ManagedDevices
import com.android.build.api.variant.AndroidVersion
import com.android.build.gradle.internal.core.dsl.features.AndroidTestOptionsDslInfo
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtensionImpl
import com.android.build.gradle.internal.plugins.KotlinMultiplatformAndroidPlugin
import com.android.build.gradle.internal.utils.createTargetSdkVersion

internal class KmpAndroidTestOptionsDslInfoImpl(
    private val extension: KotlinMultiplatformAndroidExtensionImpl,
): AndroidTestOptionsDslInfo {
    private val testOnDeviceConfig
        get() = extension.androidTestOnDeviceOptions ?: throw RuntimeException(
            "Android tests on device are not enabled. (use `kotlin.${KotlinMultiplatformAndroidPlugin.ANDROID_EXTENSION_ON_KOTLIN_EXTENSION_NAME}.withAndroidTestOnDevice()` to enable)"
        )
    override val animationsDisabled: Boolean
        get() = testOnDeviceConfig.animationsDisabled
    override val execution: String
        get() = testOnDeviceConfig.execution

    override val resultsDir: String?
        get() = null
    override val reportDir: String?
        get() = null
    override val managedDevices: ManagedDevices
        get() = testOnDeviceConfig.managedDevices
    override val emulatorControl: EmulatorControl
        get() = testOnDeviceConfig.emulatorControl
    override val emulatorSnapshots: EmulatorSnapshots
        get() = testOnDeviceConfig.emulatorSnapshots
    override val targetSdkVersion: AndroidVersion?
        get() = extension.run { createTargetSdkVersion(compileSdk, compileSdkPreview) }
}
