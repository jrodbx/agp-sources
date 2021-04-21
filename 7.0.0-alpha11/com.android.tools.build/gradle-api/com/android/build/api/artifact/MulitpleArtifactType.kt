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

package com.android.build.api.artifact

import org.gradle.api.Incubating
import org.gradle.api.file.FileSystemLocation

/**
 * Public [Artifact] for Android Gradle plugin.
 *
 * This type inherits [Artifact.MultipleArtifact]. For single artifacts, see [ArtifactType].
 *
 * All methods in [Artifacts] should be supported with any subclass of this
 * class.
 */
@Incubating
sealed class MultipleArtifactType<FileTypeT : FileSystemLocation>(
    kind: ArtifactKind<FileTypeT>,
    category: Category
) : Artifact.MultipleArtifact<FileTypeT>(kind, category) {
    // there are no public multiple artifact types at this time.
}