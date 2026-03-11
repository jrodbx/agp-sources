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

package com.android.build.gradle

import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfig
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.NamedDomainObjectContainer

/**
 * An intermediate implementation class of the previous `android` extension for application and dynamic feature plugins.
 *
 * Replaced by [com.android.build.api.dsl.ApplicationExtension] and [com.android.build.api.dsl.DynamicFeatureExtension] in the application
 * and dynamic-feature plugins respectively.
 */
@Deprecated(
  message =
    "Replaced by com.android.build.api.dsl.ApplicationExtension and com.android.build.api.dsl.DynamicFeatureExtension.\n" +
      "This class is not used for the public extensions in AGP when android.newDsl=true, which is the default in AGP 9.0, and will be removed in AGP 10.0."
)
abstract class AppExtension(
  dslServices: DslServices,
  bootClasspathConfig: BootClasspathConfig,
  buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
  sourceSetManager: SourceSetManager,
  isBaseModule: Boolean,
  stats: GradleBuildProject.Builder?,
) : AbstractAppExtension(dslServices, bootClasspathConfig, buildOutputs, sourceSetManager, isBaseModule, stats)
