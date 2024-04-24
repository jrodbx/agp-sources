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

package com.android.build.gradle.internal.plugins

import com.android.SdkConstants
import com.android.build.api.dsl.AssetPackExtension
import com.android.build.gradle.internal.dsl.AssetPackExtensionImpl
import com.android.build.gradle.internal.tasks.AssetPackManifestGenerationTask
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.Plugin
import org.gradle.api.Project

class AssetPackPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(AssetPackExtension::class.java, "assetPack", AssetPackExtensionImpl::class.java)

        val manifestGenerationTaskProvider = project.tasks.register(
            "generateAssetPackManifest",
            AssetPackManifestGenerationTask::class.java
        ) { manifestGenerationTask ->
            manifestGenerationTask.variantName = ""
            manifestGenerationTask.manifestFile.setDisallowChanges(
                project.layout.buildDirectory.get().dir(
                    SdkConstants.FD_INTERMEDIATES
                ).dir("asset_pack_manifest").file(SdkConstants.FN_ANDROID_MANIFEST_XML)
            )
            manifestGenerationTask.packName.setDisallowChanges(extension.packName)
            manifestGenerationTask.deliveryType.setDisallowChanges(extension.dynamicDelivery.deliveryType)
            manifestGenerationTask.instantDeliveryType.setDisallowChanges(extension.dynamicDelivery.instantDeliveryType)
        }

        val assetPackFiles = project.configurations.maybeCreate("packElements")
        val assetPackManifest = project.configurations.maybeCreate("manifestElements")
        assetPackFiles.isCanBeConsumed = true
        assetPackManifest.isCanBeConsumed = true
        project.artifacts.add("manifestElements", manifestGenerationTaskProvider.flatMap { it.manifestFile })
        project.artifacts.add("packElements", project.layout.projectDirectory.dir("src/main/assets"))
    }
}
