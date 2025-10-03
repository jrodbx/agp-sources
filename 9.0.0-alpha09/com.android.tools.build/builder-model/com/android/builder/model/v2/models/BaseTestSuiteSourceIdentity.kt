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

package com.android.builder.model.v2.models

/**
 * Parent interface for all test suite source dependencies in model.
 */
interface BaseTestSuiteSourceIdentity {

    /**
     * Type of source for this test suite and dependencies.
     * For example: [SourceType.ASSETS], [SourceType.HOST_JAR], [SourceType.TEST_APK].
     */
    val type: SourceType

    /**
     * The name of this source dependency set. This is used to identify the dependencies associated
     * with a specific source type within the test suite.
     * For example, for [SourceType.ASSETS], this might be "assets".
     */
    val name: String
}
