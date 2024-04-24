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

import com.android.build.gradle.internal.DependencyResourcesComputer
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.options.Version
import com.android.buildanalyzer.common.TaskCategory
import com.android.utils.FileUtils
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider

import java.io.FileOutputStream
import java.nio.file.Files
import javax.inject.Inject

/*
Anchor task with validating resource folder overlapping.
*/
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.VERIFICATION)
abstract class ValidateResourcesTask @Inject constructor(
    @get:Internal
    val projectLayout: ProjectLayout
) : NonIncrementalTask() {

    @get:Nested
    abstract val resources: MapProperty<String, DependencyResourcesComputer.ResourceSourceSetInput>

    @get:OutputFile
    abstract val validationReportFile: RegularFileProperty

    override fun doTaskAction() {
        verifyNestedResources().let { verificationResult ->
            FileOutputStream(validationReportFile.get().asFile).use {
                it.write(verificationResult.toByteArray(Charsets.UTF_8))
            }
        }
    }

    /**
     * Method checks if any resource folders are nested inside others. For example folder a and a/b.
     * Warning will be shown once we find such situation.
     */
    private fun verifyNestedResources(): String {
        val fileCollection = resources.get().values.map { it.sourceDirectories }
        val dirs = fileCollection.flatten()
            .map {
                if (Files.isSymbolicLink(it.toPath()))
                    Files.readSymbolicLink(it.toPath()).normalize().toFile()
                else
                    it.normalize()
            }

        val rootDir = projectLayout.projectDirectory.asFile
        val map = mutableMapOf<String, MutableList<String>>()
        for (dirA in dirs) {
            for (dirB in dirs) {
                if (!FileUtils.isSameFile(dirA, dirB) && FileUtils.isFileInDirectory(dirA, dirB)) {
                    val pivotRelative =
                        if (FileUtils.isFileInDirectory(dirB, rootDir)) dirB.relativeTo(rootDir) else dirB
                    val nestedRelative =
                        if (FileUtils.isFileInDirectory(dirA, rootDir)) dirA.relativeTo(rootDir) else dirA
                    map.getOrPut(pivotRelative.toString()) { mutableListOf() }
                        .add(nestedRelative.toString())
                }
            }
        }

        if(map.isNotEmpty()){
            val mapString = map.flatMap { keyValue ->
                listOf("+ ${keyValue.key}", *keyValue.value.map { "-- $it" }.toTypedArray(), "")
            }.joinToString("\n")
            val output = "Nested resources detected.\n" +
                    mapString +
                """

                Nested resources means you have layout like this:
                res.srcDirs = [
                    'src/main/res/',
                    'src/main/res/category1'
                ]
                However, you should use following structure instead:
                res.srcDirs = [
                    'src/main/res/common',
                    'src/main/res/category1',
                    'src/main/res/category2'
                ]
                This Warning will be transformed into Error in version ${Version.VERSION_9_0}
             """.trimIndent()
            logger.warn(output)
            return output
        }
        return "0 Warning/Error"
    }

    internal class CreateAction(
        creationConfig: ComponentCreationConfig,
    ) :  VariantTaskCreationAction<ValidateResourcesTask, ComponentCreationConfig>(creationConfig) {

        override val name: String = creationConfig.computeTaskName("generate", "Resources")

        override val type: Class<ValidateResourcesTask> = ValidateResourcesTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<ValidateResourcesTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ValidateResourcesTask::validationReportFile
            ).withName(VALIDATION_RESULT_FILE_NAME)
                .on(InternalArtifactType.NESTED_RESOURCES_VALIDATION_REPORT)
        }

        override fun configure(task: ValidateResourcesTask) {
            super.configure(task)

            creationConfig.sources.res { resSources ->
                val resourceMap = resSources.getVariantSourcesWithFilter { !it.isGenerated }

                resourceMap.forEach { (name, providerOfDirectories) ->
                    task.resources.put(name,
                        creationConfig.services.newInstance(DependencyResourcesComputer.ResourceSourceSetInput::class.java)
                            .also { it.sourceDirectories.fromDisallowChanges(providerOfDirectories) })
                }
            }
            task.resources.disallowChanges()

        }
    }

    companion object {
        internal const val VALIDATION_RESULT_FILE_NAME = "nestedResourcesValidationReport.txt"
    }

}
