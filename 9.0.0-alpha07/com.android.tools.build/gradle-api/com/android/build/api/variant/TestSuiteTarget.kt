/*
 * Copyright (C) 2025 The Android Open Source Project
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

import org.gradle.api.Incubating
import org.gradle.api.Named

/**
 * Model for running a [TestSuite] in a context (local attached devices, gmd, etc...)
 */
@Incubating
interface TestSuiteTarget: Named {

    @get:Incubating
    val enabled: Boolean

    /**
     * GMD identifier to deploy APKs to. The identifier must be defined in the
     * [com.android.build.api.dsl.ManagedDevices] for this module.
     */
    @get:Incubating
    val targetDevices: Collection<String>
}
