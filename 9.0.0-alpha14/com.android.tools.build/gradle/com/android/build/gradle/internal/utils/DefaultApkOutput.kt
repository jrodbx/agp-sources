/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.internal.utils

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApkInstallGroup
import com.android.build.api.variant.ApkOutput
import com.android.build.api.variant.DeviceSpec
import com.android.build.api.variant.impl.toSharedAndroidVersion
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.options.BooleanOption
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskInputs

class DefaultApkOutput(variant: VariantCreationConfig, val deviceSpec: DeviceSpec) : ApkOutput {
    private val deviceApkOutput: DeviceApkOutput

    init {
        val skipApksViaBundle = variant.services.projectOptions.get(BooleanOption.SKIP_APKS_VIA_BUNDLE_IF_POSSIBLE)
        val hasDynamicFeatures = variant.global.hasDynamicFeatures
        val minSdk = variant.minSdk.toSharedAndroidVersion()
        val variantName = variant.baseName
        val projectPath = variant.services.projectInfo.path
        val privacySandboxApks: FileCollection = variant.variantDependencies
            .getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                AndroidArtifacts.ArtifactScope.ALL,
                AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_EXTRACTED_SDK_APKS)

        val useViaBundleFlow = !skipApksViaBundle || hasDynamicFeatures
        deviceApkOutput = if (useViaBundleFlow) {
            val apkBundle = variant.artifacts.get(InternalArtifactType.APKS_FROM_BUNDLE)
            ViaBundleDeviceApkOutput(
                apkBundle,
                minSdk,
                privacySandboxApks,
                variantName,
                projectPath
            )
        } else {
            val supportedAbis = variant.nativeBuildCreationConfig?.supportedAbis

            DefaultDeviceApkOutput(
                getApkSources(variant, privacySandboxApks), supportedAbis, minSdk,
                variantName, projectPath)
        }
    }
    override val apkInstallGroups: List<ApkInstallGroup>
    get() {
        return deviceApkOutput.getApks(deviceSpec)
    }

    fun setInputs(inputs: TaskInputs) {
        deviceApkOutput.setInputs(inputs, deviceSpec)
    }

    private fun getApkSources(variant: VariantCreationConfig, privacySandboxApks: FileCollection): ApkSources {
        val privacySandboxApkSources =getPrivacySandboxApkSources(variant, privacySandboxApks)
        return ApkSources(
            mainApkArtifacts = variant.artifacts.get(SingleArtifact.APK).map { listOf(it) },
            privacySandboxSdksApksFiles = privacySandboxApkSources.privacySandboxSdksApksFiles,
            additionalSupportedSdkApkSplits = privacySandboxApkSources.additionalSupportedSdkApkSplits,
            privacySandboxSdkSplitApksForLegacy = privacySandboxApkSources.privacySandboxSdkSplitApksForLegacy,
            dexMetadataDirectory = variant.artifacts.get(InternalArtifactType.DEX_METADATA_DIRECTORY))
    }

    private fun getPrivacySandboxApkSources(variant: VariantCreationConfig, privacySandboxApks: FileCollection) : PrivacySandboxApkSources {
        return PrivacySandboxApkSources(
            privacySandboxSdksApksFiles = privacySandboxApks,
            additionalSupportedSdkApkSplits = variant.artifacts.get(
                InternalArtifactType.USES_SDK_LIBRARY_SPLIT_FOR_LOCAL_DEPLOYMENT),
            privacySandboxSdkSplitApksForLegacy = variant.artifacts.get(InternalArtifactType.EXTRACTED_SDK_APKS))
    }
}
