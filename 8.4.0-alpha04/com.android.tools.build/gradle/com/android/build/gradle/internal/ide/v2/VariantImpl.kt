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

import com.android.builder.model.v2.ide.AndroidArtifact
import com.android.builder.model.v2.ide.JavaArtifact
import com.android.builder.model.v2.ide.TestedTargetVariant
import com.android.builder.model.v2.ide.Variant
import java.io.File
import java.io.Serializable

/**
 * Implementation of [Variant] for serialization via the Tooling API.
 */
data class VariantImpl(
    override val name: String,
    override val displayName: String,
    override val mainArtifact: AndroidArtifact,
    override val androidTestArtifact: AndroidArtifact?,
    override val unitTestArtifact: JavaArtifact?,
    override val testFixturesArtifact: AndroidArtifact?,
    override val testedTargetVariant: TestedTargetVariant?,
    override val runTestInSeparateProcess: Boolean,
    override val isInstantAppCompatible: Boolean,
    override val desugaredMethods: List<File>
) : Variant, Serializable {
    companion object {
        @JvmStatic
        private val serialVersionUID: Long = 2L
    }
}
