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

import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.gradle.internal.AndroidJarInput
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.initialize
import com.android.build.gradle.internal.packaging.getDefaultDebugKeystoreSigningConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.res.namespaced.Aapt2LinkRunnable
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.services.getLeasingAapt2
import com.android.build.gradle.internal.signing.SigningConfigData
import com.android.build.gradle.internal.signing.SigningConfigDataProvider
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.core.ComponentTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.builder.internal.packaging.ApkFlinger
import com.android.bundle.RuntimeEnabledSdkConfigProto
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig
import com.android.ide.common.signing.KeystoreHelper
import com.android.ide.common.xml.XmlPrettyPrinter
import com.android.tools.build.apkzlib.sign.SigningOptions
import com.android.tools.build.apkzlib.zfile.ApkCreatorFactory
import com.android.tools.build.apkzlib.zfile.NativeLibrariesPackagingMode
import com.android.tools.build.bundletool.model.RuntimeEnabledSdkVersionEncoder
import com.android.tools.build.bundletool.transparency.CodeTransparencyCryptoUtils
import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.collect.ImmutableList
import com.google.common.io.Files
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.nio.file.Path
import java.util.zip.Deflater
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * task for generating an asset metadata file required for Play rubidium libraries when an
 * application is built with Privacy Sandbox support and support for legacy devices,
 * and the full APK split for
 */
@BuildAnalyzer(TaskCategory.APK_PACKAGING)
@CacheableTask
abstract class GenerateAdditionalApkSplitForDeploymentViaApk : NonIncrementalTask() {

    /**
     * The split APK to be installed with the main app to bring in the privacy sandbox SDK dependency
     */
    @get:OutputDirectory
    abstract val usesSdkLibrarySplit: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val runtimeConfigFile: RegularFileProperty

    @get:Input
    abstract val applicationId: Property<String>

    @get:Input
    abstract val apkName: Property<String>

    @get:Input
    @get:Optional
    abstract val versionCode: Property<Int?>

    @get:Nested
    abstract val androidJarInput: AndroidJarInput

    @get:Nested
    abstract val aapt2: Aapt2Input

    @get:Nested
    abstract val apkSigningConfig: Property<SigningConfigDataProvider>

    @get:Nested
    abstract val sdkSigningConfig: Property<SigningConfigDataProvider>

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(WorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.usesSdkLibrarySplitDir.set(usesSdkLibrarySplit)
            it.runtimeConfigFile.set(runtimeConfigFile)
            it.applicationId.set(applicationId)
            it.versionCode.set(versionCode)
            it.androidJar.set(androidJarInput.getAndroidJar().get())
            it.aapt2.set(aapt2)
            it.apkSigningConfig.set(apkSigningConfig.get().signingConfigData)
            it.sdkSigningConfig.set(sdkSigningConfig.get().signingConfigData)
            it.tempDir.set(temporaryDir)
            it.apkName.set(apkName)
        }
    }

    abstract class Params : ProfileAwareWorkAction.Parameters() {

        abstract val usesSdkLibrarySplitDir: DirectoryProperty
        abstract val runtimeConfigFile: RegularFileProperty
        abstract val applicationId: Property<String>
        abstract val versionCode: Property<Int?>
        abstract val androidJar: RegularFileProperty
        abstract val aapt2: Property<Aapt2Input>
        abstract val apkSigningConfig: Property<SigningConfigData>
        abstract val sdkSigningConfig: Property<SigningConfigData>
        abstract val tempDir: DirectoryProperty
        abstract val apkName: Property<String>
    }

    abstract class WorkAction : ProfileAwareWorkAction<Params>() {

        override fun run() {

            val privacySandboxRuntimeConfig =
                    parameters.runtimeConfigFile.get().asFile.inputStream().buffered()
                            .use { input -> RuntimeEnabledSdkConfig.parseFrom(input) }

            val sdkSigningConfigData =
                    parameters.sdkSigningConfig.get() ?: throw IllegalStateException()
            val sdkCertInfo = KeystoreHelper.getCertificateInfo(
                    sdkSigningConfigData.storeType,
                    sdkSigningConfigData.storeFile,
                    sdkSigningConfigData.storePassword,
                    sdkSigningConfigData.keyPassword,
                    sdkSigningConfigData.keyAlias
            )
            val certificateDigest =
                    CodeTransparencyCryptoUtils.getCertificateFingerprint(sdkCertInfo.certificate)
                            .replace(' ', ':')

            val manifestContent =
                    privacySandboxRuntimeConfig.runtimeEnabledSdkList.joinToString("\n") {
                        """        <uses-sdk-library
            android:name="${it.packageName}"
            android:certDigest="$certificateDigest"
            android:versionMajor="${it.encodedVersion}" />"""
                    }

            val outputApk = File(parameters.usesSdkLibrarySplitDir.get().asFile, parameters.apkName.get())
            generateAdditionalSplitApk(
                    outputApkPath = outputApk.toPath(),
                    applicationId = parameters.applicationId.get(),
                    versionCode = parameters.versionCode.orNull,
                    files = mapOf(),
                    manifestContent = manifestContent,
                    signingConfigData = parameters.apkSigningConfig.get(),
                    tempDir = parameters.tempDir.get().asFile,
                    aapt2 = parameters.aapt2.get(),
                    androidJar = parameters.androidJar.get().asFile,
            )

            BuiltArtifactsImpl(
                    artifactType = InternalArtifactType.USES_SDK_LIBRARY_SPLIT_FOR_LOCAL_DEPLOYMENT,
                    applicationId = parameters.applicationId.get(),
                    variantName = "",
                    elements = listOf(
                            BuiltArtifactImpl.make(
                                    outputFile = outputApk.absolutePath.toString()
                            )
                    )
            ).saveToDirectory(parameters.usesSdkLibrarySplitDir.get().asFile)
        }

        private val RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk.encodedVersion: Int
            get() = RuntimeEnabledSdkVersionEncoder
                    .encodeSdkMajorAndMinorVersion(versionMajor, versionMinor)
    }

    class CreationAction(creationConfig: ApplicationCreationConfig) :
            VariantTaskCreationAction<GenerateAdditionalApkSplitForDeploymentViaApk, ApplicationCreationConfig>(
                    creationConfig) {

        override val name: String
            get() = computeTaskName(creationConfig)

        override val type: Class<GenerateAdditionalApkSplitForDeploymentViaApk>
            get() = GenerateAdditionalApkSplitForDeploymentViaApk::class.java

        override fun handleProvider(taskProvider: TaskProvider<GenerateAdditionalApkSplitForDeploymentViaApk>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    GenerateAdditionalApkSplitForDeploymentViaApk::usesSdkLibrarySplit
            ).on(InternalArtifactType.USES_SDK_LIBRARY_SPLIT_FOR_LOCAL_DEPLOYMENT)
        }

        override fun configure(task: GenerateAdditionalApkSplitForDeploymentViaApk) {
            super.configure(task)
            task.runtimeConfigFile.setDisallowChanges(
                    creationConfig.artifacts.get(
                            InternalArtifactType.PRIVACY_SANDBOX_SDK_RUNTIME_CONFIG_FILE)
            )
            task.applicationId.setDisallowChanges(creationConfig.applicationId)
            task.versionCode.setDisallowChanges(creationConfig.outputs.getMainSplit().versionCode)
            task.androidJarInput.initialize(creationConfig)
            creationConfig.services.initializeAapt2Input(task.aapt2)
            task.apkSigningConfig.setDisallowChanges(SigningConfigDataProvider.create(creationConfig))
            val defaultDebugConfig: Provider<SigningConfigData> = getBuildService(
                    creationConfig.services.buildServiceRegistry,
                    AndroidLocationsBuildService::class.java
            ).map(AndroidLocationsBuildService::getDefaultDebugKeystoreSigningConfig)
            val experimentalPropSigningConfig: Provider<SigningConfigData>? =
                    SigningConfigData.fromExperimentalPropertiesSigningConfig(creationConfig.experimentalProperties)
                            ?.let {
                                creationConfig.services.provider { it }
                            }
            task.sdkSigningConfig.setDisallowChanges(
                    SigningConfigDataProvider(
                            signingConfigData = experimentalPropSigningConfig
                                    ?: defaultDebugConfig as Provider<SigningConfigData?>,
                            signingConfigFileCollection = null,
                            signingConfigValidationResultDir = null
                    )
            )
            task.apkName.set(
                    creationConfig.services.projectInfo.getProjectBaseName().map {
                        "$it-${creationConfig.baseName}-injected-privacy-sandbox.apk"
                    }
            )
        }

        companion object {

            fun computeTaskName(component: ApplicationCreationConfig): String {
                return component.computeTaskName("generate",
                        "AdditionalSplitForPrivacySandboxDeployment")
            }
        }
    }

}

internal fun generateAdditionalSplitApk(
        outputApkPath: Path,
        applicationId: String,
        versionCode: Int?,
        files: Map<String, File>,
        manifestContent: String = "",
        signingConfigData: SigningConfigData,
        tempDir: File,
        aapt2: Aapt2Input,
        androidJar: File,
): Path {

    val certificateInfo = KeystoreHelper.getCertificateInfo(
            signingConfigData.storeType,
            signingConfigData.storeFile,
            signingConfigData.storePassword,
            signingConfigData.keyPassword,
            signingConfigData.keyAlias)
    val signingOptions = SigningOptions.builder()
            .setKey(certificateInfo.key)
            .setCertificates(certificateInfo.certificate)
            // ECDH encryption key signing requires minSdk 18, privacy sandbox will
            // never be supported below API 19.
            .setMinSdkVersion(19)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .build()
    val creationData =
            ApkCreatorFactory.CreationData.builder()
                    .setNativeLibrariesPackagingMode(NativeLibrariesPackagingMode.COMPRESSED)
                    .setApkPath(outputApkPath.toFile())
                    .setSigningOptions(signingOptions)
                    .build()

    val apkDir =
            FileUtils.join(tempDir, "tmp-apk-compat-privacy-sandbox-split").apply { mkdirs() }
    val manifest = File(apkDir, "AndroidManifest.xml")
    val manifestText =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<manifest \n" +
                    " xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    " android:isFeatureSplit=\"true\"\n" +
                    (versionCode?.let { " android:versionCode=\"$versionCode\"\n" } ?: "") +
                    " split=\"${
                        outputApkPath.toFile().nameWithoutExtension.replace("-",
                                "")
                    }\"\n" +
                    " package=\"$applicationId\">\n" +
                    "<application android:hasCode=\"false\">$manifestContent</application>\n" +
                    "</manifest>"
    val documentFactory = DocumentBuilderFactory.newInstance()
    val xmlDocBuilder: DocumentBuilder = documentFactory.newDocumentBuilder()
    val manifestDocument = xmlDocBuilder.parse(InputSource(StringReader(manifestText)))
    val prettyPrintManifest = XmlPrettyPrinter.prettyPrint(manifestDocument, true)
    Files.asCharSink(manifest, Charsets.UTF_8)
            .write(prettyPrintManifest)

    val config = AaptPackageConfig(
            androidJarPath = androidJar.absolutePath,
            generateProtos = false,
            manifestFile = manifest,
            options = AaptOptions(null, null),
            resourceOutputApk = outputApkPath.toFile(),
            componentType = ComponentTypeImpl.BASE_APK,
            packageId = null,
            resourceDirs = ImmutableList.of()
    )
    aapt2.getLeasingAapt2()
            .link(config,
                    LoggerWrapper(Logging.getLogger(Aapt2LinkRunnable::class.java)))

    ApkFlinger(creationData,
            Deflater.BEST_SPEED,
            true,
            enableV3Signing = true,
            enableV4Signing = true).use { apkCreator ->
        files.forEach { (path, file) ->
            apkCreator.writeFile(file, path)
        }

    }
    return outputApkPath
}
