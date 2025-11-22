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

import com.android.builder.model.v2.ide.SourceProvider
import java.io.File
import java.io.Serializable

/**
 * Represents the source files for a specific type of test suite.
 */
interface TestSuiteSource: Serializable {

    /**
     * name of the test suite source as defined by the user in the AgpTestSuite DSL.
     */
    val name: String

    val type: SourceType

    /**
     * Returns a collection of [File] when the test suite source is either
     * [SourceType.HOST_JAR] or [SourceType.ASSETS], it will be null otherwise.
     */
    val folders: Collection<File>?

    /**
     * Returns the [SourceProvider] when dealing with an Android source-set
     * when source type is [SourceType.TEST_APK], it will be null otherwise.
     */
    val sourceProvider: SourceProvider?
}
