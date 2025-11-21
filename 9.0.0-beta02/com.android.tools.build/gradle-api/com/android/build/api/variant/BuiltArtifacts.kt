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

package com.android.build.api.variant

import com.android.build.api.artifact.Artifact
import org.gradle.api.file.Directory
import org.gradle.workers.WorkParameters
import java.io.File
import java.io.Serializable

/**
 * Represents a [Collection] of [BuiltArtifact] produced by a [org.gradle.api.Task].
 *
 * Tasks in Android Gradle Plugin can produce more than one file in the output folder so any
 * [Artifact] with a [Artifact.kind] of type
 * [com.android.build.api.artifact.ArtifactKind.DIRECTORY] can actually contain several produced
 * [File]. For instance, when dealing with multi-apk, there will be several manifest files or APKs
 * produced by the Android Gradle Plugin.
 *
 * Each produced file can be identified with unique metadata like the list of filters and/or
 * version code or version name. This instance will allow producer and consumer
 * [org.gradle.api.Task]s to easily match produced files with this metadata without relying on
 * name mangling or other custom solutions.
 *
 * Simple use of this facility can look like :
 *
 * <pre><code>
 * abstract class MyTask @inject constructor(val objectFactory: ObjectFactory): DefaultTask() {
 *     @get:InputDirectory
 *     abstract val input: DirectoryProperty
 *     @get:OutputDirectory
 *     abstract val output: DirectoryProperty
 *     @get:Internal
 *     abstract val artifactsLoader: Property<BuiltArtifactsLoader>
 *
 *     @TaskAction
 *     fun taskAction() {
 *          val builtArtifacts= artifactsLoader.get().load(
 *               objectFactory, input.get())
 *
 *          TODO : TBD what will be surfaced here
 *
 *          newBuiltArtifacts.save(output.get()))
 *     }
 * }
 * </code></pre>
 *
 * This [BuiltArtifacts] will abstract access to these produced files and provided some metadata
 * associated with each file to be able to identify filters, version code or version name.
 */
interface BuiltArtifacts {

    companion object {
        /**
         * Current version of the metadata file.
         */
        const val METADATA_FILE_VERSION = 3
    }

    /**
     * Identifies the [Artifact] for this [Collection] of [BuiltArtifact], all [BuiltArtifact]
     * are the same type of artifact.
     *
     * @return the [Artifact] for all the [BuiltArtifact] instances.
     */
    val artifactType: Artifact<*>

    /**
     * Returns the application ID for these [BuiltArtifact] instances.
     *
     * @return the application ID.
     */
    val applicationId: String

    /**
     * Identifies the variant name for these [BuiltArtifact]
     */
    val variantName: String

    /**
     * Returns the [Collection] of [BuiltArtifact].
     */
    val elements: Collection<BuiltArtifact>

    /**
     * Saves the metadata associated with this instance into a folder.
     * @param out the [Directory] that can be used to save the metadata using a standard file
     * name.
     */
    fun save(out: Directory)

    /**
     * Specialized version  of Gradle's [WorkParameters] so we can retrieve the output file
     * generated when transforming an instance of [BuiltArtifacts] into a new one.
     */
    interface TransformParams: WorkParameters, Serializable {
        /**
         * Result of the work item submission must be made available through this field.
         */
        val output: File
    }
}
