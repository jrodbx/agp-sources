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
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.core.ComponentType
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File
import org.gradle.api.tasks.VerificationTask
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

/**
 * Invoke Render CLI tool
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class PreviewScreenshotRenderTask : NonIncrementalTask(), VerificationTask {

    companion object {

        const val previewlibCliToolConfigurationName = "_internal-screenshot-test-task-previewlib-cli"
    }

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
    abstract val outputDir: DirectoryProperty

    @get:Internal
    abstract val layoutlibDir: ConfigurableFileCollection

    @get:Classpath
    abstract val testClassesDir: ConfigurableFileCollection

    private val cliParams: MutableMap<String, String> = mutableMapOf()

    override fun doTaskAction() {
        cliParams["previewJar"] = screenshotCliJar.singleFile.absolutePath
        cliParams["layoutlib.dir"] = layoutlibDir.singleFile.toPath().toString()
        val testClassesDependencies = testClassesDir.files
            .filter { it.exists() && it.isDirectory}.map { it.absolutePath + '/' }.joinToString(";")
        cliParams["output.location"] = outputDir.get().asFile.absolutePath

        // invoke CLI tool
        val process = ProcessBuilder(
            mutableListOf(cliParams["java"],
                "-cp", cliParams["previewJar"], "com.android.screenshot.cli.Main",
                "--client-name", cliParams["client.name"],
                "--client-version", cliParams["client.version"],
                "--jdk-home", cliParams["java.home"],
                "--sdk-home", cliParams["androidsdk"],
                "--lint-model", cliParams["lint.model"],
                "--cache-dir", cliParams["lint.cache"],
                "--root-lint-model", cliParams["lint.model"],
                "--output-location", cliParams["output.location"] + "/",
                "--file-path", cliParams["sources"]!!.split(",").first(),
                "--additional-deps", listOf(cliParams["additional.deps"]!!, testClassesDependencies).joinToString(File.pathSeparator),
                "--layoutlib-dir", cliParams["layoutlib.dir"],)
        ).apply {
            environment().remove("TEST_WORKSPACE")
            redirectErrorStream(true)
            redirectOutput(ProcessBuilder.Redirect.INHERIT)
        }.start()
        process.waitFor()

        if (process.exitValue() == 4) {
            throw GradleException("Invalid arguments to the rendering tool")
        }

    }

    class CreationAction(
        androidTestCreationConfig: AndroidTestCreationConfig,
        private val layoutlibDir: FileCollection,
        private val lintModelDir: File,
        private val lintCacheDir: File,
        private val additionalDependencyPaths: List<String>
    ) :
        VariantTaskCreationAction<
                PreviewScreenshotRenderTask,
                InstrumentedTestCreationConfig
                >(androidTestCreationConfig) {

        override val name = computeTaskName(ComponentType.PREVIEW_SCREENSHOT_RENDER_PREFIX)
        override val type = PreviewScreenshotRenderTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<PreviewScreenshotRenderTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(taskProvider) { it.outputDir }
                .on(InternalArtifactType.SCREENSHOTS_RENDERED)
        }

        override fun configure(task: PreviewScreenshotRenderTask) {
            val testedConfig = (creationConfig as? AndroidTestCreationConfig)?.mainVariant

            creationConfig.sources.kotlin?.getVariantSources()?.forEach {
                task.sourceFiles.from(
                    it.asFileTree { task.project.objects.fileTree() }
                )
            }
            task.sourceFiles.disallowChanges()
            task.cliParams["sources"] =
                task.sourceFiles.files.map { it.absolutePath }.joinToString(",")

            maybeCreatePreviewlibCliToolConfiguration(task.project)
            task.screenshotCliJar.from(
                task.project.configurations.getByName(previewlibCliToolConfigurationName)
            )

            val toolchain = task.project.extensions.getByType(JavaPluginExtension::class.java).toolchain
            val service = task.project.extensions.getByType(JavaToolchainService::class.java)
            // TODO(b/295886078) Investigate error handling needed for getting JavaLauncher.
            val javaLauncher = try {
                service.launcherFor(toolchain).get()
            } catch (ex: GradleException) {
                // If the JDK that was set is not available get the JDK 11 as a default
                service.launcherFor { toolchainSpec ->
                    toolchainSpec.languageVersion.set(JavaLanguageVersion.of(11))
                }.get()
            }
            task.cliParams["java"] =
                javaLauncher.executablePath.asFile.absolutePath
            task.cliParams["java.home"] =
                javaLauncher.metadata.installationPath.asFile.absolutePath

            task.cliParams["androidsdk"] =
                getBuildService(
                    creationConfig.services.buildServiceRegistry,
                    SdkComponentsBuildService::class.java)
                    .get().sdkDirectoryProvider.get().asFile.absolutePath

            task.lintModelDir.set(lintModelDir)
            task.lintModelDir.disallowChanges()
            task.cliParams["lint.model"] = lintModelDir.absolutePath

            task.lintCacheDir.set(lintCacheDir)
            task.lintCacheDir.disallowChanges()
            task.cliParams["lint.cache"] = lintCacheDir.absolutePath

            task.cliParams["client.name"] = "Android Gradle Plugin"
            task.cliParams["client.version"] = Version.ANDROID_GRADLE_PLUGIN_VERSION

            task.testClassesDir.from(creationConfig.services.fileCollection().apply {from(creationConfig
                .artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
                .getFinalArtifacts(ScopedArtifact.CLASSES)) })
            task.testClassesDir.disallowChanges()

            task.cliParams["additional.deps"] = additionalDependencyPaths.joinToString (";")

            task.layoutlibDir.from(layoutlibDir)
            task.layoutlibDir.disallowChanges()
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
