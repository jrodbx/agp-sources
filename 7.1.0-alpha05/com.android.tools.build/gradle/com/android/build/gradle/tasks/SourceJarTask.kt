/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.getOutputPath
import com.android.build.gradle.internal.tasks.VariantAwareTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.util.PatternSet
import org.gradle.work.DisableCachingByDefault
import java.util.concurrent.Callable

@DisableCachingByDefault
abstract class SourceJarTask : Jar(), VariantAwareTask {

    @Internal
    override lateinit var variantName: String

    class CreationAction(
        creationConfig: ComponentCreationConfig
    ) : VariantTaskCreationAction<SourceJarTask, ComponentCreationConfig>(
        creationConfig
    ) {

        override val type: Class<SourceJarTask>
            get() = SourceJarTask::class.java

        override val name: String
            get() = computeTaskName("source", "Jar")

        override fun handleProvider(
            taskProvider: TaskProvider<SourceJarTask>
        ) {
            super.handleProvider(taskProvider)

            val propertyProvider = { task: SourceJarTask ->
                val property = task.project.objects.fileProperty()
                property.set(task.archiveFile)
                property
            }
            creationConfig.artifacts.setInitialProvider(taskProvider, propertyProvider)
                .on(InternalArtifactType.SOURCE_JAR)
        }

        override fun configure(task: SourceJarTask) {
            super.configure(task)

            task.duplicatesStrategy = DuplicatesStrategy.FAIL
            task.isReproducibleFileOrder = true
            task.isPreserveFileTimestamps = false

            val javaSource = computeJavaSource(creationConfig, task.project)
            val kotlinSource = computeKotlinSource(task.project)

            task.from(javaSource, kotlinSource)

            val outputFile =
                InternalArtifactType.SOURCE_JAR
                    .getOutputPath(
                        creationConfig.artifacts.buildDirectory,
                        creationConfig.name,
                        "out.jar"
                    )

            task.archiveFileName.set(outputFile.name)
            task.destinationDirectory.set(outputFile.parentFile)
            task.archiveExtension.set(SdkConstants.EXT_JAR)
        }

        private fun computeKotlinSource(project: Project): FileTree {
            val sources = Callable {
                listOf(creationConfig.variantSources.getSourceFiles { it.kotlinDirectories} ) }
            val kotlinSourceFilter = PatternSet().include("**/*.kt")
            return project.files(sources).asFileTree.matching(kotlinSourceFilter)
        }
    }
}
