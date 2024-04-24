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

package com.android.build.gradle.internal.privaysandboxsdk

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.dsl.PrivacySandboxSdkExtension
import com.android.build.api.dsl.SigningConfig
import com.android.build.gradle.internal.dsl.PrivacySandboxSdkBundleImpl
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryConfigurations
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryDependencies
import com.android.build.gradle.internal.publishing.AarOrJarTypeToConsume
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.builder.model.ApiVersion
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec

interface PrivacySandboxSdkVariantScope {
    val layout: ProjectLayout
    val artifacts: ArtifactsImpl
    val incomingConfigurations: FusedLibraryConfigurations
    val outgoingConfigurations: FusedLibraryConfigurations
    val dependencies: FusedLibraryDependencies
    val extension: PrivacySandboxSdkExtension
    val mergeSpec: Spec<ComponentIdentifier>
    val compileSdkVersion: String
    val minSdkVersion: ApiVersion
    val bootClasspath: Provider<List<RegularFile>>
    val bundle: PrivacySandboxSdkBundleImpl
    val services: TaskCreationServices
    val signingConfig: SigningConfig
    val experimentalProperties: MapProperty<String, Any>
    val aarOrJarTypeToConsume: AarOrJarTypeToConsume
}
