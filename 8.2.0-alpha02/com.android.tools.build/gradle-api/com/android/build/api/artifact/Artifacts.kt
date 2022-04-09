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

import com.android.build.api.variant.BuiltArtifactsLoader
import com.android.build.api.variant.ScopedArtifacts
import org.gradle.api.Task
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

/**
 * Access to the artifacts on a Variant object.
 *
 * Artifacts are temporary or final files or directories that are produced by the Android Gradle
 * plugin during the build. Depending on its configuration, each [com.android.build.api.variant.VariantBuilder]
 * produces different versions of some of the output artifacts.
 *
 * An example of temporary artifacts are .class files obtained from compiling source files that will
 * eventually get transformed further into dex files. Final artifacts are APKs and bundle files that
 * are not transformed further.
 *
 * Artifacts are uniquely defined by their [Artifact] type and public artifact types that can be
 * accessed from third-party plugins or build script are defined in [SingleArtifact]
 */
interface Artifacts {

    /**
     * Get the [Provider] of [FileTypeT] for the passed [Artifact].
     *
     * @param type Type of the single artifact.
     */
    fun <FileTypeT: FileSystemLocation> get(
        type: SingleArtifact<FileTypeT>
    ): Provider<FileTypeT>

    /**
     * Get all the [Provider]s of [FileTypeT] for the passed [Artifact].
     *
     * @param type Type of the multiple artifact.
     */
    fun <FileTypeT: FileSystemLocation> getAll(
        type: MultipleArtifact<FileTypeT>
    ): Provider<List<FileTypeT>>

    /**
     * Add an existing [FileTypeT] for the passed [Artifact].
     * For task generated folder or file, do not use this API but instead use the [use] API.
     *
     * @param type Type of the multiple artifact.
     * @param artifact is an existing static [FileTypeT]
     */
    fun <FileTypeT: FileSystemLocation> add(
            type: MultipleArtifact<FileTypeT>,
            artifact: FileTypeT
    )

    /**
     * Access [Task] based operations.
     *
     * @param taskProvider The [TaskProvider] for the [TaskT] that will be producing and/or
     * consuming artifact types.
     * @return A [TaskBasedOperation] object using the passed [TaskProvider] for all its operations.
     */
    fun <TaskT: Task> use(taskProvider: TaskProvider<TaskT>): TaskBasedOperation<TaskT>

    /**
     * Provides an implementation of [BuiltArtifactsLoader] that can be used to load built artifacts
     * metadata.
     *
     * @return A thread safe implementation of [BuiltArtifactsLoader] that can be reused.
     */
    fun getBuiltArtifactsLoader(): BuiltArtifactsLoader

    /**
     * Some artifacts do not have a single origin (like compiled from source code). Some artifacts
     * can be obtained from a combination of [Task]s running or incoming dependencies. For example,
     * classes used for dexing can come from compilation related tasks as well as .aar or .jar
     * files expressed as a project dependency.
     *
     * Therefore, these artifacts values can have a scope like [ScopedArtifacts.Scope.PROJECT] for
     * values directly produced by this module (as a [Task] output most likely). Alternatively,
     * the [ScopedArtifacts.Scope.ALL] adds all incoming dependencies (including transitive ones)
     * to the previous scope.
     *
     * For such cases, the artifact is represented as [ScopedArtifact] and can be manipulated by
     * its own set of API that are scope aware.
     *
     * Return [ScopedArtifacts] for a [ScopedArtifacts.Scope]
     */
    fun forScope(scope: ScopedArtifacts.Scope): ScopedArtifacts
}
