/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.api.artifact.ArtifactKind
import com.android.build.api.artifact.Artifact
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile

/**
 * List of internal [Artifact.Multiple] [Artifact]
 */
sealed class InternalMultipleArtifactType<T: FileSystemLocation>(
    kind: ArtifactKind<T>,
    category: Category = Category.INTERMEDIATES
) : Artifact.Multiple<T>(kind, category), Artifact.Appendable {

    // The final dex files (if the dex splitter does not run)
    // that will get packaged in the APK or bundle.
    object DEX: InternalMultipleArtifactType<Directory>(DIRECTORY)

    // External libraries' dex files only.
    object EXTERNAL_LIBS_DEX: InternalMultipleArtifactType<Directory>(DIRECTORY)

    // Partial R.txt files generated by AAPT2 at compile time.
    object PARTIAL_R_FILES: InternalMultipleArtifactType<Directory>(DIRECTORY)

    // --- Namespaced android res ---
    // Compiled resources (directory of .flat files) for the local library
    object RES_COMPILED_FLAT_FILES: InternalMultipleArtifactType<Directory>(DIRECTORY)

    // native libs built in module
    // Unlike other JNI libs artifacts, these directories end with the ABI name. The reason is that
    // separate tasks produce each ABI's outputs.
    object EXTERNAL_NATIVE_BUILD_LIBS: InternalMultipleArtifactType<Directory>(DIRECTORY)
}
