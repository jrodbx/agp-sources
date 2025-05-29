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
package com.android.ide.common.repository

import com.android.ide.common.gradle.Component
import com.android.ide.common.gradle.Dependency
import com.android.ide.common.gradle.Module
import com.android.ide.common.gradle.RichVersion
import com.android.ide.common.gradle.Version

sealed interface WellKnownMavenArtifactId {
    val groupId: String
    val artifactId: String

    fun getModule(): Module =
        Module(groupId, artifactId)

    fun getCoordinate(revision: String): GradleCoordinate =
        GradleCoordinate(groupId, artifactId, revision)

    fun getComponent(version: String): Component =
        Component(groupId, artifactId, Version.parse(version))

    fun getDependency(richVersion: String): Dependency =
        Dependency(groupId, artifactId, RichVersion.parse(richVersion))

    val displayName get() = "$groupId:$artifactId"

    companion object {
        @JvmField val IDS_BY_GROUP_ARTIFACT_PAIR = mutableMapOf<Pair<String,String>,WellKnownMavenArtifactId>()
        @JvmField val KOTLIN_STDLIB: WellKnownMavenArtifactId = WellKnownKotlinArtifactId("kotlin-stdlib")
        @JvmField val KOTLIN_REFLECT: WellKnownMavenArtifactId = WellKnownKotlinArtifactId("kotlin-reflect")
        @JvmField val TFLITE_GPU: WellKnownMavenArtifactId = WellKnownTfliteArtifactId("tensorflow-lite-gpu")
        @JvmField val TFLITE_METADATA: WellKnownMavenArtifactId = WellKnownTfliteArtifactId("tensorflow-lite-metadata")
        @JvmField val TFLITE_SUPPORT: WellKnownMavenArtifactId = WellKnownTfliteArtifactId("tensorflow-lite-support")

        @JvmStatic
        fun find(groupId: String, artifactId: String) =
            GoogleMavenArtifactId.find(groupId, artifactId)
                ?: IDS_BY_GROUP_ARTIFACT_PAIR[groupId to artifactId]
    }
}

private data class WellKnownKotlinArtifactId(
    override val artifactId: String
) : WellKnownMavenArtifactId {
    override val groupId = "org.jetbrains.kotlin"

    init {
        WellKnownMavenArtifactId.IDS_BY_GROUP_ARTIFACT_PAIR[groupId to artifactId] = this
    }

    override fun toString() = displayName
}

private data class WellKnownTfliteArtifactId(
    override val artifactId: String
) : WellKnownMavenArtifactId {
    override val groupId = "org.tensorflow"

    init {
        WellKnownMavenArtifactId.IDS_BY_GROUP_ARTIFACT_PAIR[groupId to artifactId] = this
    }

    override fun toString() = displayName
}
