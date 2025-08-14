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

package com.android.build.api.variant

import com.android.build.api.AndroidPluginVersion
import com.android.build.api.dsl.SdkComponents
import com.android.build.api.instrumentation.manageddevice.ManagedDeviceRegistry
import org.gradle.api.Incubating

interface AndroidComponents {
    /**
     * The version of the Android Gradle Plugin currently in use.
     */
    val pluginVersion: AndroidPluginVersion

    /**
     * Provides access to underlying Android SDK and build-tools components like adb.
     *
     * @return [SdkComponents] to access Android SDK used by Gradle.
     */
    val sdkComponents: SdkComponents

    /**
     * Provides access to Managed Device Registry to be able to register Custom Managed
     * Device Types.
     *
     * @return [ManagedDeviceRegistry] to register Custom Managed Devices.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get:Incubating
    val managedDeviceRegistry: ManagedDeviceRegistry
}
