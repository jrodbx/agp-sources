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
import com.android.tools.build.bundletool.model.Aapt2Command
import com.android.utils.FileUtils
import com.google.common.util.concurrent.MoreExecutors
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.Serializable
import java.util.concurrent.ForkJoinPool
import javax.inject.Inject

/**
 * Task that generates APKs from a bundle. All the APKs are bundled into a single zip file.
 */
abstract class BundleToApkTask : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val bundle: RegularFileProperty

    @get:Input
    lateinit var aapt2Version: String
        private set
    @get:Internal
    abstract val aapt2FromMaven: ConfigurableFileCollection

    @get:Nested
    lateinit var signingConfig: SigningConfigProvider
        private set

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    override fun doTaskAction() {
        val config = signingConfig.resolve()
        getWorkerFacadeWithWorkers().use {
            it.submit(
                BundleToolRunnable::class.java,
                Params(
                    bundle.get().asFile,
                    File(aapt2FromMaven.singleFile, SdkConstants.FN_AAPT2),
                    outputFile.get().asFile,
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
        val keystoreFile: File?,
        val keystorePassword: String?,
        val keyAlias: String?,
        val keyPassword: String?
    ) : Serializable

    private class BundleToolRunnable @Inject constructor(private val params: Params): Runnable {
        override fun run() {
            FileUtils.deleteIfExists(params.outputFile)

            val command = BuildApksCommand
                .builder()
                .setExecutorService(MoreExecutors.listeningDecorator(ForkJoinPool.commonPool()))
                .setBundlePath(params.bundleFile.toPath())
                .setOutputFile(params.outputFile.toPath())
                .setAapt2Command(Aapt2Command.createFromExecutablePath(params.aapt2File.toPath()))
                .setSigningConfiguration(
                    keystoreFile = params.keystoreFile,
                    keystorePassword = params.keystorePassword,
                    keyAlias = params.keyAlias,
                    keyPassword = params.keyPassword
                )

            command.build().execute()
        }
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<BundleToApkTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("makeApkFromBundleFor")
        override val type: Class<BundleToApkTask>
            get() = BundleToApkTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out BundleToApkTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesFile(
                InternalArtifactType.APKS_FROM_BUNDLE,
                taskProvider,
                BundleToApkTask::outputFile,
                "bundle.apks"
            )
        }

        override fun configure(task: BundleToApkTask) {
            super.configure(task)

            variantScope.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.INTERMEDIARY_BUNDLE, task.bundle)
            val (aapt2FromMaven, aapt2Version) = getAapt2FromMavenAndVersion(variantScope.globalScope)
            task.aapt2FromMaven.from(aapt2FromMaven)
            task.aapt2Version = aapt2Version
            task.signingConfig = SigningConfigProvider.create(variantScope)
        }
    }
}
