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

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.variant.AnnotationProcessor
import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES_JAR
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.publishing.PublishingSpecs
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.BuiltInKaptSupportMode
import com.android.build.gradle.internal.services.BuiltInKotlinSupportMode
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.toAndroidVariantType
import org.gradle.api.JavaVersion
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.plugin.sources.android.AndroidVariantType

class ComponentBasedBuiltInKotlinCreationConfig(val componentCreationConfig: ComponentCreationConfig) : BuiltInKotlinCreationConfig {

  override val java: FlatSourceDirectoriesImpl?
    get() = componentCreationConfig.sources.java

  override val kotlin: FlatSourceDirectoriesImpl?
    get() = componentCreationConfig.sources.kotlin

  override fun java(action: (FlatSourceDirectoriesImpl) -> Unit) {
    componentCreationConfig.sources.java(action)
  }

  override fun kotlin(action: (FlatSourceDirectoriesImpl) -> Unit) {
    componentCreationConfig.sources.kotlin(action)
  }

  override val bootClasspath: Provider<List<RegularFile>>
    get() = componentCreationConfig.global.bootClasspath

  override fun getJavaClasspath(
    configType: AndroidArtifacts.ConsumedConfigType,
    classesType: AndroidArtifacts.ArtifactType,
    generatedBytecodeKey: Any?,
  ): FileCollection = componentCreationConfig.getJavaClasspath(configType, classesType, generatedBytecodeKey)

  override fun getBuiltInKaptArtifact(internalArtifactType: InternalArtifactType<Directory>): Provider<Directory>? =
    componentCreationConfig.getBuiltInKaptArtifact(internalArtifactType)

  override val builtInKotlinSupportMode: BuiltInKotlinSupportMode
    get() = componentCreationConfig.builtInKotlinSupportMode

  override val builtInKaptSupportMode: BuiltInKaptSupportMode
    get() = componentCreationConfig.builtInKaptSupportMode

  override val targetCompatibility: JavaVersion
    get() = componentCreationConfig.global.compileOptions.targetCompatibility

  override val name: String
    get() = componentCreationConfig.name

  override val services: TaskCreationServices
    get() = componentCreationConfig.services

  override val taskContainer: MutableTaskContainer
    get() = componentCreationConfig.taskContainer

  override val artifacts: ArtifactsImpl
    get() = componentCreationConfig.artifacts

  override val kaptSourceOutputDir: Provider<Directory> by lazy {
    componentCreationConfig.paths.generatedDir("source", "kapt", componentCreationConfig.paths.dirName)
  }

  override val kaptKotlinSourceOutputDir: Provider<Directory> by lazy {
    componentCreationConfig.paths.generatedDir("source", "kaptKotlin", componentCreationConfig.paths.dirName)
  }

  override fun getExplicitApiMode(): ExplicitApiMode? {
    return if (componentCreationConfig.componentType.isForTesting) {
      ExplicitApiMode.Disabled
    } else {
      services.builtInKotlinServices.kotlinAndroidProjectExtension.explicitApi
    }
  }

  @OptIn(InternalKotlinGradlePluginApi::class)
  override fun toAndroidVariantType(): AndroidVariantType = componentCreationConfig.toAndroidVariantType()

  override fun getAnnotationProcessorJars(): FileCollection = componentCreationConfig.getAnnotationProcessorJars()

  override val sourceCompatibility: JavaVersion
    get() = componentCreationConfig.global.compileOptions.sourceCompatibility

  override val annotationProcessor: AnnotationProcessor
    get() = componentCreationConfig.javaCompilation.annotationProcessor

  override fun setupFriends(friendPaths: ConfigurableFileCollection) {
    // Set friendPaths to allow tests/test fixtures to access internal functions/properties of the
    // main component
    if (componentCreationConfig is NestedComponentCreationConfig) {
      val mainComponent = componentCreationConfig.mainVariant
      val mainComponentClassesJar =
        PublishingSpecs.getVariantPublishingSpec(mainComponent.componentType)
          .getSpec(CLASSES_JAR, COMPILE_CLASSPATH.publishedTo)!!
          .outputType
      friendPaths.from(mainComponent.artifacts.get(mainComponentClassesJar))
    }

    // Set friendPaths to allow tests to access internal functions/properties of test fixtures
    if (componentCreationConfig is TestComponentCreationConfig) {
      val testFixturesComponent =
        componentCreationConfig.mainVariant.nestedComponents.filterIsInstance<TestFixturesCreationConfig>().firstOrNull()
      if (testFixturesComponent != null) {
        val testFixturesClassesJar =
          PublishingSpecs.getVariantPublishingSpec(testFixturesComponent.componentType)
            .getSpec(CLASSES_JAR, COMPILE_CLASSPATH.publishedTo)!!
            .outputType
        friendPaths.from(testFixturesComponent.artifacts.get(testFixturesClassesJar))
      }
    }
  }
}
