/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.api.variant.impl

import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilation
import com.android.build.api.variant.SourceDirectories
import com.android.build.gradle.internal.services.VariantServices
import org.gradle.api.tasks.util.PatternFilterable

/**
 * Implementation of [SourceDirectories.Flat] that is read-only.
 */
class KotlinMultiplatformFlatSourceDirectoriesImpl(
    name: String,
    variantServices: VariantServices,
    variantDslFilters: PatternFilterable?,
    private val compilation: KotlinMultiplatformAndroidCompilation
): FlatSourceDirectoriesImpl(name, variantServices, variantDslFilters), SourceDirectories.Flat {

    override fun addSource(directoryEntry: DirectoryEntry) {
        throw IllegalAccessException("$name sources for kotlin multiplatform android plugin " +
                "are read-only, to append to the $name sources you need to add your sources to " +
                "the compilation named (${compilation.name}).")
    }

    override fun addStaticSource(directoryEntry: DirectoryEntry) {
        throw IllegalAccessException("$name sources for kotlin multiplatform android plugin " +
                "are read-only, to append to the $name sources you need to add your sources to " +
                "the compilation named (${compilation.name}).")
    }
}
