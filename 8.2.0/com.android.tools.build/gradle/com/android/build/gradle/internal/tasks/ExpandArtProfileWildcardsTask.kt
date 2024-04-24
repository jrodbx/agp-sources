/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.dexing.ClassBucket
import com.android.builder.dexing.DirectoryBucketGroup
import com.android.tools.profgen.Diagnostics
import com.android.tools.profgen.expandWildcards
import com.google.common.io.Closer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import kotlin.io.path.pathString

/**
 * Task that executes profgen method to expand wildcards in the merged art profile.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ART_PROFILE)
abstract class ExpandArtProfileWildcardsTask: NonIncrementalTask() {
    @get: [InputFiles Optional PathSensitive(PathSensitivity.NAME_ONLY)]
    abstract val mergedArtProfile: RegularFileProperty

    @get:Classpath
    abstract val projectClasses: ConfigurableFileCollection

    @get: OutputFile
    abstract val expandedArtProfile: RegularFileProperty

    override fun doTaskAction() {
        if (!mergedArtProfile.isPresent || !mergedArtProfile.get().asFile.exists()) return

        workerExecutor.noIsolation().submit(ExpandArtProfileWildcardsWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.mergedArtProfile.set(mergedArtProfile)
            it.projectClasses.from(projectClasses)
            it.expandedArtProfile.set(expandedArtProfile)
        }
    }

    abstract class ExpandArtProfileWildcardsWorkAction:
        ProfileAwareWorkAction<ExpandArtProfileWildcardsWorkAction.Parameters>() {
        abstract class Parameters : ProfileAwareWorkAction.Parameters() {
            abstract val mergedArtProfile: RegularFileProperty
            abstract val projectClasses: ConfigurableFileCollection
            abstract val expandedArtProfile: RegularFileProperty
        }

        override fun run() {
            val diagnostics = Diagnostics { error ->
                throw RuntimeException("Error parsing baseline-prof.txt : $error")
            }

            val (directoryInputs, jarInputs) =
                parameters.projectClasses
                    .filter { it.exists() }
                    .partition { it.isDirectory }

            val classesFilePaths: MutableList<String> = jarInputs.map { it.path }.toMutableList()

            if (directoryInputs.isNotEmpty()) {
                val classBucket = ClassBucket(
                    DirectoryBucketGroup(directoryInputs.toList(), 1), 0)
                Closer.create().use { closer ->
                    val filter: (File, String) -> Boolean = { _, _ -> true }
                    classBucket.getClassFiles(filter, closer).use { stream ->
                        stream.forEach {
                            classesFilePaths.add(it.input.path.pathString + ":" + it.relativePath)
                        }
                    }
                }
            }

            expandWildcards(
                parameters.mergedArtProfile.get().asFile.path,
                parameters.expandedArtProfile.get().asFile.path,
                classesFilePaths,
                diagnostics)
        }
    }

    class CreationAction(
        creationConfig: ApkCreationConfig,
        private val classesClasspathUtils: ClassesClasspathUtils,
    ) : VariantTaskCreationAction<ExpandArtProfileWildcardsTask, ApkCreationConfig>(creationConfig) {

        override val name: String
            get() = creationConfig.computeTaskName("expand", "ArtProfileWildcards")
        override val type: Class<ExpandArtProfileWildcardsTask>
            get() = ExpandArtProfileWildcardsTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<ExpandArtProfileWildcardsTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.use(taskProvider).wiredWithFiles(
                ExpandArtProfileWildcardsTask::mergedArtProfile,
                ExpandArtProfileWildcardsTask::expandedArtProfile
            ).toTransform(InternalArtifactType.MERGED_ART_PROFILE)
        }

        override fun configure(task: ExpandArtProfileWildcardsTask) {
            super.configure(task)

            task.projectClasses.fromDisallowChanges(
                classesClasspathUtils.projectClasses +
                classesClasspathUtils.subProjectsClasses +
                classesClasspathUtils.mixedScopeClasses +
                classesClasspathUtils.externalLibraryClasses
            )
        }
    }
}
