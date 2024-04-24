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

import com.android.Version
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.InstrumentedTestCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.core.ComponentType
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import java.io.File
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.Input

/**
 * Runs screenshot tests of a variant.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class PreviewScreenshotTestTask : Test(), VariantAwareTask {

    companion object {

        const val configurationName = "_internal-screenshot-test-task-test-engine"
        const val previewlibCliToolConfigurationName = "_internal-screenshot-test-task-previewlib-cli"
    }

    @Internal
    override lateinit var variantName: String

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val screenshotCliJar: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val lintModelDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val lintCacheDir: DirectoryProperty

    @get:OutputDirectory
    abstract val ideExtractionDir: DirectoryProperty

    @get:OutputDirectory
    abstract val imageOutputDir: DirectoryProperty

    @get:Input
    abstract val recordGolden: Property<Boolean>

    @Option(
            option = "record-golden",
            description = "If this option is present, record snapshots and update golden images instead of running an image differ against existing golden images.")
    fun setRecordGoldenOption(value: Boolean) = recordGolden.set(value)

    private val cliParams: MutableMap<String, String> = mutableMapOf()

    @TaskAction
    override fun executeTests() {
        cliParams["previewJar"] = screenshotCliJar.singleFile.absolutePath

        // invoke CLI tool
        val commands = mutableListOf(cliParams["java"],
                "-cp", cliParams["previewJar"], "com.android.screenshot.cli.Main",
                "--client-name", cliParams["client.name"],
                "--client-version", cliParams["client.version"],
                "--jdk-home", cliParams["java.home"],
                "--sdk-home", cliParams["androidsdk"],
                "--extraction-dir", cliParams["extraction.dir"],
                "--jar-location", cliParams["previewJar"],
                "--lint-model", cliParams["lint.model"],
                "--cache-dir", cliParams["lint.cache"],
                "--root-lint-model", cliParams["lint.model"],
                "--output-location", cliParams["output.location"] + "/",
                "--golden-location", cliParams["output.location"] + "/",
                "--file-path", cliParams["sources"]!!.split(",").first())
        if (recordGolden.get()) {
            commands.add("--record-golden")
        }
        val process = ProcessBuilder(
                commands
        ).apply {
            environment().remove("TEST_WORKSPACE")
            redirectErrorStream(true)
            redirectOutput(ProcessBuilder.Redirect.INHERIT)
        }.start()
        process.waitFor()
        setTestEngineParam("process.exit.value", process.exitValue().toString())
    }

    class CreationAction(
            private val androidTestCreationConfig: AndroidTestCreationConfig,
            private val imageOutputDir: File,
            private val ideExtractionDir: File,
            private val lintModelDir: File,
            private val lintCacheDir: File,
            ) :
            VariantTaskCreationAction<
                    PreviewScreenshotTestTask,
                    InstrumentedTestCreationConfig
                    >(androidTestCreationConfig) {

        override val name = computeTaskName(ComponentType.SCREENSHOT_TEST_PREFIX)
        override val type = PreviewScreenshotTestTask::class.java

        override fun configure(task: PreviewScreenshotTestTask) {
            task.recordGolden.convention(false)

            val testedConfig = (creationConfig as? AndroidTestCreationConfig)?.mainVariant
            task.variantName = testedConfig?.name ?: creationConfig.name

            val testedVariant = androidTestCreationConfig.mainVariant
            task.description = "Run screenshot tests for the " + testedVariant.name + " build."

            task.useJUnitPlatform()
            (task.testFramework as JUnitPlatformTestFramework)
                    .options.includeEngines("preview-screenshot-test-engine")

            task.group = JavaBasePlugin.VERIFICATION_GROUP

            task.testClassesDirs = creationConfig.services.fileCollection().apply {
                from(creationConfig
                        .artifacts
                        .forScope(ScopedArtifacts.Scope.PROJECT)
                        .getFinalArtifacts(ScopedArtifact.CLASSES))
                creationConfig.buildConfigCreationConfig?.let {
                    from(it.compiledBuildConfig)
                }
                creationConfig.androidResourcesCreationConfig?.let {
                    from(it.getCompiledRClasses(ConsumedConfigType.RUNTIME_CLASSPATH))
                }
            }

            creationConfig.sources.kotlin?.getVariantSources()?.forEach {
                task.sourceFiles.from(
                    it.asFileTree { task.project.objects.fileTree() }
                )
            }
            task.sourceFiles.disallowChanges()
            task.cliParams["sources"] =
                    task.sourceFiles.files.map { it.absolutePath }.joinToString(",")

            maybeCreateScreenshotTestConfiguration(task.project)
            task.classpath = creationConfig.services.fileCollection().apply {
                from(task.project.configurations.getByName(configurationName))
                from(task.testClassesDirs)
            }

            maybeCreatePreviewlibCliToolConfiguration(task.project)
            task.screenshotCliJar.from(
                    task.project.configurations.getByName(previewlibCliToolConfigurationName)
            )

            task.cliParams["java"] =
                    task.javaLauncher.get().executablePath.asFile.absolutePath
            task.cliParams["java.home"] =
                    task.javaLauncher.get().metadata.installationPath.asFile.absolutePath

            task.cliParams["androidsdk"] =
                    getBuildService(
                            creationConfig.services.buildServiceRegistry,
                            SdkComponentsBuildService::class.java)
                            .get().sdkDirectoryProvider.get().asFile.absolutePath

            task.imageOutputDir.set(imageOutputDir)
            task.imageOutputDir.disallowChanges()
            task.cliParams["output.location"] = imageOutputDir.absolutePath

            task.ideExtractionDir.set(ideExtractionDir)
            task.ideExtractionDir.disallowChanges()
            task.cliParams["extraction.dir"] = ideExtractionDir.absolutePath

            task.lintModelDir.set(lintModelDir)
            task.lintModelDir.disallowChanges()
            task.cliParams["lint.model"] = lintModelDir.absolutePath

            task.lintCacheDir.set(lintCacheDir)
            task.lintCacheDir.disallowChanges()
            task.cliParams["lint.cache"] = lintCacheDir.absolutePath

            task.cliParams["client.name"] = "Android Gradle Plugin"
            task.cliParams["client.version"] = Version.ANDROID_GRADLE_PLUGIN_VERSION

            task.reports.junitXml.outputLocation.set(
                    File(
                            creationConfig
                                    .services
                                    .projectInfo
                                    .getTestResultsFolder(),
                            task.name))
            task.reports.html.outputLocation.set(
                    File(
                            creationConfig
                                    .services
                                    .projectInfo
                                    .getTestReportFolder(),
                            task.name))
        }

        private fun maybeCreateScreenshotTestConfiguration(project: Project) {
            val container = project.configurations
            val dependencies = project.dependencies
            if (container.findByName(configurationName) == null) {
                container.create(configurationName).apply {
                    isVisible = false
                    isTransitive = true
                    isCanBeConsumed = false
                    description = "A configuration to resolve Screenshot Test dependencies."
                }
                val engineVersion = "0.0.1" +
                        if (Version.ANDROID_GRADLE_PLUGIN_VERSION.endsWith("-dev"))
                            "-dev"
                        else
                            "-alpha01"
                dependencies.add(
                        configurationName,
                        "com.android.tools.screenshot:junit-engine:${engineVersion}")
            }
        }

        private fun maybeCreatePreviewlibCliToolConfiguration(project: Project) {
            val container = project.configurations
            val dependencies = project.dependencies
            if (container.findByName(previewlibCliToolConfigurationName) == null) {
                container.create(previewlibCliToolConfigurationName).apply {
                    isVisible = false
                    isTransitive = true
                    isCanBeConsumed = false
                    description = "A configuration to resolve PreviewLib CLI tool dependencies."
                }
                dependencies.add(
                        previewlibCliToolConfigurationName,
                        "com.android.screenshot.cli:screenshot:${Version.ANDROID_TOOLS_BASE_VERSION}")
            }
        }
    }
}

private fun PreviewScreenshotTestTask.setTestEngineParam(key: String, value: String) {
    jvmArgs("-Dcom.android.tools.screenshot.junit.engine.${key}=${value}")
}
