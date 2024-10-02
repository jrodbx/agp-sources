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

import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.io.File

/**
 * Task responsible for creating the asset pack's manifest file from the settings in the module's
 * build.gradle file, as well as the base package name of the project.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.MANIFEST, secondaryTaskCategories = [TaskCategory.SOURCE_GENERATION])
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

    public override fun doTaskAction() {
        workerExecutor.noIsolation().submit(AssetPackManifestGenerationRunnable::class.java) {
            it.manifestFile.set(manifestFile.asFile)
            it.packName.set(packName)
            it.deliveryType.set(deliveryType)
            it.instantDeliveryType.set(instantDeliveryType)
        }
    }
}

//TODO(b/162727093) record worker execution span
abstract class AssetPackManifestGenerationRunnable :
    WorkAction<AssetPackManifestGenerationRunnable.Params> {
    override fun execute() {
        var manifestText =
            ("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" "
                    + "xmlns:dist=\"http://schemas.android.com/apk/distribution\" "
                    + "package=\"basePackage\" " // Currently filled in by a different task.
                    + "split=\"${parameters.packName.get()}\">\n"
                    + "  <dist:module dist:type=\"asset-pack\">\n"
                    + "    <dist:fusing dist:include=\"true\" />"
                    + "    <dist:delivery>\n"
                    + "      <dist:${parameters.deliveryType.get()}/>\n"
                    + "    </dist:delivery>\n")
        if (parameters.instantDeliveryType.orNull != null) {
            manifestText +=
                ("    <dist:instantDelivery>\n"
                 +"      <dist:${parameters.instantDeliveryType.get()}/>\n"
                 +"    </dist:instantDelivery>\n")
        }
        manifestText += ("  </dist:module>\n"
                    + "</manifest>\n")
        parameters.manifestFile.get().writeText(manifestText)
    }

    abstract class Params: WorkParameters {
        abstract val manifestFile: Property<File>
        abstract val packName: Property<String>
        abstract val deliveryType: Property<String>
        abstract val instantDeliveryType: Property<String>
    }
}
