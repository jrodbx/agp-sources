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

import com.android.apksig.ApkSigner
import com.android.build.api.artifact.ArtifactType
import com.android.build.api.component.impl.ComponentImpl
import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.signing.SigningConfigDataProvider
import com.android.build.gradle.internal.signing.SigningConfigProviderParams
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.StringOption
import com.android.builder.internal.packaging.AabFlinger
import com.android.ide.common.signing.KeystoreHelper
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

/**
 * Task that copies the bundle file (.aab) to it's final destination and do final touches like:
 * <ul>
 *     <li>Signing the bundle if credentials are available and it's not a debuggable variant.
 * </ul>
 */
abstract class FinalizeBundleTask : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val intermediaryBundleFile: RegularFileProperty

    @get:Nested
    @get:Optional
    var signingConfigData: SigningConfigDataProvider? = null
        private set

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
        }
    }

    abstract class Params: ProfileAwareWorkAction.Parameters() {
        abstract val intermediaryBundleFile: RegularFileProperty
        abstract val finalBundleFile: RegularFileProperty
        abstract val signingConfig: Property<SigningConfigProviderParams>
    }

    abstract class BundleToolRunnable : ProfileAwareWorkAction<Params>() {

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
                ).use {
                    it.writeZip(
                            parameters.intermediaryBundleFile.get().asFile,
                            Deflater.DEFAULT_COMPRESSION
                    )
                }
            } ?: run {
                compressBundle(parameters.intermediaryBundleFile.asFile.get(),
                        parameters.finalBundleFile.asFile.get())
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

            val bundleName = "${creationConfig.globalScope.projectBaseName}-${creationConfig.baseName}.aab"
            val apkLocationOverride = creationConfig.services.projectOptions.get(StringOption.IDE_APK_LOCATION)
            if (apkLocationOverride == null) {
                creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    FinalizeBundleTask::finalBundleFile
                ).withName(bundleName).on(ArtifactType.BUNDLE)
            } else {
                creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    FinalizeBundleTask::finalBundleFile)
                    .atLocation(FileUtils.join(
                        creationConfig.services.file(apkLocationOverride),
                        creationConfig.dirName).absolutePath)
                    .withName(bundleName)
                    .on(ArtifactType.BUNDLE)
            }
        }

        override fun configure(
            task: FinalizeBundleTask
        ) {
            super.configure(task)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.INTERMEDIARY_BUNDLE,
                task.intermediaryBundleFile)

            // Don't sign debuggable bundles.
            if (!creationConfig.debuggable) {
                task.signingConfigData =
                    SigningConfigDataProvider.create(creationConfig as ComponentImpl)
            }
        }
    }
}
