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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.builder.symbols.writeRPackages
import com.android.bundle.RuntimeEnabledSdkConfigProto
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig
import com.android.bundle.SdkMetadataOuterClass.SdkMetadata
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider

@CacheableTask
abstract class GeneratePrivacySandboxSdkRuntimeConfigFile : NonIncrementalTask() {

    @get:OutputFile
    abstract val generatedRPackage: RegularFileProperty

    @get:OutputFile
    abstract val privacySandboxSdkRuntimeConfigFile: RegularFileProperty

    @get:Classpath // Classpath as the output is brittle to the order
    abstract val sdkArchiveMetadata: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val featureSetMetadata: RegularFileProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(WorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.privacySandboxSdkRuntimeConfigFile.set(privacySandboxSdkRuntimeConfigFile)
            it.generatedRPackage.set(generatedRPackage)
            it.sdkArchiveMetadata.from(sdkArchiveMetadata)
            it.featureSetMetadata.set(featureSetMetadata)
            it.variantName.set(variantName)
        }
    }

    abstract class WorkAction: ProfileAwareWorkAction<WorkAction.Parameters>() {

        abstract class Parameters: ProfileAwareWorkAction.Parameters() {
            abstract val privacySandboxSdkRuntimeConfigFile: RegularFileProperty
            abstract val generatedRPackage: RegularFileProperty
            abstract val sdkArchiveMetadata: ConfigurableFileCollection
            abstract val featureSetMetadata: RegularFileProperty
            abstract val variantName: Property<String>
        }

        override fun run() {
            val privacySandboxSdkPackages = parameters.sdkArchiveMetadata.files.map {
                it.inputStream().buffered().use { input -> SdkMetadata.parseFrom(input) }
            }
            val featureSetMetadata =
                    FeatureSetMetadata.load(parameters.featureSetMetadata.get().asFile).toBuilder()
            val configProto = RuntimeEnabledSdkConfig.newBuilder().also { runtimeEnabledSdkConfig ->
                for (sdkMetadata in privacySandboxSdkPackages) {

                    val resPackageId =
                            featureSetMetadata.addFeatureSplit(
                                    modulePath = PRIVACY_SANDBOX_PREFIX + sdkMetadata.packageName,
                                    featureName = PRIVACY_SANDBOX_PREFIX + sdkMetadata.packageName,
                                    packageName = sdkMetadata.packageName,
                            )
                    runtimeEnabledSdkConfig.addRuntimeEnabledSdkBuilder().apply {
                        packageName = sdkMetadata.packageName
                        versionMajor = sdkMetadata.sdkVersion.major
                        versionMinor = sdkMetadata.sdkVersion.minor
                        buildTimeVersionPatch = sdkMetadata.sdkVersion.patch
                        certificateDigest = sdkMetadata.certificateDigest
                        resourcesPackageId = resPackageId
                    }
                }
            }.build()

            val configFile = parameters.privacySandboxSdkRuntimeConfigFile.get().asFile
            configFile.outputStream().buffered().use { configProto.writeTo(it) }
            writeRPackages(
                    packageNameToId = configProto.runtimeEnabledSdkList.associateBy({it.packageName}) { it.resourcesPackageId.shl(24) },
                    outJar = parameters.generatedRPackage.get().asFile.toPath(),
            )
            val analyticsData: GradleBuildVariant = GradleBuildVariant.newBuilder().also { variant ->
                variant.setPrivacySandboxDependenciesInfo(GradleBuildVariant.PrivacySandboxDependenciesInfo.newBuilder().also { privacySandboxDependenciesInfo ->
                    for (sdk in configProto.runtimeEnabledSdkList) {
                        privacySandboxDependenciesInfo.addSdk(sdk.toAnalytics())
                    }
                })
            }.build()
            parameters.analyticsService.get().mergeToVariantBuilder(parameters.projectPath.get(), parameters.variantName.get(), analyticsData)
        }

        private fun RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk.toAnalytics() = GradleBuildVariant.PrivacySandboxDependenciesInfo.RuntimeEnabledSdk.newBuilder().also { analytics ->
            analytics.packageName = packageName
            analytics.versionMajor = versionMajor
            analytics.versionMinor = versionMinor
            analytics.buildTimeVersionPatch = buildTimeVersionPatch
        }.build()

        companion object {
            private const val PRIVACY_SANDBOX_PREFIX = "PrivacySandboxSdk_"
        }
    }

    class CreationAction(applicationCreationConfig: ConsumableCreationConfig) :
            VariantTaskCreationAction<GeneratePrivacySandboxSdkRuntimeConfigFile, ConsumableCreationConfig>(
                    applicationCreationConfig,
                    dependsOnPreBuildTask = false) {

        override val name: String
            get() = computeTaskName("generate", "PrivacySandboxSdkRuntimeConfigFile")
        override val type: Class<GeneratePrivacySandboxSdkRuntimeConfigFile>
            get() = GeneratePrivacySandboxSdkRuntimeConfigFile::class.java

        override fun handleProvider(taskProvider: TaskProvider<GeneratePrivacySandboxSdkRuntimeConfigFile>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(taskProvider, GeneratePrivacySandboxSdkRuntimeConfigFile::generatedRPackage)
                    .on(InternalArtifactType.PRIVACY_SANDBOX_SDK_R_PACKAGE_JAR)
            creationConfig.artifacts.setInitialProvider(taskProvider, GeneratePrivacySandboxSdkRuntimeConfigFile::privacySandboxSdkRuntimeConfigFile)
                    .on(InternalArtifactType.PRIVACY_SANDBOX_SDK_RUNTIME_CONFIG_FILE)
        }

        override fun configure(task: GeneratePrivacySandboxSdkRuntimeConfigFile) {
            super.configure(task)
            task.sdkArchiveMetadata.fromDisallowChanges(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_METADATA_PROTO
                    )
            )
            creationConfig.artifacts.setTaskInputToFinalProduct(InternalArtifactType.FEATURE_SET_METADATA, task.featureSetMetadata)
        }
    }
}
