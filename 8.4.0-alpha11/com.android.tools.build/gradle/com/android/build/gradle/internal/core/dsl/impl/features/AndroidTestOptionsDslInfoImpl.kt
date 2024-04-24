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
import com.android.build.gradle.internal.dsl.CommonExtensionImpl
import com.android.build.gradle.internal.utils.createTargetSdkVersion

internal class AndroidTestOptionsDslInfoImpl(
    private val extension: CommonExtensionImpl<*, *, *, *, *, *>,
): AndroidTestOptionsDslInfo {
    override val animationsDisabled: Boolean
        get() = extension.testOptions.animationsDisabled
    override val execution: String
        get() = extension.testOptions.execution

    override val resultsDir: String?
        get() = extension.testOptions.resultsDir
    override val reportDir: String?
        get() = extension.testOptions.reportDir
    override val managedDevices: ManagedDevices
        get() = extension.testOptions.managedDevices
    override val emulatorControl: EmulatorControl
        get() = extension.testOptions.emulatorControl
    override val emulatorSnapshots: EmulatorSnapshots
        get() = extension.testOptions.emulatorSnapshots
    override val targetSdkVersion: AndroidVersion?
        get() = extension.testOptions.run { createTargetSdkVersion(targetSdk, targetSdkPreview)}
}
