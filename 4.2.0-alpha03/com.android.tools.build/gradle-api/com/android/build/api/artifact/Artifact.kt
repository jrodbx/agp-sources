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

package com.android.build.api.artifact

import org.gradle.api.Incubating
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import java.io.File
import java.util.Locale
import java.io.Serializable

/**
 * Defines a type of artifact handled by the Android Gradle Plugin.
 *
 * Each instance of [Artifact] is produced by a [org.gradle.api.Task] and potentially consumed by
 * any number of tasks.
 *
 * An artifact can potentially be produced by more than one task (each task acting in an additive
 * behavior), but consumers must be aware when more than one artifact can be present,
 * implementing the [MultipleArtifact] interface will indicate such requirement.
 *
 * An artifact must be one of the supported [ArtifactKind] and must be provided when the constructor is called.
 * ArtifactKind also defines the specific [FileSystemLocation] subclass used.
 */
@Incubating
abstract class Artifact<T: FileSystemLocation>(
    val kind: ArtifactKind<T>,
    val category: Category
) : Serializable {

    /**
     * Provide a unique name for the artifact type. For external plugins defining new types,
     * consider adding the plugin name to the artifact's name to avoid collision with other plugins.
     */
    fun name(): String = javaClass.simpleName

    /**
     * @return The folder name under which the artifact files or folders should be stored.
     */
    open fun getFolderName(): String = name().toLowerCase(Locale.US)

    /**
     * @return Depending on [T], returns the file name of the folder under the variant-specific folder or
     * an empty string to use defaults.
     */
    open fun getFileSystemLocationName(): String = ""

    /**
     * Supported [ArtifactKind]
     */
    @Incubating
    companion object {
        /**
         * [ArtifactKind] for [RegularFile]
         */
        @JvmField
        val FILE = ArtifactKind.FILE

        /**
         * [ArtifactKind] for [Directory]
         */
        @JvmField
        val DIRECTORY = ArtifactKind.DIRECTORY
    }

    /**
     * Defines the kind of artifact type. this will be used to determine the output file location
     * for instance.
     */
    @Incubating
    enum class Category {
        /* Source artifacts */
        SOURCES,
        /* Generated files that are meant to be visible to users from the IDE */
        GENERATED,
        /* Intermediates files produced by tasks. */
        INTERMEDIATES,
        /* output files going into the outputs folder. This is the result of the build. */
        OUTPUTS;
    }

    /**
     * Denotes possible multiple [FileSystemLocation] instances for this artifact type.
     * Consumers of artifact types with multiple instances must consume a collection of
     * [FileSystemLocation]
     */
    @Incubating
    abstract class MultipleArtifact<FileTypeT: FileSystemLocation>(
        kind: ArtifactKind<FileTypeT>,
        category: Category
    ) : Artifact<FileTypeT>(kind, category)


    /**
     * Denotes a single [FileSystemLocation] instance of this artifact type at a given time.
     * Single artifact types can be transformed or replaced but never appended.
     */
    @Incubating
    abstract class SingleArtifact<FileTypeT: FileSystemLocation>(
        kind: ArtifactKind<FileTypeT>,
        category: Category
    ) : Artifact<FileTypeT>(kind, category)

    /**
     * Denotes a single [DIRECTORY] that may contain zero to many
     * [com.android.build.api.variant.BuiltArtifact].
     *
     * Artifact types annotated with this marker interface are backed up by a [DIRECTORY] whose
     * content should be read using the [com.android.build.api.variant.BuiltArtifactsLoader].
     *
     * If producing an artifact type annotated with this marker interface, content should be
     * written using the [com.android.build.api.variant.BuiltArtifacts.save] methods.
     */
    @Incubating
    interface ContainsMany

    /**
     * Denotes an artifact type that can be appended to.
     * Appending means that existing artifacts produced by other tasks are untouched and a
     * new task producing the artifact type will have its output appended to the list of artifacts.
     *
     * Due to the additive behavior of the append scenario, an [Appendable] must be a
     * [MultipleArtifact].
     */
    @Incubating
    interface Appendable

    /**
     * Denotes an artifact type that can transformed.
     *
     * Either a [SingleArtifact] or [MultipleArtifact] artifact type can be transformed.
     */
    @Incubating
    interface Transformable

    /**
     * Denotes an artifact type that can be replaced.
     * Only [SingleArtifact] artifacts can be replaced, if you want to replace a [MultipleArtifact]
     * artifact type, you will need to transform it by combining all the inputs into a single output
     * instance.
     */
    @Incubating
    interface Replaceable
}


