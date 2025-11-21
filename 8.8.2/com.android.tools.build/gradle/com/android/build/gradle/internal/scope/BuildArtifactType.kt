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

package com.android.build.gradle.internal.scope

import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.ArtifactKind
import org.gradle.api.Incubating
import org.gradle.api.file.Directory

/**
 * Artifact type use for transform
 *
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
sealed class BuildArtifactType : Artifact.Single<Directory>(
    ArtifactKind.DIRECTORY,
    Category.INTERMEDIATES
) {
    @Incubating
    object JAVAC_CLASSES : BuildArtifactType()
    @Incubating
    object JAVA_COMPILE_CLASSPATH: BuildArtifactType()
}
