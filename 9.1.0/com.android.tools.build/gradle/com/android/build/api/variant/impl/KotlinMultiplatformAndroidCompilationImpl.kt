/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.api.variant.impl

import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilation
import com.android.build.gradle.internal.plugins.KotlinMultiplatformAndroidPlugin.Companion.getNamePrefixedWithAndroidTarget
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.plugin.mpp.external.DecoratedExternalKotlinCompilation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

@OptIn(ExternalKotlinTargetApi::class)
open class KotlinMultiplatformAndroidCompilationImpl(delegate: Delegate) :
  DecoratedExternalKotlinCompilation(delegate), KotlinMultiplatformAndroidCompilation {

  @Suppress("UNCHECKED_CAST")
  override val compileTaskProvider: TaskProvider<out KotlinCompilationTask<KotlinJvmCompilerOptions>>
    get() = super.compileTaskProvider as TaskProvider<KotlinCompilationTask<KotlinJvmCompilerOptions>>

  override val componentName: String
    get() = this.compilationName.getNamePrefixedWithAndroidTarget()
}

internal enum class KmpAndroidCompilationType(
  val defaultCompilationName: String,
  val defaultSourceSetName: String = defaultCompilationName.getNamePrefixedWithAndroidTarget(),
  val defaultSourceSetTreeName: String?,
) {
  MAIN(defaultCompilationName = "main", defaultSourceSetTreeName = KotlinSourceSetTree.main.name),
  HOST_TEST(defaultCompilationName = "hostTest", defaultSourceSetTreeName = KotlinSourceSetTree.test.name),
  DEVICE_TEST(defaultCompilationName = "deviceTest", defaultSourceSetTreeName = null),
}
