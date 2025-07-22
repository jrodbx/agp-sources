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

import com.android.build.gradle.internal.dependency.GenericTransformParameters
import com.android.build.gradle.internal.privaysandboxsdk.extractPrivacySandboxPermissions
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.tasks.PrivacySandboxSdkGenerateJarStubsTask
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.nio.file.Files
import java.util.zip.ZipFile

@CacheableTransform
abstract class AsarTransform : TransformAction<AsarTransform.Parameters> {

    interface Parameters : GenericTransformParameters {
        @get:Input
        val targetType: Property<ArtifactType>
    }

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputArtifact
    abstract val asar: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val asarFile = asar.get().asFile
        ZipFile(asarFile).use {
            when (val targetType = parameters.targetType.get()) {
                ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_METADATA_PROTO -> {
                    val outputFile =
                            outputs.file(asarFile.nameWithoutExtension + "_SdkMetadata.pb").toPath()
                    it.getInputStream(it.getEntry("SdkMetadata.pb")).use { protoBytes ->
                        Files.copy(protoBytes, outputFile)
                    }
                }
                ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_INTERFACE_DESCRIPTOR -> {
                    val sdkInterfaceDescriptor = outputs.file(
                            PrivacySandboxSdkGenerateJarStubsTask.privacySandboxSdkStubJarFilename)
                    val entry =
                            it.getEntry(PrivacySandboxSdkGenerateJarStubsTask.privacySandboxSdkStubJarFilename)
                    it.getInputStream(entry).use { jar ->
                        sdkInterfaceDescriptor.writeBytes(jar.readAllBytes())
                    }
                }
                // The ASAR contributes to the main manifest potentially permissions,
                // which are marked with tools:requiredByPrivacySandboxSdk="true"
                // Bundle tool will then remove those for base APKs that support privacy sandbox
                ArtifactType.MANIFEST -> {
                    val manifest = outputs.file("${asarFile.nameWithoutExtension}_AndroidManifest.xml").toPath()
                    it.getInputStream(it.getEntry("AndroidManifest.xml")).use { asarManifest ->
                        val newManifestString = extractPrivacySandboxPermissions(asarManifest)
                        Files.writeString(manifest, newManifestString)
                    }
                }
                else -> {
                    error("There is not yet support from transforming the asar format to $targetType")
                }
            }
        }
    }

    companion object {

        val supportedAsarTransformTypes = listOf(
                ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_METADATA_PROTO,
                ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_INTERFACE_DESCRIPTOR,
                ArtifactType.MANIFEST,
        )
    }
}
