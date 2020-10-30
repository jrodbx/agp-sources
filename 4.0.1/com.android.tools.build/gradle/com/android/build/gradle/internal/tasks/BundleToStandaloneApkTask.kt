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
import com.android.build.gradle.internal.res.getAapt2FromMavenAndVersion
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.signing.SigningConfigProvider
import com.android.tools.build.bundletool.commands.BuildApksCommand
import com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode
import com.android.tools.build.bundletool.model.Aapt2Command
import com.android.utils.FileUtils
import com.google.common.util.concurrent.MoreExecutors
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.IOException
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ForkJoinPool
import java.util.zip.ZipInputStream
import javax.inject.Inject

/**
 * Task that generates the standalone from a bundle.
 */
abstract class BundleToStandaloneApkTask : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val bundle: RegularFileProperty

    @get:Input
    lateinit var aapt2Version: String
        private set
    @get:Internal
    abstract val aapt2FromMaven: ConfigurableFileCollection

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
    lateinit var signingConfig: SigningConfigProvider
        private set

    override fun doTaskAction() {
        val config = signingConfig.resolve()
        getWorkerFacadeWithWorkers().use {
            it.submit(
                BundleToolRunnable::class.java,
                Params(
                    bundle.get().asFile,
                    File(aapt2FromMaven.singleFile, SdkConstants.FN_AAPT2),
                    outputFile.get().asFile,
                    tempDirectory,
                    config?.storeFile,
                    config?.storePassword,
                    config?.keyAlias,
                    config?.keyPassword
                )
            )
        }
    }

    private data class Params(
        val bundleFile: File,
        val aapt2File: File,
        val outputFile: File,
        val temporaryDir: File,
        val keystoreFile: File?,
        val keystorePassword: String?,
        val keyAlias: String?,
        val keyPassword: String?
    ) : Serializable

    private class BundleToolRunnable @Inject constructor(private val params: Params): Runnable {
        override fun run() {
            FileUtils.cleanOutputDir(params.outputFile.parentFile)
            FileUtils.cleanOutputDir(params.temporaryDir)

            val outputApksBundle =
                params.temporaryDir.toPath().resolve("universal_bundle.apks")

            generateUniversalApkBundle(outputApksBundle)
            extractUniversalApk(outputApksBundle)
        }

        private fun generateUniversalApkBundle(outputApksBundle: Path) {
            val command = BuildApksCommand
                .builder()
                .setExecutorService(
                    MoreExecutors.listeningDecorator(
                        ForkJoinPool.commonPool()))
                .setBundlePath(params.bundleFile.toPath())
                .setOutputFile(outputApksBundle)
                .setAapt2Command(Aapt2Command.createFromExecutablePath(params.aapt2File.toPath()))
                .setSigningConfiguration(
                    keystoreFile = params.keystoreFile,
                    keystorePassword = params.keystorePassword,
                    keyAlias = params.keyAlias,
                    keyPassword = params.keyPassword
                )

            command.setApkBuildMode(ApkBuildMode.UNIVERSAL)

            command.build().execute()
        }

        private fun extractUniversalApk(outputApksBundle: Path) {
            ZipInputStream(Files.newInputStream(outputApksBundle).buffered()).use { zipInputStream ->
                var found = false
                while (true) {
                    val entry = zipInputStream.nextEntry ?: break
                    if (entry.name.endsWith(".apk")) {
                        if (found) {
                            throw IOException("Expected bundle to contain the single universal apk, but contained multiple: $outputApksBundle")
                        }
                        Files.copy(
                            zipInputStream,
                            params.outputFile.toPath(),
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

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<BundleToStandaloneApkTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("package", "UniversalApk")
        override val type: Class<BundleToStandaloneApkTask>
            get() = BundleToStandaloneApkTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out BundleToStandaloneApkTask>) {
            super.handleProvider(taskProvider)
            // Mirrors logic in OutputFactory.getOutputFileName, but without splits.
            val suffix = if (variantScope.variantDslInfo.isSigningReady) SdkConstants.DOT_ANDROID_PACKAGE else "-unsigned.apk"
            variantScope.artifacts.producesFile(
                InternalArtifactType.UNIVERSAL_APK,
                taskProvider,
                BundleToStandaloneApkTask::outputFile,
                "${variantScope.globalScope.projectBaseName}-${variantScope.variantDslInfo.baseName}-universal$suffix"
            )
        }

        override fun configure(task: BundleToStandaloneApkTask) {
            super.configure(task)

            variantScope.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.INTERMEDIARY_BUNDLE, task.bundle)
            val (aapt2FromMaven,aapt2Version) = getAapt2FromMavenAndVersion(variantScope.globalScope)
            task.aapt2FromMaven.from(aapt2FromMaven)
            task.aapt2Version = aapt2Version
            task.tempDirectory = variantScope.getIncrementalDir(name)
            task.signingConfig = SigningConfigProvider.create(variantScope)
        }
    }
}
