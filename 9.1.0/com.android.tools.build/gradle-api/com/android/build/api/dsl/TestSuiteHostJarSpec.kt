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

package com.android.build.api.dsl

import org.gradle.api.Incubating

/**
 * Definition of host test sources for a test suite. Host test source files are compiled and therefore dependencies can be attached to them.
 */
@Incubating
interface TestSuiteHostJarSpec {

  /** Dependency handler for this sources */
  @get:Incubating val dependencies: AgpTestSuiteDependencies

  /** Specifies dependency information for this test suite. */
  @Incubating fun dependencies(action: AgpTestSuiteDependencies.() -> Unit)

  /**
   * If this property is set to <code>true</code>, the plugin performs resource, asset, and manifest merging before running your host tests.
   * Your tests can then inspect a file called `com/android/tools/test_config.properties` on the classpath, which is a Java properties file
   * with the following keys:
   *
   * `android_resource_apk`: the path to the APK-like zip file containing merged resources, which includes all the resources from the
   * current subproject and all its dependencies.
   *
   * `android_merged_assets`: the path to the directory containing merged assets. The merged assets directory contains assets from the
   * current subproject and it dependencies.
   *
   * `android_merged_manifest`: the path to the merged manifest file. Only app subprojects have the manifest merged from their dependencies.
   * Library subprojects do not include manifest components from their dependencies.
   *
   * `android_custom_package`: the package name (namespace) of the final R class.
   *
   * Note that the paths above are relative paths (relative to the current project directory, not the root project directory).
   */
  @get:Incubating @set:Incubating var enableAndroidResources: Boolean
}
