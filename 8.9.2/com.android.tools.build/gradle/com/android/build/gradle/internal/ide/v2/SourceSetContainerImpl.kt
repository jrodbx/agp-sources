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

package com.android.build.gradle.internal.ide.v2

import com.android.builder.core.ComponentTypeImpl
import com.android.builder.model.v2.ide.SourceProvider
import com.android.builder.model.v2.ide.SourceSetContainer
import java.io.Serializable

data class SourceSetContainerImpl(
    override val sourceProvider: SourceProvider,
    override val deviceTestSourceProviders: Map<String, SourceProvider>,
    override val hostTestSourceProviders: Map<String, SourceProvider>,
    override val testFixturesSourceProvider: SourceProvider? = null
) : SourceSetContainer, Serializable {

    override val androidTestSourceProvider: SourceProvider?
        get() = deviceTestSourceProviders[ComponentTypeImpl.ANDROID_TEST.artifactName]
    override val unitTestSourceProvider: SourceProvider?
        get() = hostTestSourceProviders[ComponentTypeImpl.UNIT_TEST.artifactName]

    companion object {
        @JvmStatic
        private val serialVersionUID: Long = 1L
    }
}
