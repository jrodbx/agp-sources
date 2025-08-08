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

package com.android.build.gradle.internal.api

import com.android.build.api.variant.SourceDirectories
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.api.variant.impl.FileBasedDirectoryEntryImpl
import java.io.File

/**
 * Implementation of [TestSuiteSourceSet] for a single folder under src/test.
 *
 * @param sourceSetName the name of the folder under src/test
 */
internal abstract class AssetsOrHostJarTestSuiteSourceSet(
    private val sourceSetName: String,
    private val variantServices: VariantServices,
) {

    private val testSuiteSourcesFolder = FlatSourceDirectoriesImpl(
        sourceSetName,
        variantServices,
        null,
    ).also {
        it.addSource(FileBasedDirectoryEntryImpl(
            name = sourceSetName,
            directory = File(variantServices.projectInfo.projectDirectory.asFile, "src/$sourceSetName"),
            filter = null,
            isUserAdded = false,
            shouldBeAddedToIdeModel = true
        ))
    }


    fun get(): SourceDirectories.Flat = testSuiteSourcesFolder
}
