/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.api.extension.impl

import com.android.build.api.dsl.SdkComponents
import com.android.build.api.dsl.TestExtension
import com.android.build.api.instrumentation.manageddevice.ManagedDeviceRegistry
import com.android.build.api.variant.TestAndroidComponentsExtension
import com.android.build.api.variant.TestVariant
import com.android.build.api.variant.TestVariantBuilder
import com.android.build.gradle.internal.services.DslServices

open class TestAndroidComponentsExtensionImpl(
        dslServices: DslServices,
        sdkComponents: SdkComponents,
        managedDeviceRegistry: ManagedDeviceRegistry,
        variantApiOperationsRegistrar: VariantApiOperationsRegistrar<TestExtension, TestVariantBuilder, TestVariant>,
        testExtension: TestExtension
):
        TestAndroidComponentsExtension,
        AndroidComponentsExtensionImpl<TestExtension, TestVariantBuilder, TestVariant>(
            dslServices,
            sdkComponents,
            managedDeviceRegistry,
            variantApiOperationsRegistrar,
            testExtension
        )
