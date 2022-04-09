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
package com.android.build.gradle.internal.coverage

import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.pipeline.OriginalStream
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.utils.zipEntry
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.util.jar.JarOutputStream

/**
 * Writes the java resource file for jacoco to work out of the box.
 *
 * See https://issuetracker.google.com/151471144 for context
 *
 * Caching disabled by default for this task because the task does very little work.
 * The taskAction does no complex processing -- it just writes a file to disk with some
 *  statically determinate content.
 * Calculating cache hit/miss and fetching results is likely more expensive than
 *  simply executing the task.
 */
@DisableCachingByDefault
abstract class JacocoPropertiesTask : NonIncrementalTask() {

    @get:OutputFile
    abstract val propertiesJar: RegularFileProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(WriteJacocoPropertiesFile::class.java) {
            it.propertiesJar.setDisallowChanges(propertiesJar)
        }
    }

    abstract class WriteJacocoPropertiesFile : WorkAction<WriteJacocoPropertiesFile.Parameters> {

        interface Parameters : WorkParameters {
            val propertiesJar: RegularFileProperty
        }

        override fun execute() {
            JarOutputStream(
                parameters.propertiesJar.get().asFile.outputStream()
                    .buffered()
            ).use { jar ->
                jar.putNextEntry(zipEntry("jacoco-agent.properties"))
                jar.write("#Injected by the Android Gradle Plugin\noutput=none\n".toByteArray())
            }
        }
    }

    class CreationAction(creationConfig: ComponentCreationConfig) : VariantTaskCreationAction<JacocoPropertiesTask, ComponentCreationConfig>(
        creationConfig
    ) {

        override val name: String =
            creationConfig.computeTaskName("generate", "JacocoPropertiesFile")
        override val type: Class<JacocoPropertiesTask> get() = JacocoPropertiesTask::class.java

        init {
            // Do immediately as transform API is sensitive to the execution order.
            if (creationConfig.variantScope.needsJavaResStreams) {
                val taskOutput =
                    creationConfig.artifacts.get(InternalArtifactType.JACOCO_CONFIG_RESOURCES_JAR)
                @Suppress("DEPRECATION") // Legacy support (b/195153220)
                creationConfig.transformManager.addStream(
                    OriginalStream.builder("jacoco-properties-file")
                        .addContentType(com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES)
                        .addScope(com.android.build.api.transform.QualifiedContent.Scope.PROJECT)
                        .setFileCollection(creationConfig.services.fileCollection(taskOutput))
                        .build()
                )
            }
        }

        override fun handleProvider(taskProvider: TaskProvider<JacocoPropertiesTask>) {
            creationConfig.artifacts
                .setInitialProvider(taskProvider, JacocoPropertiesTask::propertiesJar)
                .withName("jacoco-properties.jar")
                .on(InternalArtifactType.JACOCO_CONFIG_RESOURCES_JAR)
        }
    }
}
