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

import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.configureVariantProperties
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.internal.packaging.IncrementalPackager
import com.android.bundle.Config
import com.android.bundle.SdkBundleConfigProto
import com.android.bundle.SdkModulesConfigOuterClass
import com.android.bundle.SdkModulesConfigOuterClass.SdkModulesConfig
import com.android.tools.build.bundletool.commands.BuildSdkBundleCommand
import com.android.tools.build.bundletool.model.version.BundleToolVersion
import com.google.common.collect.ImmutableList
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

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

    @get:Nested
    abstract val sdkBundleProperties: SdkBundleProperties

    @get:Nested
    abstract val sdkDependencies: ListProperty<SdkBundleDependencyEntry>

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

        val sdkBundleConfig =
            SdkBundleConfigProto
                .SdkBundleConfig
                .newBuilder()
                .addAllSdkDependencies(
                    sdkDependencies.get().map { dep ->
                        SdkBundleConfigProto.SdkBundle.newBuilder()
                            .setPackageName(dep.packageName.get())
                            .setVersionMajor(dep.version.major.get())
                            .setVersionMinor(dep.version.minor.get())
                            .setBuildTimeVersionPatch(dep.version.patch.get())
                            .setCertificateDigest(dep.certificateDigest.get())
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

    class CreationAction(
        private val creationConfig: PrivacySandboxSdkVariantScope
    ): TaskCreationAction<PackagePrivacySandboxSdkBundle>() {

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
            task.configureVariantProperties("", task.project.gradle.sharedServices)

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

            // TODO: Add DSL for the following
            task.sdkDependencies.setDisallowChanges(emptyList())

            task.interfaceDescriptors.setDisallowChanges(
                    creationConfig.artifacts.get(PrivacySandboxSdkInternalArtifactType.STUB_JAR))
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

    interface SdkBundleDependencyEntry {
        @get:Input
        val packageName: Property<String>

        @get:Nested
        val version: SdkVersion

        @get:Input
        val certificateDigest: Property<String>
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

