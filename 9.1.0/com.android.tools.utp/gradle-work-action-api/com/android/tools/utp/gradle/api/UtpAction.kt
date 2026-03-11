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

package com.android.tools.utp.gradle.api

import org.gradle.api.provider.ProviderFactory

/**
 * Defines the public API and entry point for executing the Unified Test Platform (UTP) logic within an isolated Gradle Work Action.
 *
 * This interface is designed to be loaded using [java.util.ServiceLoader] from the `:utp:gradle-work-action` module, while being called
 * from `:gradle-core`. This separation enables strict class loader isolation for the UTP execution, preventing classpath conflicts with the
 * main Gradle plugin.
 */
interface UtpAction {
  /**
   * Executes the main UTP test logic.
   *
   * @param parameters The [RunUtpWorkParameters] containing all configuration for the UTP execution, such as device details, test APKs, and
   *   SDK paths.
   * @param provider A [ProviderFactory] instance injected in the calling WorkAction, used to access Gradle properties (like feature flags)
   *   from within the isolated action.
   */
  fun run(parameters: RunUtpWorkParameters, provider: ProviderFactory)
}
