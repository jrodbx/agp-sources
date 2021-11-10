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

import com.android.SdkConstants
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.services.getAapt2Executable
import com.android.build.gradle.internal.signing.SigningConfigDataProvider
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.tools.build.bundletool.commands.BuildApksCommand
import com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode
import com.android.tools.build.bundletool.androidtools.Aapt2Command
import com.android.utils.FileUtils
import com.google.common.util.concurrent.MoreExecutors
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ForkJoinPool
import java.util.zip.ZipInputStream

/**
 * Task that generates the standalone from a bundle.
 */
@DisableCachingByDefault
abstract class BundleToStandaloneApkTask : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val bundle: RegularFileProperty

    @get:Nested
    abstract val aapt2: Aapt2Input

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:OutputDirectory
    val outputDirectory: File
        get() = outputFile.get().asFile.parentFile!!

    @get:Input
    val fileName: String
        get() = outputFile.get().asFile.name

    private lateinit var tempDirectory: File

    @get:Nested
    lateinit var signingConfigData: SigningConfigDataProvider
        private set

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(BundleToolRunnable::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.bundleFile.set(bundle)
            it.aapt2File.set(aapt2.getAapt2Executable().toFile())
            it.outputFile.set(outputFile)
            it.temporaryDir.set(tempDirectory)
            signingConfigData.resolve()?.let { config ->
                it.keystoreFile.set(config.storeFile)
                it.keystorePassword.set(config.storePassword)
                it.keyAlias.set(config.keyAlias)
                it.keyPassword.set(config.keyPassword)
            }
        }
    }

    abstract class Params : ProfileAwareWorkAction.Parameters() {
        abstract val bundleFile: RegularFileProperty
        abstract val aapt2File: Property<File>
        abstract val outputFile: RegularFileProperty
        abstract val temporaryDir: Property<File>
        abstract val keystoreFile: Property<File>
        abstract val keystorePassword: Property<String>
        abstract val keyAlias: Property<String>
        abstract val keyPassword: Property<String>
    }

    abstract class BundleToolRunnable : ProfileAwareWorkAction<Params>() {
        override fun run() {
            FileUtils.cleanOutputDir(parameters.outputFile.asFile.get().parentFile)
            FileUtils.cleanOutputDir(parameters.temporaryDir.get())

            val outputApksBundle =
                parameters.temporaryDir.get().toPath().resolve("universal_bundle.apks")

            generateUniversalApkBundle(outputApksBundle)
            extractUniversalApk(outputApksBundle)
        }

        private fun generateUniversalApkBundle(outputApksBundle: Path) {
            val command = BuildApksCommand
                .builder()
                .setExecutorService(MoreExecutors.listeningDecorator(ForkJoinPool.commonPool()))
                .setBundlePath(parameters.bundleFile.asFile.get().toPath())
                .setOutputFile(outputApksBundle)
                .setAapt2Command(
                    Aapt2Command.createFromExecutablePath(
                        parameters.aapt2File.get().toPath()
                    )
                )
                .setSigningConfiguration(
                    keystoreFile = parameters.keystoreFile.orNull,
                    keystorePassword = parameters.keystorePassword.orNull,
                    keyAlias = parameters.keyAlias.orNull,
                    keyPassword = parameters.keyPassword.orNull
                )

            command.setApkBuildMode(ApkBuildMode.UNIVERSAL)

            command.build().execute()
        }

        private fun extractUniversalApk(outputApksBundle: Path) {
            ZipInputStream(
                Files.newInputStream(outputApksBundle).buffered()
            ).use { zipInputStream ->
                var found = false
                while (true) {
                    val entry = zipInputStream.nextEntry ?: break
                    if (entry.name.endsWith(".apk")) {
                        if (found) {
                            throw IOException("Expected bundle to contain the single universal apk, but contained multiple: $outputApksBundle")
                        }
                        Files.copy(
                            zipInputStream,
                            parameters.outputFile.asFile.get().toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                        found = true
                    }
                }
                if (!found) {
                    throw IOException("Expected bundle to contain the single universal apk, but contained none: $outputApksBundle")
                }
            }
        }
    }

    class CreationAction(creationConfig: ApkCreationConfig) :
        VariantTaskCreationAction<BundleToStandaloneApkTask, ApkCreationConfig>(
            creationConfig
        ) {

        override val name: String
            get() = computeTaskName("package", "UniversalApk")
        override val type: Class<BundleToStandaloneApkTask>
            get() = BundleToStandaloneApkTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<BundleToStandaloneApkTask>
        ) {
            super.handleProvider(taskProvider)
            // Mirrors logic in OutputFactory.getOutputFileName, but without splits.
            val suffix =

                if (creationConfig.signingConfig?.isSigningReady() == true) SdkConstants.DOT_ANDROID_PACKAGE else "-unsigned.apk"
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                BundleToStandaloneApkTask::outputFile
            )
                .withName("${creationConfig.services.projectInfo.getProjectBaseName()}-${creationConfig.baseName}-universal$suffix")
                .on(InternalArtifactType.UNIVERSAL_APK)
        }

        override fun configure(
            task: BundleToStandaloneApkTask
        ) {
            super.configure(task)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.INTERMEDIARY_BUNDLE, task.bundle
            )
            creationConfig.services.initializeAapt2Input(task.aapt2)
            task.tempDirectory = creationConfig.paths.getIncrementalDir(name)
            task.signingConfigData = SigningConfigDataProvider.create(creationConfig)
        }
    }
}
