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

import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.services.getAapt2Executable
import com.android.build.gradle.internal.signing.SigningConfigDataProvider
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.toImmutableSet
import com.android.build.gradle.options.BooleanOption
import com.android.buildanalyzer.common.TaskCategory
import com.android.bundle.RuntimeEnabledSdkConfigProto
import com.android.ide.common.signing.CertificateInfo
import com.android.ide.common.signing.KeystoreHelper
import com.android.tools.build.bundletool.commands.BuildApksCommand
import com.android.tools.build.bundletool.androidtools.Aapt2Command
import com.android.tools.build.bundletool.transparency.CodeTransparencyCryptoUtils
import com.android.utils.FileUtils
import com.google.common.util.concurrent.MoreExecutors
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.concurrent.ForkJoinPool

/**
 * Task that generates APKs from a bundle. All the APKs are bundled into a single zip file.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.APK_PACKAGING)
abstract class BundleToApkTask : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val bundle: RegularFileProperty

    @get:Nested
    abstract val aapt2: Aapt2Input

    @get:Nested
    lateinit var signingConfigData: SigningConfigDataProvider
        private set

    @get:Classpath
    abstract val androidPrivacySandboxSdkArchives: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val enableLocalTesting: Property<Boolean>

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(BundleToolRunnable::class.java) {
            it.initializeFromBaseTask(this)
            it.bundleFile.set(bundle)
            it.aapt2File.set(aapt2.getAapt2Executable().toFile())
            it.androidPrivacySandboxSdkArchives.from(androidPrivacySandboxSdkArchives)
            it.outputFile.set(outputFile)
            signingConfigData.resolve()?.let { config ->
                it.keystoreType.set(config.storeType)
                it.keystoreFile.set(config.storeFile)
                it.keystorePassword.set(config.storePassword)
                it.keyAlias.set(config.keyAlias)
                it.keyPassword.set(config.keyPassword)

            }
            it.enableLocalTesting.set(enableLocalTesting)
        }
    }

    abstract class Params : ProfileAwareWorkAction.Parameters() {
        abstract val bundleFile: RegularFileProperty
        abstract val aapt2File: Property<File>
        abstract val androidPrivacySandboxSdkArchives: ConfigurableFileCollection
        abstract val outputFile: RegularFileProperty
        abstract val keystoreType: Property<String>
        abstract val keystoreFile: Property<File>
        abstract val keystorePassword: Property<String>
        abstract val keyAlias: Property<String>
        abstract val keyPassword: Property<String>
        abstract val enableLocalTesting: Property<Boolean>
    }

    abstract class BundleToolRunnable : ProfileAwareWorkAction<Params>() {
        override fun run() {
            FileUtils.deleteIfExists(parameters.outputFile.asFile.get())

            val command = BuildApksCommand
                .builder()
                .setExecutorService(MoreExecutors.listeningDecorator(ForkJoinPool.commonPool()))
                .setBundlePath(parameters.bundleFile.asFile.get().toPath())
                .setOutputFile(parameters.outputFile.asFile.get().toPath())
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
                .setLocalTestingMode(parameters.enableLocalTesting.get())
		.setEnableApkSerializerWithoutBundleRecompression(true)

            if (!parameters.androidPrivacySandboxSdkArchives.isEmpty) {
                val asars = parameters.androidPrivacySandboxSdkArchives.files.map { it.toPath() }

                val cert = KeystoreHelper.getCertificateInfo(
                        parameters.keystoreType.get(),
                        parameters.keystoreFile.orNull,
                        parameters.keystorePassword.orNull,
                        parameters.keyPassword.orNull,
                        parameters.keyAlias.orNull
                )

                command.setRuntimeEnabledSdkArchivePaths(asars.toImmutableSet())
                command.setLocalDeploymentRuntimeEnabledSdkConfig(
                        getLocalDeploymentRuntimeSdkConfig(cert))
            }

            command.build().execute()
        }

        private fun getLocalDeploymentRuntimeSdkConfig(cert: CertificateInfo)
                : RuntimeEnabledSdkConfigProto.LocalDeploymentRuntimeEnabledSdkConfig {

            val certificateDigest =
                    CodeTransparencyCryptoUtils.getCertificateFingerprint(cert.certificate)
                            .replace(' ', ':')

            return RuntimeEnabledSdkConfigProto.LocalDeploymentRuntimeEnabledSdkConfig
                    .newBuilder().apply {
                        certificateOverrides =
                                RuntimeEnabledSdkConfigProto.CertificateOverrides.newBuilder()
                                        .apply {
                                            defaultCertificateOverride = certificateDigest
                                        }.build()
                    }.build()
        }
    }

    class CreationAction(creationConfig: ApkCreationConfig) :
        VariantTaskCreationAction<BundleToApkTask, ApkCreationConfig>(
            creationConfig
        ) {

        override val name: String
            get() = computeTaskName("makeApkFromBundleFor")
        override val type: Class<BundleToApkTask>
            get() = BundleToApkTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<BundleToApkTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                BundleToApkTask::outputFile
            ).withName("bundle.apks").on(InternalArtifactType.APKS_FROM_BUNDLE)
        }

        override fun configure(
            task: BundleToApkTask
        ) {
            super.configure(task)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.INTERMEDIARY_BUNDLE, task.bundle
            )
            creationConfig.services.initializeAapt2Input(task.aapt2, task)
            if (creationConfig.privacySandboxCreationConfig != null) {
                task.androidPrivacySandboxSdkArchives.fromDisallowChanges(
                        creationConfig.variantDependencies.getArtifactFileCollection(
                                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                                AndroidArtifacts.ArtifactScope.ALL,
                                AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_ARCHIVE
                        )
                )
            } else {
                task.androidPrivacySandboxSdkArchives.disallowChanges()
            }
            task.signingConfigData = SigningConfigDataProvider.create(creationConfig)
            task.enableLocalTesting.set(creationConfig.services.projectOptions[BooleanOption.ENABLE_LOCAL_TESTING])
        }
    }
}
