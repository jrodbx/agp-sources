/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.featuresplit.removeVariantNameFromId
import com.android.buildanalyzer.common.TaskCategory
import com.android.utils.FileUtils
import com.google.common.io.Files
import org.apache.commons.io.Charsets
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File

/**
 * Task to check that no two APKs in a multi-APK project package the same library
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.VERIFICATION)
abstract class CheckMultiApkLibrariesTask : NonIncrementalTask() {

    private lateinit var featureTransitiveDeps : ArtifactCollection

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getFeatureTransitiveDepsFiles() : FileCollection = featureTransitiveDeps.artifactFiles

    // include fakeOutputDir to allow up-to-date checking
    @get:OutputDirectory
    lateinit var fakeOutputDir: File
        private set

    override fun doTaskAction() {
        // Build a map of libraries to their corresponding modules. If two modules package the same
        // library, we will use the map to output a user-friendly error message.
        val map = mutableMapOf<String, MutableList<String>>()
        var found = false
        for (artifact in featureTransitiveDeps) {
            // Sanity check. This should never happen.
            if (artifact.id.componentIdentifier !is ProjectComponentIdentifier) {
                throw GradleException(
                    artifact.id.componentIdentifier.displayName + " is not a Gradle project.")
            }

            val projectPath =
                    (artifact.id.componentIdentifier as ProjectComponentIdentifier).projectPath
            if (artifact.file.isFile) {
                val newlyFound = checkAndUpdateLibraryMap(artifact.file, projectPath, map)
                found = found || newlyFound
            }
        }

        if (found) {
            // Build the error message. Sort map and projectPaths for consistency.
            val output = StringBuilder()
            for ((library, projectPaths) in map.toSortedMap()) {
                if (projectPaths.size > 1) {
                    output
                        .append(projectPaths.sorted().joinToString(prefix = "[", postfix = "]"))
                        .append(" all package the same library [$library].\n")
                }
            }
            output.append(
                """

                    Multiple APKs packaging the same library can cause runtime errors.
                    Placing each of the above libraries in its own dynamic feature and adding that
                    feature as a dependency of modules requiring it will resolve this issue.
                    Libraries that are always used together can be combined into a single feature
                    module to be imported by their dependents. If a library is required by all
                    feature modules it can be added to the base module instead.
                    """.trimIndent()
            )
            throw GradleException(output.toString())
        }
    }

    class CreationAction(creationConfig: ComponentCreationConfig) :
        VariantTaskCreationAction<CheckMultiApkLibrariesTask, ComponentCreationConfig>(
            creationConfig,
            dependsOnPreBuildTask = false
        ) {

        override val name: String
            get() = computeTaskName("check", "Libraries")
        override val type: Class<CheckMultiApkLibrariesTask>
            get() = CheckMultiApkLibrariesTask::class.java

        override fun configure(
            task: CheckMultiApkLibrariesTask
        ) {
            super.configure(task)

            task.featureTransitiveDeps =
                    creationConfig.variantDependencies.getArtifactCollection(
                        AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        AndroidArtifacts.ArtifactType.PACKAGED_DEPENDENCIES
                    )
            task.fakeOutputDir =
                    FileUtils.join(
                        creationConfig.services.projectInfo.getIntermediatesDir(),
                        "check-libraries",
                        creationConfig.dirName
                    )
        }
    }

    private fun checkAndUpdateLibraryMap(
        file: File,
        projectPath: String,
        map: MutableMap<String, MutableList<String>>
    ): Boolean {
        var found = false
        for (library in Files.readLines(file, Charsets.UTF_8)) {
            val key = removeVariantNameFromId(library)
            if (map.containsKey(key)) {
                found = true
                map[key]?.add(projectPath)
            } else {
                map[key] = mutableListOf(projectPath)
            }
        }
        return found
    }
}
