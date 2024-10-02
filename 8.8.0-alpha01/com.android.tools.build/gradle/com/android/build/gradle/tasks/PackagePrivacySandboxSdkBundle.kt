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

import com.android.SdkConstants
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.tasks.factory.AndroidVariantTaskCreationAction
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.internal.packaging.IncrementalPackager
import com.android.bundle.Config
import com.android.bundle.SdkBundleConfigProto
import com.android.bundle.SdkMetadataOuterClass
import com.android.bundle.SdkMetadataOuterClass.SdkMetadata
import com.android.bundle.SdkModulesConfigOuterClass
import com.android.bundle.SdkModulesConfigOuterClass.SdkModulesConfig
import com.android.tools.build.bundletool.commands.BuildSdkBundleCommand
import com.android.tools.build.bundletool.model.version.BundleToolVersion
import com.google.common.collect.ImmutableList
import org.gradle.api.GradleException
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.zip.ZipFile

/**
 * Task to invoke the bundle tool command to create the final ASB bundle for privacy sandbox sdk
 * plugins.
 *
 * Caching disabled by default for this task because the task does very little work, the bundle tool
 * should just package already compiled and packaged stuff.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.BUNDLE_PACKAGING)
abstract class PackagePrivacySandboxSdkBundle: NonIncrementalTask() {

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val baseModuleZip: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val appMetadata: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val interfaceDescriptors: RegularFileProperty

    @get:Input
    abstract val bundleToolVersion: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val validatedSdkConfigurations: DirectoryProperty

    @get:Nested
    abstract val sdkBundleProperties: SdkBundleProperties

    @get:Classpath
    abstract val sdkArchives: ConfigurableFileCollection

    @get:Classpath
    abstract val requiredSdkArchives: ConfigurableFileCollection

    override fun doTaskAction() {
        val modulesPaths = ImmutableList.of(baseModuleZip.get().asFile.toPath())

        if (sdkBundleProperties.packageName.get().isEmpty()) {
            throw GradleException("The identity for this Privacy Sandbox SDK bundle needs to be set through android.bundle.applicationId")
        }
        if (sdkBundleProperties.version.major.get() < 0 || sdkBundleProperties.version.minor.get() < 0 || sdkBundleProperties.version.patch.get() < 0) {
            throw GradleException("version needs to bet set through android.bundle.setVersion")
        }
        if (sdkBundleProperties.sdkProviderClassName.get().isEmpty()) {
            throw GradleException("sdkProviderClassName needs to bet set through android.bundle.sdkProviderClassName")
        }

        val sdkModulesConfig =
            SdkModulesConfig
                .newBuilder()
                .setBundletool(
                    Config.Bundletool.newBuilder().setVersion(bundleToolVersion.get()).build()
                ).setSdkPackageName(sdkBundleProperties.packageName.get())
                .setSdkVersion(
                    SdkModulesConfigOuterClass.RuntimeEnabledSdkVersion.newBuilder()
                        .setMajor(sdkBundleProperties.version.major.get())
                        .setMinor(sdkBundleProperties.version.minor.get())
                        .setPatch(sdkBundleProperties.version.patch.get())
                        .build()
                ).setSdkProviderClassName(
                    sdkBundleProperties.sdkProviderClassName.get()
                ).setCompatSdkProviderClassName(
                    sdkBundleProperties.compatSdkProviderClassName.get()
                ).build()

        val requiredSdkPackageNames = requiredSdkArchives.files
                .map(this::getSdkMetadataFromAsar)
                .map(SdkMetadata::getPackageName)
                .toSet()

        val sdkBundleConfig =
                SdkBundleConfigProto
                        .SdkBundleConfig
                        .newBuilder()
                        .addAllSdkDependencies(
                                sdkArchives.files
                                        .map(this::getSdkMetadataFromAsar)
                                        .map { sdkMetadata ->
                                            SdkBundleConfigProto.SdkBundle.newBuilder()
                                                    .setPackageName(sdkMetadata.packageName)
                                                    .setVersionMajor(sdkMetadata.sdkVersion.major)
                                                    .setVersionMinor(sdkMetadata.sdkVersion.minor)
                                                    .setBuildTimeVersionPatch(sdkMetadata.sdkVersion.patch)
                                                    .setCertificateDigest(sdkMetadata.certificateDigest)
                                                    .setDependencyType(
                                                            if (sdkMetadata.packageName in requiredSdkPackageNames) {
                                                                SdkBundleConfigProto.SdkDependencyType.SDK_DEPENDENCY_TYPE_REQUIRED
                                                            } else {
                                                                SdkBundleConfigProto.SdkDependencyType.SDK_DEPENDENCY_TYPE_OPTIONAL
                                                            }
                                                    )
                                                    .build()
                                        }
                        ).build()

        val command =
            BuildSdkBundleCommand
                    .builder()
                    .setModulesPaths(modulesPaths)
                    .setOutputPath(outputFile.get().asFile.toPath())
                    .setSdkBundleConfig(sdkBundleConfig)
                    .setSdkModulesConfig(sdkModulesConfig)
                    .setSdkInterfaceDescriptors(interfaceDescriptors.get().asFile.toPath())

        command.addMetadataFile(
            "com.android.tools.build.gradle",
            IncrementalPackager.APP_METADATA_FILE_NAME,
            appMetadata.asFile.get().toPath()
        )

        command.build().execute()
    }

    private fun getSdkMetadataFromAsar(sdkArchiveFile: File): SdkMetadataOuterClass.SdkMetadata {
        if (sdkArchiveFile.extension != SdkConstants.EXT_ASAR) {
            throw RuntimeException("${sdkArchiveFile.absolutePath} is not a valid Privacy Sandbox SDK archive file." +
                    "File extension must be '${SdkConstants.DOT_ASAR}' rather than '.${sdkArchiveFile.extension}'")
        }
        ZipFile(sdkArchiveFile).use { openAsar ->
            val sdkMetadataEntry = openAsar.getEntry("SdkMetadata.pb")
            val sdkMetadataBytes = openAsar.getInputStream(sdkMetadataEntry).readBytes()
            return sdkMetadataBytes.inputStream()
                    .buffered()
                    .use { input -> SdkMetadataOuterClass.SdkMetadata.parseFrom(input) }
        }
    }

    class CreationAction(
        private val creationConfig: PrivacySandboxSdkVariantScope
    ): AndroidVariantTaskCreationAction<PackagePrivacySandboxSdkBundle>() {

        override val name: String = "packagePrivacySandboxSdkBundle"
        override val type: Class<PackagePrivacySandboxSdkBundle> = PackagePrivacySandboxSdkBundle::class.java

        override fun handleProvider(taskProvider: TaskProvider<PackagePrivacySandboxSdkBundle>) {
            super.handleProvider(taskProvider)

            val name = "${creationConfig.services.projectInfo.name}.asb"

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                PackagePrivacySandboxSdkBundle::outputFile
            ).withName(name).on(PrivacySandboxSdkInternalArtifactType.ASB)
        }

        override fun configure(task: PackagePrivacySandboxSdkBundle) {
            super.configure(task)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                PrivacySandboxSdkInternalArtifactType.MODULE_BUNDLE, task.baseModuleZip
            )

            creationConfig.artifacts.setTaskInputToFinalProduct(
                PrivacySandboxSdkInternalArtifactType.APP_METADATA,
                task.appMetadata
            )

            task.bundleToolVersion.setDisallowChanges(
                BundleToolVersion.getCurrentVersion().toString()
            )

            creationConfig.bundle.let { bundle ->
                task.sdkBundleProperties.apply {
                    packageName.setDisallowChanges(bundle.applicationId ?: "")
                    version.major.setDisallowChanges(bundle.version?.major ?: -1)
                    version.minor.setDisallowChanges(bundle.version?.minor ?: -1)
                    version.patch.setDisallowChanges(bundle.version?.patch ?: -1)
                    sdkProviderClassName.setDisallowChanges(bundle.sdkProviderClassName ?: "")
                    compatSdkProviderClassName.setDisallowChanges(bundle.compatSdkProviderClassName ?: "")
                }
            }

            task.requiredSdkArchives.setFrom(
                    task.project.configurations.getByName("requiredSdk").incoming.artifactView {
                        it.attributes.attribute(AndroidArtifacts.ARTIFACT_TYPE,
                                AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_ARCHIVE.type)
                    }.files
            )

            task.sdkArchives.setFrom(
                    creationConfig.dependencies.getArtifactFileCollection(
                            Usage.JAVA_RUNTIME,
                            creationConfig.mergeSpec,
                            AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_ARCHIVE))

            task.interfaceDescriptors.setDisallowChanges(
                    creationConfig.artifacts.get(PrivacySandboxSdkInternalArtifactType.STUB_JAR))

            task.validatedSdkConfigurations.setDisallowChanges(
                    creationConfig.artifacts.get(PrivacySandboxSdkInternalArtifactType.VALIDATE_PRIVACY_SANDBOX_SDK_CONFIGURATIONS)
            )
        }
    }

    interface SdkVersion {
        @get:Input
        val major: Property<Int>

        @get:Input
        val minor: Property<Int>

        @get:Input
        val patch: Property<Int>
    }

    interface SdkBundleProperties {
        @get:Input
        val packageName: Property<String>

        @get:Nested
        val version: SdkVersion

        @get:Input
        val sdkProviderClassName: Property<String>

        @get:Input
        val compatSdkProviderClassName: Property<String>
    }
}

