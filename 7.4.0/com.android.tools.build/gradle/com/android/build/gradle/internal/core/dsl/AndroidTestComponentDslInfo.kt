/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.core.dsl

import org.gradle.api.provider.Provider

/**
 * Represents the dsl info for an android test component, initialized from the DSL object model
 * (extension, default config, build type, flavors)
 *
 * This class allows querying for the values set via the DSL model.
 *
 * Use [DslInfoBuilder] to instantiate.
 *
 * @see [com.android.build.gradle.internal.component.AndroidTestCreationConfig]
 */
interface AndroidTestComponentDslInfo
    : TestComponentDslInfo, ApkProducingComponentDslInfo, InstrumentedTestComponentDslInfo {
    /**
     * The namespace for the R class for AndroidTest.
     *
     * This is a special case due to legacy reasons.
     *
     *  TODO(b/176931684) Remove and use [namespace] after we stop supporting using applicationId
     *   to namespace the test component R class.
     */
    val namespaceForR: Provider<String>
}
