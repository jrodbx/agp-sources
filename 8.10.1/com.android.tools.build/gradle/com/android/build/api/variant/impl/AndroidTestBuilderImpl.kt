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

package com.android.build.api.variant.impl

import com.android.build.api.variant.PropertyAccessNotAllowedException

@Suppress("DEPRECATION")
class AndroidTestBuilderImpl(
    private val deviceTestBuilder: DeviceTestBuilderImpl,
): com.android.build.api.variant.AndroidTestBuilder {

    @Deprecated("replaced with DeviceTestBuilder.enable")
    override var enable: Boolean
        get() = deviceTestBuilder.enable
        set(value) {
            deviceTestBuilder.enable = value
        }

    @Deprecated("replaced with DeviceTestBuilder.setEnableMultiDex")
    override var enableMultiDex: Boolean?
        get() = throw PropertyAccessNotAllowedException("enableMultiDex", "AndroidTestBuilder")
        set(value) {
            deviceTestBuilder.enableMultiDex = value
        }

    override var enableCodeCoverage: Boolean
        get() = throw PropertyAccessNotAllowedException("enableCodeCoverage", "AndroidTestBuilder")
        set(value) {
            deviceTestBuilder.enableCodeCoverage = value
        }
    override var targetSdk: Int?
        get() = deviceTestBuilder.targetSdk
        set(value) {
            deviceTestBuilder.targetSdk = value
        }
    override var targetSdkPreview: String?
        get() = deviceTestBuilder.targetSdkPreview
        set(value) {
            deviceTestBuilder.targetSdkPreview = value
        }
    override var debuggable: Boolean
        get() = deviceTestBuilder.debuggable
        set(value) {
            deviceTestBuilder.debuggable = value
        }
}
