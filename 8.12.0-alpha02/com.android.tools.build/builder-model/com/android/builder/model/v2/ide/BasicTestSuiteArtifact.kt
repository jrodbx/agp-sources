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

import com.android.builder.model.v2.AndroidModel
import java.io.File

/**
 * Basic information about a test suite.
 */
interface BasicTestSuiteArtifact: AndroidModel {

    /**
     * The test suite source file directories
     *
     * TODO: Clearly this single source directory will only work for simple test suites like unit
     * tests and journeys test. However, for more complicated test suites like device tests, a
     * SourceProvider will be more appropriate. We need to figure out how to model that but that's
     * really starting from the DSL definition so I am not resolving this here for now.
     */
    val sources: Set<File>
}
