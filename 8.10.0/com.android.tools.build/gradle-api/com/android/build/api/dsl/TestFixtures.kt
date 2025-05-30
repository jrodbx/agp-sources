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

package com.android.build.api.dsl

import org.gradle.api.Incubating

/**
 * DSL object for configuring test fixtures.
 */
@Incubating
interface TestFixtures {

    /**
     * Flag to enable test fixtures.
     *
     * Default value is derived from `android.experimental.enableTestFixtures` which is 'false' by
     * default.
     *
     * You can override the default for this for all projects in your build by adding the line
     *     `android.experimental.enableTestFixtures=true`
     * in the gradle.properties file at the root project of your build.
     */
    var enable: Boolean

    /**
     * Flag to enable Android resource processing in test fixtures.
     *
     * Default value is 'false'.
     */
    var androidResources: Boolean
}
