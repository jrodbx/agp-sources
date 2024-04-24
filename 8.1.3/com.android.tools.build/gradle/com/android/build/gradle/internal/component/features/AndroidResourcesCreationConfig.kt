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

package com.android.build.gradle.internal.component.features

import com.android.build.api.variant.AndroidResources
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.builder.model.VectorDrawablesOptions
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/**
 * Creation config for components that support android resources.
 *
 * To use this in a task that requires android resources support, use
 * [com.android.build.gradle.internal.tasks.factory.features.AndroidResourcesTaskCreationAction].
 * Otherwise, access the nullable property on the component
 * [com.android.build.gradle.internal.component.ComponentCreationConfig.androidResourcesCreationConfig].
 */
interface AndroidResourcesCreationConfig {
    val androidResources: AndroidResources
    val pseudoLocalesEnabled: Property<Boolean>
    val isCrunchPngs: Boolean
    val isPrecompileDependenciesResourcesEnabled: Boolean
    val resourceConfigurations: Set<String>
    val vectorDrawables: VectorDrawablesOptions
    val useResourceShrinker: Boolean
    val compiledRClassArtifact: Provider<RegularFile>
    fun getCompiledRClasses(configType: AndroidArtifacts.ConsumedConfigType): FileCollection
}
