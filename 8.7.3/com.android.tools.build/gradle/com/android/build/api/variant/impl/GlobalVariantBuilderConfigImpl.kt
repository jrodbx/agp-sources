/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.DependenciesInfo
import com.android.build.gradle.internal.core.dsl.features.DeviceTestOptionsDslInfo
import com.android.build.gradle.internal.core.dsl.impl.features.DeviceTestOptionsDslInfoImpl

class GlobalVariantBuilderConfigImpl(
    private val extension: CommonExtension<*, *, *, *, *, *>
) : GlobalVariantBuilderConfig {

    override val dependenciesInfo: DependenciesInfo
        get() = (extension as? ApplicationExtension)?.dependenciesInfo
            ?: throw RuntimeException("Access to dependenciesInfo on a non Application variant")

    override val deviceTestOptions: DeviceTestOptionsDslInfo
        get() = DeviceTestOptionsDslInfoImpl(extension)
}
