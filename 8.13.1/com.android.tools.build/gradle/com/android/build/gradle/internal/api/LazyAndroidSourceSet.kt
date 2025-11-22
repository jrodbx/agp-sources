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

package com.android.build.gradle.internal.api

import com.android.build.gradle.api.AndroidSourceSet
import org.gradle.api.NamedDomainObjectContainer

class LazyAndroidSourceSet(
    private val sourceSetsContainer: NamedDomainObjectContainer<AndroidSourceSet>,
    private val sourceSetName: String
) {
    private val sourceSet = lazy {
        sourceSetsContainer.maybeCreate(sourceSetName) as DefaultAndroidSourceSet
    }

    fun get(): DefaultAndroidSourceSet {
        return sourceSet.value
    }

    fun isInitialized(): Boolean {
        return sourceSet.isInitialized()
    }
}
