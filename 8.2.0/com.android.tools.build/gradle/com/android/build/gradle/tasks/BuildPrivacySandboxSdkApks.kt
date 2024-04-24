/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.apksig.apk.ApkUtils
import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl.Companion.saveAll
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.options.StringOption
import com.android.utils.FileUtils
import com.android.zipflinger.ZipArchive
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.bufferedWriter

/**
 * Task to extract the privacy sandbox SDK APKs for this app
 */
@DisableCachingByDefault(because="Task only extracts zips")
abstract class BuildPrivacySandboxSdkApks : NonIncrementalTask() {

    @get:Classpath // Classpath as the output is brittle to the order
    abstract val sdkApksArchives: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val deviceConfig: RegularFileProperty

    @get:OutputDirectory
    abstract val sdkApks: DirectoryProperty

    @get:OutputFile
    abstract val ideModelFile: RegularFileProperty

    override fun doTaskAction() {
        val logLevel = if( deviceConfig.isPresent) LogLevel.INFO else LogLevel.LIFECYCLE
        if (sdkApksArchives.isEmpty) {
            logger.log(logLevel, "There are no privacy sandbox SDK dependencies for ${projectPath.get()} $variantName ")
            return
        }
        val outputDirectory = sdkApks.get().asFile.toPath()
        val ideModel = ideModelFile.get().asFile.toPath()
        FileUtils.cleanOutputDir(outputDirectory.toFile())
        Files.deleteIfExists(ideModel)

        val artifacts = mutableListOf<BuiltArtifactsImpl>()

        // TODO(b/235469089) use bundle tool here, using the device config supplied
        forEachInputFile(
                inputFiles = sdkApksArchives.files.map { it.toPath() },
                outputDirectory = outputDirectory,
        ) { archive, subDirectory ->
            Files.createDirectory(subDirectory)
            val outputFile = subDirectory.resolve("standalone.apk")
            ZipArchive(archive).use {
                it.getInputStream("standalones/standalone.apk").use { inputStream ->
                    Files.copy(inputStream, outputFile)
                    ZipArchive(outputFile).use { apk ->
                        // TODO(b/241077141): We can potentially fetch the applicationId from
                        // "toc.pb" file but they have different values.
                        // Binary manifest returns  <pkg_name>_<encoded_version>
                        // whereas toc.pb returns   <pkg_name> only.
                        artifacts.add(
                            BuiltArtifactsImpl(
                                artifactType = InternalArtifactType.EXTRACTED_APKS_FROM_PRIVACY_SANDBOX_SDKs,
                                applicationId = ApkUtils.getPackageNameFromBinaryAndroidManifest(apk.getContent("AndroidManifest.xml")),
                                variantName = "",
                                elements = listOf(
                                    BuiltArtifactImpl.make(
                                        outputFile = outputFile.toString()
                                    )
                                )
                            )
                        )
                        logger.log(
                            logLevel,
                            "Extracted sandbox SDK APK for ${projectPath.get()} $variantName: $outputFile"
                        )
                    }
                }
            }
        }

        artifacts.saveAll(ideModel)
    }
    companion object {

        fun forEachInputFile(
                inputFiles: Iterable<Path>,
                outputDirectory: Path,
                action: (input: Path, outputSubDirectory: Path) -> Unit,
        ) {
            val usedOutputNames = mutableSetOf<String>()
            for (inputFile in inputFiles) {
                val key = inputFile.fileName.toString().substringBeforeLast('.')
                var index = 0
                var candidateSubDirectoryName = key
                while (!usedOutputNames.add(candidateSubDirectoryName)) {
                    index++
                    candidateSubDirectoryName = key + "_" + index
                }
                action(inputFile, outputDirectory.resolve(candidateSubDirectoryName))
            }
        }
    }


    class CreationAction(creationConfig: ApkCreationConfig) : VariantTaskCreationAction<BuildPrivacySandboxSdkApks, ApkCreationConfig>(
            creationConfig,
            dependsOnPreBuildTask = false
    ) {

        override val name: String
            get() = getTaskName(creationConfig)

        override val type: Class<BuildPrivacySandboxSdkApks>
            get() = BuildPrivacySandboxSdkApks::class.java

        override fun handleProvider(taskProvider: TaskProvider<BuildPrivacySandboxSdkApks>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    BuildPrivacySandboxSdkApks::sdkApks
            ).on(InternalArtifactType.EXTRACTED_APKS_FROM_PRIVACY_SANDBOX_SDKs)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    BuildPrivacySandboxSdkApks::ideModelFile
            ).withName("ide_model.json").on(InternalArtifactType.EXTRACTED_APKS_FROM_PRIVACY_SANDBOX_SDKs_IDE_MODEL)
        }

        override fun configure(task: BuildPrivacySandboxSdkApks) {
            super.configure(task)
            task.sdkApksArchives.fromDisallowChanges(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_APKS
                    )
            )
            val deviceConfigPath = creationConfig.services.projectOptions.get(StringOption.IDE_APK_SELECT_CONFIG)
            if (deviceConfigPath != null) {
                task.deviceConfig.set(File(deviceConfigPath))
            }
            task.deviceConfig.disallowChanges()
        }

        companion object {
            fun getTaskName(creationConfig: ComponentCreationConfig) = creationConfig.computeTaskName("buildPrivacySandboxSdkApksFor")
        }
    }
}
