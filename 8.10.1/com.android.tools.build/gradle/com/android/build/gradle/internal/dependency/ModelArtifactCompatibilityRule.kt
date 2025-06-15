/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.internal.publishing.AndroidArtifacts
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.attributes.CompatibilityCheckDetails
import javax.inject.Inject

class ModelArtifactCompatibilityRule @Inject constructor(val privacySandboxSdkSupportEnabled: Boolean): AttributeCompatibilityRule<String> {

    override fun execute(details: CompatibilityCheckDetails<String>) {
        val producerValue = details.producerValue
        when (details.consumerValue) {
            producerValue -> details.compatible()
            AndroidArtifacts.ArtifactType.AAR_OR_JAR.type -> {
                when (producerValue) {
                    AndroidArtifacts.ArtifactType.AAR.type -> details.compatible()
                    AndroidArtifacts.ArtifactType.JAR.type -> details.compatible()
                    AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_ARCHIVE.type -> details.compatible()
                }
            }
            AndroidArtifacts.ArtifactType.EXPLODED_AAR_OR_ASAR_INTERFACE_DESCRIPTOR.type -> {
                when (producerValue) {
                    AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_INTERFACE_DESCRIPTOR.type ->
                        if (privacySandboxSdkSupportEnabled)
                            details.compatible()
                    AndroidArtifacts.ArtifactType.EXPLODED_AAR.type -> details.compatible()
                }
            }
            AndroidArtifacts.ArtifactType.LOCAL_EXPLODED_AAR_FOR_LINT.type -> {
                when (producerValue) {
                    AndroidArtifacts.ArtifactType.EXPLODED_AAR.type -> details.compatible()
                }
            }
        }
    }

    companion object {
        fun setUp(attributesSchema: AttributesSchema, privacySandboxSdkSupportEnabled: Boolean) {
            val strategy = attributesSchema.attribute(ARTIFACT_TYPE_ATTRIBUTE)
            strategy.compatibilityRules.add(ModelArtifactCompatibilityRule::class.java) { config -> config.setParams(privacySandboxSdkSupportEnabled) }
        }
    }
}
