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

package com.android.builder.model.v2.ide

/**
 * Information for a test suite in a variant.
 *
 * This includes JUnit engines configuration, etc.
 *
 * @since 8.12
 */
// TODO : maybe consider subclassing TestInfo
interface TestSuiteTestInfo {
    /**
     * Information for the junit engines configured for running the
     * test suite.
     */
    val junitInfo: JUnitEngineInfo

    /**
     * Information for the test suite's targets.
     *
     * @return map of [TestSuiteTarget] indexed by their [TestSuiteTarget.name]
     */
    val targets: Map<String, TestSuiteTarget>
}
