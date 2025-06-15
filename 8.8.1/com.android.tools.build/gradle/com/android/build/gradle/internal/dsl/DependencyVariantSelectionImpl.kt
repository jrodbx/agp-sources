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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.DependencyVariantSelection
import com.android.build.gradle.internal.services.DslServices
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import javax.inject.Inject

abstract class DependencyVariantSelectionImpl@Inject constructor(
    dslServices: DslServices,
    objectFactory: ObjectFactory
): DependencyVariantSelection {
    // In kmp, there is only a single variant so when a kmp library consumes android library which
    // typically expose multiple build type variants (debug, release, etc) we want to have a default
    // build type to consumer (unless specified directly through the DSL). We use Gradle Property's
    // convention to do this
    override val buildTypes: ListProperty<String> = objectFactory.listProperty(String::class.java).also {
        it.convention(listOf("debug"))
        it.finalizeValueOnRead()
    }
}
