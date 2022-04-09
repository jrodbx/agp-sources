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

import com.android.SdkConstants
import com.android.build.gradle.internal.dependency.GenericTransformParameters
import com.android.bundle.SdkMetadataOuterClass.SdkMetadata
import com.android.tools.build.bundletool.model.RuntimeEnabledSdkVersionEncoder
import com.android.zipflinger.ZipArchive
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import kotlin.io.path.nameWithoutExtension

/**
 * Generates privacy sandbox manifest snippets from the ASAR to be merged into the app manifest.
 */
@CacheableTransform
abstract class AsarToManifestSnippetTransform: TransformAction<GenericTransformParameters> {

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    abstract fun getInputArtifact(): Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val inputFile = getInputArtifact().get().asFile.toPath()
        val outputFile = outputs.file(inputFile.fileName.nameWithoutExtension
                + SdkConstants.PRIVACY_SANDBOX_SDK_DEPENDENCY_MANIFEST_SNIPPET_NAME_SUFFIX).toPath()

        ZipArchive(inputFile).use {
            it.getInputStream("SdkMetadata.pb").use { protoBytes ->
                val metadata = SdkMetadata.parseFrom(protoBytes)
                val encodedVersion = RuntimeEnabledSdkVersionEncoder.encodeSdkMajorAndMinorVersion(
                    metadata.sdkVersion.major,
                    metadata.sdkVersion.minor
                )
                outputFile.toFile().writeText("""
                    <manifest
                        xmlns:android="http://schemas.android.com/apk/res/android">
                        <application>
                            <uses-sdk-library
                                android:name="${metadata.packageName}"
                                android:certDigest="${metadata.certificateDigest}"
                                android:versionMajor="$encodedVersion" />
                        </application>
                    </manifest>
                """.trimIndent())
            }
        }

    }
}
