/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.databinding.tool.ext.toCamelCase
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.component.impl.computeTaskName
import com.android.build.api.dsl.AgpTestSuiteInputParameters
import com.android.build.gradle.internal.testsuites.impl.TestEngineInputProperties
import com.android.build.gradle.internal.testsuites.impl.TestEngineInputProperty
import com.android.build.gradle.internal.component.TestSuiteCreationConfig
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.GlobalTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import java.io.File
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions

@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class TestSuiteTestTask: Test(), GlobalTask {

    @get:Nested
    abstract val engineInputParameters: ListProperty<AgpTestSuiteInputParameter>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFolders: ListProperty<Directory>

    @get:OutputFile
    abstract val engineInputProperties: RegularFileProperty

    @get:OutputFile
    abstract val logFile: RegularFileProperty

    @get:OutputFile
    abstract val streamingOutputFile: RegularFileProperty

    @TaskAction
    override fun executeTests() {

        // write all the input properties for the junit engine. This mean the input properties
        // that were requested through the TestSuite DSL/Variant APIs but also the default ones
        // that are always provided.
        TestEngineInputProperties(
            engineInputParameters.get(). map { inputProperty ->
                TestEngineInputProperty(
                    inputProperty.type.toString(),
                    inputProperty.value.get().asFile.absolutePath
                )
            }.plus(
                listOf(
                    TestEngineInputProperty(
                        TestEngineInputProperty.SOURCE_FOLDERS,
                        sourceFolders.get()
                            .joinToString(separator = File.separator) { it.asFile.absolutePath }
                    ),
                    TestEngineInputProperty(
                        TestEngineInputProperty.LOGGING_FILE,
                        providerToPath(logFile)
                    ),
                    TestEngineInputProperty(
                        TestEngineInputProperty.STREAMING_FILE,
                        providerToPath(streamingOutputFile)
                    ),
                )
            )
        ).save(engineInputProperties.asFile.get())

        super.executeTests()

        // Read the junit engine logging file and output it.
        // This is probably a temporary solution until something better is figured out.
        this.logger.info(logFile.get().asFile.readText())
    }

    private fun providerToPath(value: Provider<RegularFile>): String =
        value.get().asFile.absolutePath

    class CreationAction(
        val creationConfig: TestSuiteCreationConfig
    ): GlobalTaskCreationAction<TestSuiteTestTask>() {

        override val name: String
            get() = computeTaskName(creationConfig.testedVariant.name, "test${creationConfig.name.toCamelCase()}","TestSuite" )

        override val type: Class<TestSuiteTestTask> = TestSuiteTestTask::class.java

        override fun configure(task: TestSuiteTestTask) {
            super.configure(task)
            task.outputs.upToDateWhen { false }

            val classesDir = task.project.layout.buildDirectory.file(task.name)
            UniqueClassGenerator().generateSimpleClass(classesDir.get().asFile)
            task.testClassesDirs =  creationConfig.services.fileCollection().also {
                it.from(classesDir)
            }
            task.classpath = creationConfig.services.fileCollection().also {
                it.from(classesDir)
                it.from(creationConfig.testSuiteClasspath.runtimeClasspath)
            }

            creationConfig.junitEngineSpec.inputs.forEach { inputParameter: AgpTestSuiteInputParameters ->
                when (inputParameter) {
                    AgpTestSuiteInputParameters.MERGED_MANIFEST -> {
                        task.engineInputParameters.add(
                            AgpTestSuiteInputParameter(
                                AgpTestSuiteInputParameters.MERGED_MANIFEST,
                                creationConfig.testedVariant.artifacts.get(
                                    SingleArtifact.MERGED_MANIFEST
                                )
                            )
                        )
                    }

                    AgpTestSuiteInputParameters.TESTED_APKS -> {
                        task.engineInputParameters.add(
                            AgpTestSuiteInputParameter(
                                AgpTestSuiteInputParameters.TESTED_APKS,
                                creationConfig.testedVariant.artifacts.get(
                                    SingleArtifact.APK
                                )
                            )
                        )
                    }

                    else -> {
                        println("I don't know of this parameter $inputParameter")
                    }
                }
            }
            task.useJUnitPlatform { testFramework: JUnitPlatformOptions ->
                testFramework.includeEngines(*creationConfig.junitEngineSpec.includeEngines.toTypedArray())
                testFramework.excludeEngines("junit-jupiter")
            }
            task.sourceFolders.set(
                creationConfig.sources.all()
            )

            task.engineInputParameters.disallowChanges()
            // TODO : Improve file handling by using Artifacts APIs.
            task.engineInputProperties.set(
                task.project.layout.buildDirectory
                    .file("intermediates/${creationConfig.testedVariant.name}/$name/junit_inputs.json")
            )
            task.logFile.set(
                task.project.layout.buildDirectory
                    .file("intermediates/${creationConfig.testedVariant.name}/$name/junit_engines_logging.txt")
            )
            task.streamingOutputFile.set(
                task.project.layout.buildDirectory
                    .file("intermediates/${creationConfig.testedVariant.name}/$name/streaming.txt")
            )
            task.environment(TestEngineInputProperties.INPUT_PARAMETERS, task.engineInputProperties.get().asFile.absolutePath)
            task.environment("junit.platform.commons.logging.level","debug")

            // TODO : Provide this as a DSL setting
            val debugJunitEngine = System.getenv("DEBUG_JUNIT_ENGINE")
            if (!debugJunitEngine.isNullOrEmpty()) {
                val serverArg: String =
                    if (debugJunitEngine.equals("socket-listen", ignoreCase = true)) "n" else "y"
                task.jvmArgs("-agentlib:jdwp=transport=dt_socket,server=$serverArg,suspend=y,address=5006")
            }
        }
    }

    class AgpTestSuiteInputParameter(
        @get:Input
        val type: AgpTestSuiteInputParameters,

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        val value: Provider<out FileSystemLocation>
    )
}
