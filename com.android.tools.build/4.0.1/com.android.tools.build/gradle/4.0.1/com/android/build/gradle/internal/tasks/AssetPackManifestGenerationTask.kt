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

package com.android.build.gradle.internal.tasks

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import java.io.File
import java.io.Serializable
import javax.inject.Inject

/**
 * Task responsible for creating the asset pack's manifest file from the settings in the module's
 * build.gradle file, as well as the base package name of the project.
 */
abstract class AssetPackManifestGenerationTask : NonIncrementalTask() {
    /**
     * The generated manifest file for the asset pack module.
     */
    @get:OutputFile abstract val manifestFile: RegularFileProperty
    /**
     * The name of the asset pack. Used as the splitName in the manifest.
     */
    @get:Input abstract val packName: Property<String>
    /**
     * The dynamic delivery type that will be used for the asset pack.
     * The valid options are fast-follow, install-time, or on-demand.
     */
    @get:Input abstract val deliveryType: Property<String>
    /**
     * The dynamic delivery type that will be used for the asset pack in an instant app context.
     * The valid options are fast-follow or on-demand.
     */
    @get:Input
    @get:Optional
    abstract val instantDeliveryType: Property<String>

    override val enableGradleWorkers: Property<Boolean> = project.objects.property(Boolean::class.java).value(true)

    public override fun doTaskAction() {
        getWorkerFacadeWithWorkers().use {
            it.submit(
                AssetPackManifestGenerationRunnable::class.java,
                AssetPackManifestGenerationRunnable.Params(
                    manifestFile = manifestFile.get().asFile,
                    packName = packName.get(),
                    deliveryType = deliveryType.get(),
                    instantDeliveryType = instantDeliveryType.orNull
                )
            )
        }
    }
}

class AssetPackManifestGenerationRunnable @Inject constructor(private val params: Params) : Runnable {
    override fun run() {
        var manifestText =
            ("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" "
                    + "xmlns:dist=\"http://schemas.android.com/apk/distribution\" "
                    + "package=\"basePackage\" " // Currently filled in by a different task.
                    + "split=\"${params.packName}\">\n"
                    + "  <dist:module dist:type=\"asset-pack\">\n"
                    + "    <dist:fusing dist:include=\"true\" />"
                    + "    <dist:delivery>\n"
                    + "      <dist:${params.deliveryType}/>\n"
                    + "    </dist:delivery>\n")
        if (params.instantDeliveryType != null) {
            manifestText +=
                ("    <dist:instantDelivery>\n"
                 +"      <dist:${params.instantDeliveryType}/>\n"
                 +"    </dist:instantDelivery>\n")
        }
        manifestText += ("  </dist:module>\n"
                    + "</manifest>\n")
        params.manifestFile.writeText(manifestText)
    }

    class Params(
        val manifestFile: File,
        val packName: String,
        val deliveryType: String,
        val instantDeliveryType: String?
    ) : Serializable
}
