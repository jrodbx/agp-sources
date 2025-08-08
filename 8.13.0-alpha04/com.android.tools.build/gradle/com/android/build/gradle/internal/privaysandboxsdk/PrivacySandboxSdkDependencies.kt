/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.build.gradle.internal.dependency.PluginConfigurations
import com.android.build.gradle.internal.dependency.PluginDependencies
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.getComponentFilter
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.specs.Spec

class PrivacySandboxSdkDependencies(
    override val configurations: PluginConfigurations = PluginConfigurations(),
    override val spec: Spec<ComponentIdentifier> = AndroidArtifacts.ArtifactScope.ALL.getComponentFilter()
) : PluginDependencies
