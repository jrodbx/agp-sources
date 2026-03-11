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

package com.android.build.gradle.internal.component

import com.android.build.api.variant.AnnotationProcessor
import com.android.build.api.variant.Sources
import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.BuiltInKaptSupportMode
import com.android.build.gradle.internal.services.BuiltInKotlinSupportMode
import org.gradle.api.JavaVersion
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.plugin.sources.android.AndroidVariantType

/** Creation config for all kotlin tasks' configuration. */
interface BuiltInKotlinCreationConfig : TaskCreationConfig {

  val kotlin: FlatSourceDirectoriesImpl?

  val java: FlatSourceDirectoriesImpl?

  /** runs [action] passing the [Sources.java] internal representation if not null. If null, action is not run. */
  fun java(action: (FlatSourceDirectoriesImpl) -> Unit)

  /** runs [action] passing the [Sources.kotlin] internal representation if not null. If null, action is not run. */
  fun kotlin(action: (FlatSourceDirectoriesImpl) -> Unit)

  val bootClasspath: Provider<List<RegularFile>>

  fun getAnnotationProcessorJars(): FileCollection

  /** Get the compile classpath for compiling sources in this component */
  fun getJavaClasspath(
    configType: AndroidArtifacts.ConsumedConfigType,
    classesType: AndroidArtifacts.ArtifactType,
    generatedBytecodeKey: Any? = null,
  ): FileCollection

  val builtInKotlinSupportMode: BuiltInKotlinSupportMode
  val builtInKaptSupportMode: BuiltInKaptSupportMode

  /** Returns the directory for the [internalArtifactType] if built-in KAPT support is enabled, or null if not. */
  fun getBuiltInKaptArtifact(internalArtifactType: InternalArtifactType<Directory>): Provider<Directory>?

  val targetCompatibility: JavaVersion

  val kaptSourceOutputDir: Provider<Directory>

  val kaptKotlinSourceOutputDir: Provider<Directory>

  fun getExplicitApiMode(): ExplicitApiMode?

  @OptIn(InternalKotlinGradlePluginApi::class) fun toAndroidVariantType(): AndroidVariantType

  val sourceCompatibility: JavaVersion

  val annotationProcessor: AnnotationProcessor

  fun setupFriends(friendPaths: ConfigurableFileCollection)
}
