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

package com.android.build.gradle.internal.dsl

import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.artifacts.dsl.GradleDependencies
import org.gradle.declarative.dsl.model.annotations.Restricted

interface VariantDimensionDependenciesExtension {
  val api: DependencyCollector
  val implementation: DependencyCollector
  val compileOnly: DependencyCollector
  val compileOnlyApi: DependencyCollector
  val runtimeOnly: DependencyCollector
  val annotationProcessor: DependencyCollector
}

@Restricted interface BuildTypeDependenciesExtension : VariantDimensionDependenciesExtension, GradleDependencies

@Restricted interface ProductFlavorDependenciesExtension : VariantDimensionDependenciesExtension, GradleDependencies
