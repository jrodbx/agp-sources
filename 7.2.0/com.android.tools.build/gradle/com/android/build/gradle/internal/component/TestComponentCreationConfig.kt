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

package com.android.build.gradle.internal.component

import org.gradle.api.provider.Provider

/**
 * Internal interface for all test components
 */
interface TestComponentCreationConfig: TestCreationConfig {

    override val testedConfig: VariantCreationConfig

    /**
     * TODO(b/176931684) Remove this and use [namespace] instead after we stop supporting using
     *  applicationId to namespace the test component R class.
     */
    val namespaceForR: Provider<String>
}
