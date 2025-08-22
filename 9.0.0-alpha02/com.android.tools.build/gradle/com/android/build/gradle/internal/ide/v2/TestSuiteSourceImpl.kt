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

package com.android.build.gradle.internal.ide.v2

import com.android.builder.model.v2.ide.SourceProvider
import com.android.builder.model.v2.models.SourceType
import com.android.builder.model.v2.models.TestSuiteSource
import java.io.File

class TestSuiteSourceImpl private constructor(
    override val name: String,
    override val type: SourceType,
    override val folders: Collection<File>?,
    override val sourceProvider: SourceProvider?
): TestSuiteSource {
    companion object {
        /**
         * Represents test sources that are primarily static asset files (e.g., XML, JSON).
         * These are typically not compiled but are used directly by test runners.
         */
        fun assets(
            name: String,
            sources: Collection<File>
        ) = TestSuiteSourceImpl(
            name = name,
            type = SourceType.ASSETS,
            folders = sources,
            sourceProvider = null,
        )

        /**
         * Represents test sources that are compiled and run on the host machine (JVM).
         * These are typical for unit tests.
         */
        fun hostJar(
            name: String,
            sources: Collection<File>
        ) = TestSuiteSourceImpl(
            name = name,
            type = SourceType.HOST_JAR,
            folders = sources,
            sourceProvider = null,
        )

        /**
         * Represents test sources for tests that run on an Android device or emulator.
         * This includes a full Android source provider structure.
         */
        fun testApk(
            name: String,
            sources: SourceProvider
        ) = TestSuiteSourceImpl(
            name = name,
            type = SourceType.TEST_APK,
            folders = null,
            sourceProvider = sources,
        )
    }
}
