/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.TaskCreationConfig
import com.android.build.gradle.internal.component.features.DexingCreationConfig
import com.android.build.gradle.internal.component.features.InstrumentationCreationConfig
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.tasks.DexMergingAction
import com.android.builder.dexing.DexingType
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

interface DexMergingCreationConfig : TaskCreationConfig {
  val action: DexMergingAction
  val dexingType: DexingType
  val dexingUsingArtifactTransforms: Boolean
  val dexing: DexingCreationConfig
  val separateFileDependenciesDexingTask: Boolean
  val outputType: InternalMultipleArtifactType<Directory>
  val bootClasspath: Provider<List<RegularFile>>
  val debuggable: Boolean
  val enableGlobalSynthetics: Boolean
  val variantDependencies: VariantDependencies
  val enableApiModeling: Boolean
  val instrumentationCreationConfig: InstrumentationCreationConfig?
  val requiresJacocoTransformation: Boolean
}

fun createDexMergingCreationConfig(
  creationConfig: ApkCreationConfig,
  action: DexMergingAction,
  dexingType: DexingType,
  dexingUsingArtifactTransforms: Boolean = true,
  separateFileDependenciesDexingTask: Boolean = false,
  outputType: InternalMultipleArtifactType<Directory> = InternalMultipleArtifactType.DEX,
): DexMergingCreationConfig {
  return object : BaseDexMergingCreationConfig(creationConfig) {
    override val action: DexMergingAction
      get() = action

    override val dexingType: DexingType
      get() = dexingType

    override val dexingUsingArtifactTransforms: Boolean
      get() = dexingUsingArtifactTransforms

    override val separateFileDependenciesDexingTask: Boolean
      get() = separateFileDependenciesDexingTask

    override val outputType: InternalMultipleArtifactType<Directory>
      get() = outputType

    override val debuggable: Boolean
      get() = creationConfig.debuggable

    override val enableGlobalSynthetics: Boolean
      get() = creationConfig.enableGlobalSynthetics

    override val requiresJacocoTransformation: Boolean
      get() = creationConfig.requiresJacocoTransformation

    override val enableApiModeling: Boolean
      get() = creationConfig.enableApiModeling

    override val dexing: DexingCreationConfig
      get() = creationConfig.dexing
  }
}

abstract class BaseDexMergingCreationConfig(val creationConfig: ComponentCreationConfig) :
  DexMergingCreationConfig, TaskCreationConfig by creationConfig {

  override val bootClasspath: Provider<List<RegularFile>>
    get() = creationConfig.global.bootClasspath

  override val variantDependencies: VariantDependencies
    get() = creationConfig.variantDependencies

  override val instrumentationCreationConfig: InstrumentationCreationConfig?
    get() = creationConfig.instrumentationCreationConfig
}
