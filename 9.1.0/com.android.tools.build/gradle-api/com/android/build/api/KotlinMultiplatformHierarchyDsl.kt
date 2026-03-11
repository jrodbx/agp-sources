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
@file:JvmName("KotlinMultiplatformHierarchyDsl")

package com.android.build.api

import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilation
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Incubating
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyBuilder

/**
 * Creates an extension for android support in applyDefaultHierarchyTemplate, for example:
 *
 * applyDefaultHierarchyTemplate { common { group("androidJvm") { withJvm() withAndroid() } } }
 */
@OptIn(ExperimentalKotlinGradlePluginApi::class)
@Incubating
fun KotlinHierarchyBuilder.withAndroid() = withCompilations {
  it is KotlinMultiplatformAndroidCompilation && it.target is KotlinMultiplatformAndroidLibraryTarget
}
