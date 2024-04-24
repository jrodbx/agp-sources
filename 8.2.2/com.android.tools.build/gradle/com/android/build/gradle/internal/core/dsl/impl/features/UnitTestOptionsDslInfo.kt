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
import com.android.build.gradle.internal.dsl.CommonExtensionImpl
import com.android.build.gradle.internal.utils.createTargetSdkVersion
import org.gradle.api.tasks.testing.Test

internal class UnitTestOptionsDslInfoImpl(
    private val extension: CommonExtensionImpl<*, *, *, *, *>,
): UnitTestOptionsDslInfo {
    override val isIncludeAndroidResources: Boolean
        get() = extension.testOptions.unitTests.isIncludeAndroidResources
    override val isReturnDefaultValues: Boolean
        get() = extension.testOptions.unitTests.isReturnDefaultValues
    override fun applyConfiguration(task: Test) {
        extension.testOptions.unitTests.applyConfiguration(task)
    }
    override val targetSdkVersion: AndroidVersion?
        get() = extension.testOptions.run { createTargetSdkVersion(targetSdk, targetSdkPreview) }
}
