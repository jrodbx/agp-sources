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

import com.android.build.gradle.internal.dsl.ModulePropertyKey.OptionalString
import com.android.build.gradle.internal.packaging.getDefaultDebugKeystoreSigningConfig
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.signing.SigningConfigData
import com.android.build.gradle.internal.signing.SigningConfigDataProvider
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.configureVariantProperties
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.signing.DefaultSigningConfig
import com.android.ide.common.signing.KeystoreHelper
import com.android.tools.build.bundletool.commands.BuildSdkAsarCommand
import com.android.tools.build.bundletool.model.version.BundleToolVersion
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Task to invoke the bundle tool command to create the final ASB bundle for privacy sandbox sdk
 * plugins.
 *
 * Caching disabled by default for this task because the task does very little work, the bundle tool
 * should just package already compiled and packaged stuff.
 */
@DisableCachingByDefault
abstract class GeneratePrivacySandboxAsar : NonIncrementalTask() {

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val asb: RegularFileProperty

    @get:Input
    abstract val bundleToolVersion: Property<String>

    @get:Nested
    abstract val signingConfigDataProvider: Property<SigningConfigDataProvider>

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(WorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.outputFile.set(outputFile)
            it.asb.set(asb)
            it.signingConfigDataProvider.set(signingConfigDataProvider.get().signingConfigData)
        }
    }

    abstract class WorkAction : ProfileAwareWorkAction<WorkAction.Parameters>() {
        abstract class Parameters : ProfileAwareWorkAction.Parameters() {

            abstract val outputFile: RegularFileProperty
            abstract val asb: RegularFileProperty
            abstract val signingConfigDataProvider: Property<SigningConfigData>
        }

        override fun run() {
            val outputFile = parameters.outputFile.get().asFile.toPath()
            val asb = parameters.asb.get().asFile.toPath()
            val signingConfigData = parameters.signingConfigDataProvider.get()
            val certInfo = KeystoreHelper.getCertificateInfo(
                    signingConfigData.storeType,
                    signingConfigData.storeFile,
                    signingConfigData.storePassword,
                    signingConfigData.keyPassword,
                    signingConfigData.keyAlias
            )

            val builder =
                    BuildSdkAsarCommand
                            .builder()
                            .setSdkBundlePath(asb)
                            .setApkSigningCertificate(certInfo.certificate)
                            .setOverwriteOutput(true)
                            .setOutputFile(outputFile)

            // TODO(b/235469089) Call directly once fixed bundle tool is released
            val buildMethod = BuildSdkAsarCommand.Builder::class.java.getDeclaredMethod("build")
            buildMethod.isAccessible = true
            val command = buildMethod.invoke(builder) as BuildSdkAsarCommand

            command.execute()
        }
    }

    class CreationAction(
            private val creationConfig: PrivacySandboxSdkVariantScope
    ) : TaskCreationAction<GeneratePrivacySandboxAsar>() {

        override val name: String = "generatePrivacySandboxSdkArchive"
        override val type: Class<GeneratePrivacySandboxAsar> =
                GeneratePrivacySandboxAsar::class.java

        override fun handleProvider(taskProvider: TaskProvider<GeneratePrivacySandboxAsar>) {
            super.handleProvider(taskProvider)

            val name = "${creationConfig.services.projectInfo.name}.asar"

            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    GeneratePrivacySandboxAsar::outputFile
            ).withName(name).on(PrivacySandboxSdkInternalArtifactType.ASAR)
        }

        override fun configure(task: GeneratePrivacySandboxAsar) {
            task.configureVariantProperties("", task.project.gradle.sharedServices)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                    PrivacySandboxSdkInternalArtifactType.ASB, task.asb
            )

            task.bundleToolVersion.setDisallowChanges(
                    BundleToolVersion.getCurrentVersion().toString()
            )
            val experimentalProps = creationConfig.experimentalProperties
            experimentalProps.finalizeValue()
            val signingConfigProvider = if (
                    OptionalString.ANDROID_PRIVACY_SANDBOX_LOCAL_DEPLOYMENT_SIGNING_NAME.getValue(
                            experimentalProps.get()) != null &&
                    OptionalString.ANDROID_PRIVACY_SANDBOX_LOCAL_DEPLOYMENT_SIGNING_STORE_FILE.getValue(
                            experimentalProps.get()) != null) {
                val localDeploymentSigningConfig = SigningConfigData(
                        OptionalString.ANDROID_PRIVACY_SANDBOX_LOCAL_DEPLOYMENT_SIGNING_NAME.getValue(
                                experimentalProps.get())!!,
                        OptionalString.ANDROID_PRIVACY_SANDBOX_LOCAL_DEPLOYMENT_SIGNING_STORE_TYPE.getValue(
                                experimentalProps.get()),
                        File(OptionalString.ANDROID_PRIVACY_SANDBOX_LOCAL_DEPLOYMENT_SIGNING_STORE_FILE.getValue(
                                experimentalProps.get())!!),
                        OptionalString.ANDROID_PRIVACY_SANDBOX_LOCAL_DEPLOYMENT_SIGNING_STORE_PASSWORD.getValue(
                                experimentalProps.get()) ?: DefaultSigningConfig.DEFAULT_PASSWORD,
                        OptionalString.ANDROID_PRIVACY_SANDBOX_LOCAL_DEPLOYMENT_SIGNING_KEY_ALIAS.getValue(
                                experimentalProps.get()),
                        OptionalString.ANDROID_PRIVACY_SANDBOX_LOCAL_DEPLOYMENT_SIGNING_KEY_PASSOWRD.getValue(
                                experimentalProps.get()) ?: DefaultSigningConfig.DEFAULT_ALIAS
                )
                creationConfig.services.provider { localDeploymentSigningConfig }
            } else {
                getBuildService(
                        creationConfig.services.buildServiceRegistry,
                        AndroidLocationsBuildService::class.java
                ).map {
                    it.getDefaultDebugKeystoreSigningConfig()
                }
            }
            task.signingConfigDataProvider.setDisallowChanges(
                    SigningConfigDataProvider(
                            signingConfigData = signingConfigProvider as Provider<SigningConfigData?>,
                            signingConfigFileCollection = null,
                            signingConfigValidationResultDir = creationConfig.artifacts.get(
                                    InternalArtifactType.VALIDATE_SIGNING_CONFIG)
                    )
            )

        }
    }
}

