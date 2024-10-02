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

import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.configureVariantProperties
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

// Disable caching since we only create a small text file.
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.MANIFEST, secondaryTaskCategories = [TaskCategory.SOURCE_GENERATION])
abstract class PrivacySandboxSdkManifestGeneratorTask: NonIncrementalTask() {

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    override fun doTaskAction() {
        // Include empty application tag to ensure that test projects can build with an empty manifest
        // TODO(b/237279793): should the manifest merger handle this?
        outputFile.get().asFile.writeText("""
            <manifest
                xmlns:android="http://schemas.android.com/apk/res/android">
                <application />
            </manifest>
        """.trimIndent()
        )
    }

    class CreationAction(val creationConfig: PrivacySandboxSdkVariantScope) :
        TaskCreationAction<PrivacySandboxSdkManifestGeneratorTask>() {

        override val name: String
            get() = "mainManifestGenerator"

        override val type: Class<PrivacySandboxSdkManifestGeneratorTask>
            get() = PrivacySandboxSdkManifestGeneratorTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<PrivacySandboxSdkManifestGeneratorTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider, PrivacySandboxSdkManifestGeneratorTask::outputFile
            )
                .withName(FN_ANDROID_MANIFEST_XML)
                .on(PrivacySandboxSdkInternalArtifactType.SANDBOX_MANIFEST)
        }

        override fun configure(task: PrivacySandboxSdkManifestGeneratorTask) {
            task.configureVariantProperties("", task.project.gradle.sharedServices)
        }
    }
}
