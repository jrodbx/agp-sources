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

package com.android.build.gradle.internal.variant

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.gradle.internal.SdkComponentsBuildService.VersionedSdkLoader
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.errors.SyncIssueReporter
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.model.v2.ide.ProjectType
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

/**
 * Configuration object for the model builder. This contains everything that they need, and nothing
 * else.
 *
 * This will contain variant information, and their inputs. It can also compute the default variant
 * to be used during sync.
 *
 * It will contain some global DSL elements that needs to be access to put them in the model.
 *
 * Finally, this contains some utility objects, like ProjectOptions
 */
interface VariantModel {
    val projectType: ProjectType
    val projectTypeV1: Int

    val inputs: VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>

    /**
     * the main variants. This is the output of the plugin (apk, aar, etc...) and does not
     * include the test components (android test, unit test)
     */
    val variants: List<VariantCreationConfig>

    /**
     * the test components (android test, unit test)
     */
    val testComponents: List<TestComponentCreationConfig>

    val defaultVariant: String?

    val buildFeatures: BuildFeatureValues

    // utility objects and methods

    val syncIssueReporter: SyncIssueReporter

    val projectOptions: ProjectOptions

    val mockableJarArtifact: FileCollection

    val filteredBootClasspath: Provider<List<RegularFile>>

    val versionedSdkLoader: Provider<VersionedSdkLoader>
}
