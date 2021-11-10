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
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile

/**
 * Public [Artifact] for Android Gradle plugin.
 *
 * This type inherits [Artifact.Multiple]. For single artifacts, see [SingleArtifact].
 *
 * All methods in [Artifacts] should be supported with any subclass of this
 * class.
 */
sealed class MultipleArtifact<FileTypeT : FileSystemLocation>(
    kind: ArtifactKind<FileTypeT>,
    category: Category =  Category.INTERMEDIATES,
) : Artifact.Multiple<FileTypeT>(kind, category) {

    /**
     * Text files with additional ProGuard rules to be used to determine which classes are compiled
     * into the main dex file.
     *
     * If set, rules from these files are used in combination with the default rules used by the
     * build system.
     *
     * Initialized from DSL [com.android.build.api.dsl.VariantDimension.multiDexKeepProguard]
     */
    object MULTIDEX_KEEP_PROGUARD:
            MultipleArtifact<RegularFile>(FILE, Category.SOURCES),
            Transformable,
            Replaceable

    /**
     * All classes that will eventually be dex'ed for this module provided as directories.
     */
    @Incubating
    object ALL_CLASSES_DIRS:
            MultipleArtifact<Directory>(DIRECTORY),
            Appendable,
            Transformable,
            Replaceable

    /**
     * All classes that will eventually be dex'ed for this module provided as jar files.
     */
    @Incubating
    object ALL_CLASSES_JARS:
            MultipleArtifact<RegularFile>(FILE),
            Appendable,
            Transformable,
            Replaceable

    /**
     * Assets that will be packaged in the resulting APK or Bundle.
     *
     * When used as an input, the content will be the merged assets.
     * For the APK, the assets will be compressed before packaging.
     *
     * When using [OutOperationRequest.toAppendTo] method is used to append assets for packaging,
     * no handling is provided in case of conflicts. In other words, you cannot have conflicts when
     * adding to the assets. If there can be conflicts,
     * [InAndOutDirectoryOperationRequest.toTransformMany] should be used to process all inputs and
     * handle the conflicts before writing out the transformed assets.
     */
    @Incubating
    object ASSETS:
        MultipleArtifact<Directory>(DIRECTORY),
        Appendable,
        Transformable,
        Replaceable
}
