/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.privaysandboxsdk

import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.ArtifactKind
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile

@Suppress("ClassName")
sealed class
PrivacySandboxSdkInternalArtifactType<T : FileSystemLocation>(
    kind: ArtifactKind<T>,
    category: Category = Category.INTERMEDIATES,
) : Artifact.Single<T>(kind, category) {

    // Directory containing classes and java resources provided to the api packager.
    object API_PACKAGER_SOURCES: PrivacySandboxSdkInternalArtifactType<Directory>(ArtifactKind.DIRECTORY), Replaceable

    // generated manifest file that contains permissions to be automatically added to the sandbox.
    object SANDBOX_MANIFEST: PrivacySandboxSdkInternalArtifactType<RegularFile>(ArtifactKind.FILE), Replaceable

    // final .asb file ready to be uploaded to Play Store
    object ASB: PrivacySandboxSdkInternalArtifactType<RegularFile>(ArtifactKind.FILE, category = Category.OUTPUTS), Transformable
    // Locally derived Android Sdk Archive file for local testing
    object ASAR: PrivacySandboxSdkInternalArtifactType<RegularFile>(ArtifactKind.FILE, category = Category.OUTPUTS), Replaceable

    object LINKED_MERGE_RES_FOR_ASB: PrivacySandboxSdkInternalArtifactType<RegularFile>(ArtifactKind.FILE), Replaceable
    object RUNTIME_SYMBOL_LIST: PrivacySandboxSdkInternalArtifactType<RegularFile>(ArtifactKind.FILE)
    object RUNTIME_R_CLASS: PrivacySandboxSdkInternalArtifactType<RegularFile>(ArtifactKind.FILE)
    object MODULE_BUNDLE: PrivacySandboxSdkInternalArtifactType<RegularFile>(ArtifactKind.FILE), Replaceable
    object APP_METADATA: PrivacySandboxSdkInternalArtifactType<RegularFile>(FILE), Replaceable

    // Directory containing merged resources from all libraries and their dependencies.
    object MERGED_RES: PrivacySandboxSdkInternalArtifactType<Directory>(ArtifactKind.DIRECTORY), Replaceable, Transformable

    // Directory containing blame log of fused library manifest merging
    object MERGED_RES_BLAME_LOG: PrivacySandboxSdkInternalArtifactType<Directory>(ArtifactKind.DIRECTORY), Replaceable

    object INCREMENTAL_MERGED_RES: PrivacySandboxSdkInternalArtifactType<Directory>(ArtifactKind.DIRECTORY), Replaceable
    object STUB_JAR: PrivacySandboxSdkInternalArtifactType<RegularFile>(FILE), Replaceable
    object DEX_ARCHIVE: PrivacySandboxSdkInternalArtifactType<Directory>(ArtifactKind.DIRECTORY), Replaceable
    object DEX_ARCHIVE_INPUT_JAR_HASHES: PrivacySandboxSdkInternalArtifactType<RegularFile>(ArtifactKind.FILE), Replaceable

    object DESUGAR_GRAPH: PrivacySandboxSdkInternalArtifactType<Directory>(ArtifactKind.DIRECTORY), Replaceable
    object GLOBAL_SYNTHETICS_ARCHIVE: PrivacySandboxSdkInternalArtifactType<Directory>(ArtifactKind.DIRECTORY), Replaceable

    object DEX: PrivacySandboxSdkInternalArtifactType<Directory>(ArtifactKind.DIRECTORY), Replaceable

    object GENERATED_PROGUARD_FILE: PrivacySandboxSdkInternalArtifactType<RegularFile>(FILE), Replaceable
}
