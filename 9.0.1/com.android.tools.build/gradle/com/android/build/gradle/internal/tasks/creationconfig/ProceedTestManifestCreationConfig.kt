/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.creationconfig

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.gradle.internal.component.TaskCreationConfig
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.variant.VariantPathHelper
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import java.io.File

/**
 * Configuration needed to create the [com.android.build.gradle.tasks.ProcessTestManifest],
 * implementations should be partially delegated to [com.android.build.gradle.internal.component.ComponentCreationConfig].
 */
interface ProceedTestManifestCreationConfig: TaskCreationConfig {
    val baseName: String
    val dirName: String
    val paths: VariantPathHelper

    // should be taken from main variant if it's unit test with APK as tested code
    val applicationId: Provider<String>
    val testedApplicationId: Provider<String>
    val namespace: Provider<String>
    val instrumentationRunner: Provider<String>

    val debuggable: Boolean

    val compileSdk: Int?

    val manifestFile: File
    val manifestOverlayFiles: Provider<List<File>>

    // artifacts for main variant that is APK for HostedTests
    val testedApkVariantArtifacts: ArtifactsImpl?

    val minSdk: String
    val targetSdkVersion: String

    val handleProfiling: Provider<Boolean>
    val functionalTest: Provider<Boolean>
    val testLabel: Provider<String>

    val variantDependencies: VariantDependencies

    val useLegacyPackaging: Provider<Boolean>

    val placeholderValues: MapProperty<String, String>
}

