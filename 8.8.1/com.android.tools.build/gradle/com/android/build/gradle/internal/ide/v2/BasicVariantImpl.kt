/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.builder.model.v2.ide.BasicArtifact
import com.android.builder.model.v2.ide.BasicVariant
import com.android.builder.model.v2.ide.Variant
import java.io.Serializable

/**
 * Implementation of [Variant] for serialization via the Tooling API.
 */
data class BasicVariantImpl(
    override val name: String,
    override val mainArtifact: BasicArtifact,
    override val deviceTestArtifacts: Map<String, BasicArtifact>,
    override val hostTestArtifacts: Map<String, BasicArtifact>,
    override val testFixturesArtifact: BasicArtifact?,
    override val buildType: String?,
    override val productFlavors: List<String>,
) : BasicVariant, Serializable {
    override val androidTestArtifact: BasicArtifact?
        get() = deviceTestArtifacts[ComponentTypeImpl.ANDROID_TEST.artifactName]
    override val unitTestArtifact: BasicArtifact?
        get() = hostTestArtifacts[ComponentTypeImpl.UNIT_TEST.artifactName]

    companion object {
        @JvmStatic
        private val serialVersionUID: Long = 1L
    }
}
