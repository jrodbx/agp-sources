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

package com.android.build.api.variant

import com.android.build.api.dsl.AgpTestSuiteDependencies
import java.io.File
import org.gradle.api.Incubating
import org.gradle.api.Named

/**
 * A test source abstraction which can be either an asset folder, a host jar source, or a test apk source.
 *
 * Test suite sources attached to the [TestSuite]. These are not specific to a single variant but shared between all variants that the test
 * suite targets through the [com.android.build.api.dsl.AgpTestSuite.targetVariants] API.
 */
@Incubating
interface TestSuiteSourceSet : Named {

  /** The source type. */
  @get:Incubating val type: TestSuiteSourceType

  /**
   * The dependencies associated with this source or null if this [type] does not support source dependencies (like
   * [TestSuiteSourceType.ASSETS]]).
   *
   * The dependencies added through the DSL declarations will not be queryable through the returned instance. This [dependencies] is only
   * for adding new dependencies that were not added at the DSL declaration.
   */
  @get:Incubating val dependencies: AgpTestSuiteDependencies?

  /** Represents a [TestSuiteSourceSet] for asset based tests. */
  @Incubating
  interface Assets : TestSuiteSourceSet {
    @Incubating fun get(): SourceDirectories.Flat

    @get:Incubating
    override val type: TestSuiteSourceType
      get() = TestSuiteSourceType.ASSETS
  }

  /** Represents a [TestSuiteSourceSet] for host jars. */
  @Incubating
  interface HostJar : TestSuiteSourceSet {
    @get:Incubating val java: SourceDirectories.Flat?

    @get:Incubating val kotlin: SourceDirectories.Flat?

    @get:Incubating val resources: SourceDirectories.Flat

    @get:Incubating
    override val type: TestSuiteSourceType
      get() = TestSuiteSourceType.HOST_JAR

    /** Will point to a manifest file location when [com.android.build.api.dsl.TestSuiteHostJarSpec.enableAndroidResources] is turned on. */
    @get:Incubating val manifestFile: File?
  }

  /** Represents a [TestSuiteSourceSet] for test apks. */
  @Incubating
  interface TestApk : TestSuiteSourceSet {
    @get:Incubating val manifestFile: File
    @get:Incubating val java: SourceDirectories.Flat?
    @get:Incubating val kotlin: SourceDirectories.Flat?
    @get:Incubating val resources: SourceDirectories.Flat
    @get:Incubating
    override val type: TestSuiteSourceType
      get() = TestSuiteSourceType.TEST_APK
  }
}
