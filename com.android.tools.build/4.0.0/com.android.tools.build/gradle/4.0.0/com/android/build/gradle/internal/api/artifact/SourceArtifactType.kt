/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.api.artifact;

import com.android.build.api.artifact.ArtifactType
import org.gradle.api.Incubating;
import org.gradle.api.file.Directory

/** [ArtifactType] for source set. */
@Incubating
sealed class SourceArtifactType: ArtifactType<Directory>(
    DIRECTORY) {
    override val isPublic: Boolean
        get() = true

    object JAVA_SOURCES : SourceArtifactType()
    object JAVA_RESOURCES : SourceArtifactType()
    object ASSETS : SourceArtifactType()
    object ANDROID_RESOURCES : SourceArtifactType()
    object AIDL : SourceArtifactType()
    object RENDERSCRIPT : SourceArtifactType()
    object JNI : SourceArtifactType()
    object JNI_LIBS : SourceArtifactType()
    object SHADERS : SourceArtifactType()
}
