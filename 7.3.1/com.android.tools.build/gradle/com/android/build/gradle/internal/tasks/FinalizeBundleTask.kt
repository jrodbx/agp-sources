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

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.dsl.SigningConfig
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.getOutputPath
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.signing.SigningConfigData
import com.android.build.gradle.internal.signing.SigningConfigDataProvider
import com.android.build.gradle.internal.signing.SigningConfigProviderParams
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.StringOption
import com.android.builder.internal.packaging.AabFlinger
import com.android.ide.common.signing.KeystoreHelper
import com.android.tools.build.bundletool.commands.AddTransparencyCommand
import com.android.utils.FileUtils
import com.google.common.io.ByteStreams
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.work.DisableCachingByDefault

/**
 * Task that copies the bundle file (.aab) to it's final destination and do final touches like:
 * <ul>
 *     <li>Signing the bundle if credentials are available and it's not a debuggable variant.
 * </ul>
 */
@DisableCachingByDefault
abstract class FinalizeBundleTask : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val intermediaryBundleFile: RegularFileProperty

    @get:Nested
    @get:Optional
    var signingConfigData: SigningConfigDataProvider? = null
        private set

    @get:Nested
    @get:Optional
    var codeTransparencySigningConfigData: SigningConfigData? = null
        private set

    @get:Internal
    abstract val tmpDir: DirectoryProperty

    @Suppress("unused")
    @get:Input
    val finalBundleFileName: String
        get() = finalBundleFile.get().asFile.name

    @get:OutputFile
    abstract val finalBundleFile: RegularFileProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(BundleToolRunnable::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.intermediaryBundleFile.set(intermediaryBundleFile)
            it.finalBundleFile.set(finalBundleFile)
            signingConfigData?.convertToParams()?.let { signing ->
                it.signingConfig.set(signing)
            }
            codeTransparencySigningConfigData?.let { signingData ->
                it.codeTransparencySigningConfig.set(SigningConfigProviderParams(signingData, null))
            }
            it.tmpDir.set(tmpDir.orNull)
        }
    }

    abstract class Params : ProfileAwareWorkAction.Parameters() {

        abstract val intermediaryBundleFile: RegularFileProperty
        abstract val finalBundleFile: RegularFileProperty
        abstract val signingConfig: Property<SigningConfigProviderParams>
        abstract val codeTransparencySigningConfig: Property<SigningConfigProviderParams>
        abstract val tmpDir: DirectoryProperty
    }

    abstract class BundleToolRunnable : ProfileAwareWorkAction<Params>() {

        private fun addCodeTransparencySigning(
            inputFile: File,
            outputFile: File,
            codeSigning: SigningConfigData
        ) {
            FileUtils.mkdirs(outputFile.parentFile)
            FileUtils.deleteIfExists(outputFile)
            AddTransparencyCommand.builder()
                .setBundlePath(inputFile.toPath())
                .setOutputPath(outputFile.toPath())
                .setDexMergingChoice(AddTransparencyCommand.DexMergingChoice.CONTINUE)
                .setSignerConfig(codeSigning)
                .build().execute()
        }

        private fun compressBundle(inputFile: File, outputFile: File) {
            ZipInputStream(inputFile.inputStream().buffered()).use { inputStream ->
                ZipOutputStream(outputFile.outputStream().buffered()).use { outputStream ->
                    while (true) {
                        val entry = inputStream.nextEntry ?: break
                        val outEntry = ZipEntry(entry.name)
                        outEntry.time = 0
                        outputStream.putNextEntry(outEntry)
                        ByteStreams.copy(inputStream, outputStream)
                        outputStream.closeEntry()
                    }
                }
            }
        }

        override fun run() {
            FileUtils.deleteIfExists(parameters.finalBundleFile.asFile.get())
            var inputFile = parameters.intermediaryBundleFile.get().asFile
            var cleanup = {}
            try {
                parameters.codeTransparencySigningConfig.orNull?.resolve()
                    ?.let { codeSigningConfig ->
                        val outputFile = FileUtils.join(
                            parameters.tmpDir.get().asFile,
                            inputFile.name
                        )
                        addCodeTransparencySigning(inputFile, outputFile, codeSigningConfig)
                        inputFile = outputFile
                        cleanup = { FileUtils.deleteIfExists(outputFile) }
                    }

                parameters.signingConfig.orNull?.resolve()?.let {
                    val certificateInfo =
                        KeystoreHelper.getCertificateInfo(
                            it.storeType,
                            it.storeFile!!,
                            it.storePassword!!,
                            it.keyPassword!!,
                            it.keyAlias!!
                        )
                    AabFlinger(
                        outputFile = parameters.finalBundleFile.asFile.get(),
                        signerName = it.keyAlias.toUpperCase(Locale.US),
                        privateKey = certificateInfo.key,
                        certificates = listOf(certificateInfo.certificate),
                        minSdkVersion = 18 // So that RSA + SHA256 are used
                    ).use { aabFlinger ->
                        aabFlinger.writeZip(
                            inputFile,
                            Deflater.DEFAULT_COMPRESSION
                        )
                    }
                } ?: run {
                    compressBundle(
                        inputFile,
                        parameters.finalBundleFile.asFile.get()
                    )
                }
            } finally {
                cleanup()
            }
        }
    }

    class CreationForAssetPackBundleAction(
        private val projectServices: ProjectServices,
        private val artifacts: ArtifactsImpl,
        private val signingConfig: SigningConfig,
        private val isSigningReady: Boolean
    ) : TaskCreationAction<FinalizeBundleTask>() {

        override val type = FinalizeBundleTask::class.java
        override val name = "signBundle"

        override fun handleProvider(
            taskProvider: TaskProvider<FinalizeBundleTask>
        ) {
            super.handleProvider(taskProvider)

            val bundleName = "${projectServices.projectInfo.getProjectBaseName()}.aab"
            val location = SingleArtifact.BUNDLE.getOutputPath(artifacts.buildDirectory, "")
            artifacts.setInitialProvider(taskProvider, FinalizeBundleTask::finalBundleFile)
                .atLocation(location.absolutePath)
                .withName(bundleName)
                .on(SingleArtifact.BUNDLE)
        }

        override fun configure(
            task: FinalizeBundleTask
        ) {
            task.configureVariantProperties(variantName = "", projectServices.buildServiceRegistry)
            artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.INTERMEDIARY_BUNDLE,
                task.intermediaryBundleFile
            )

            if (isSigningReady) {
                val signingConfigData =
                    SigningConfigData.fromDslSigningConfig(signingConfig)
                task.signingConfigData = SigningConfigDataProvider(
                    signingConfigData = projectServices.providerFactory.provider { signingConfigData },
                    signingConfigFileCollection = null,
                    signingConfigValidationResultDir = artifacts.get(
                        InternalArtifactType.VALIDATE_SIGNING_CONFIG
                    )
                )
            }
        }
    }

    /**
     * CreateAction for a task that will sign the bundle artifact.
     */
    class CreationAction(creationConfig: ApkCreationConfig) :
        VariantTaskCreationAction<FinalizeBundleTask, ApkCreationConfig>(
            creationConfig
        ) {

        override val name: String
            get() = computeTaskName("sign", "Bundle")

        override val type: Class<FinalizeBundleTask>
            get() = FinalizeBundleTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<FinalizeBundleTask>
        ) {
            super.handleProvider(taskProvider)

            val bundleName =
                "${creationConfig.services.projectInfo.getProjectBaseName()}-${creationConfig.baseName}.aab"
            val apkLocationOverride =
                creationConfig.services.projectOptions.get(StringOption.IDE_APK_LOCATION)
            if (apkLocationOverride == null) {
                creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    FinalizeBundleTask::finalBundleFile
                ).withName(bundleName).on(SingleArtifact.BUNDLE)
            } else {
                creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    FinalizeBundleTask::finalBundleFile
                )
                    .atLocation(
                        FileUtils.join(
                            creationConfig.services.file(apkLocationOverride),
                            creationConfig.dirName
                        ).absolutePath
                    )
                    .withName(bundleName)
                    .on(SingleArtifact.BUNDLE)
            }
        }

        override fun configure(
            task: FinalizeBundleTask
        ) {
            super.configure(task)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.INTERMEDIARY_BUNDLE,
                task.intermediaryBundleFile
            )

            // Don't sign debuggable bundles.
            if (!creationConfig.debuggable) {
                task.signingConfigData =
                    SigningConfigDataProvider.create(creationConfig)

                creationConfig.bundleConfig?.codeTransparency?.signingConfiguration?.let { codeSigning ->
                    if (codeSigning.storeFile != null && codeSigning.keyAlias != null) {
                        task.codeTransparencySigningConfigData =
                            SigningConfigData.fromDslSigningConfig(codeSigning)
                    }
                }
            }

            task.tmpDir.setDisallowChanges(
                creationConfig.paths.intermediatesDir(
                    "tmp",
                    "FinalizeBundle",
                    creationConfig.dirName
                )
            )
        }
    }
}
