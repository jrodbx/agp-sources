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
 * List of possible input parameters a Junit engine can consume to run successfully.
 *
 * Each JUnit engine has particular inputs requirements and these must be passed across processes as the JUnit engine runs in a separate VM.
 * Therefore, we cannot just use the Gradle's Provider APIs, plus those JUnit engines can run im multiple build systems.
 *
 * So to express an input requirement in the Android ecosystem, a JUnit engine configuration can use a combination of the ones defined
 * below. In the case of Gradle build system, the Test task will automatically become dependent on the tasks producing the artifacts and the
 * artifacts location will be passed ot the JUnit engine.
 *
 * To express the engine's requirements, the user must add the required inputs using the [JUnitEngineSpec.inputs] collection of
 * [AgpTestSuiteInputParameters]
 *
 * TODO: possibly explore how junit engine could also express their dependencies to avoid users having the manually add those.
 * TODO: Describe how the JUnit engine can retrieve the values at execution time once experimentation concluded.
 */
enum class AgpTestSuiteInputParameters(val propertyName: String) {

  /**
   * [java.io.File.pathSeparator] separated list of directories containing the static source files for the test suite. The test suite must
   * have been configured using the [AgpTestSuite.assets] method.
   */
  @Incubating STATIC_FILES("com.android.agp.test.STATIC_FILES"),

  /**
   * [java.io.File.pathSeparator] separated list of folders containing test classes for this suite. The test suite must have been configured
   * using the [AgpTestSuite.hostJar].
   */
  @Incubating TEST_CLASSES("com.android.agp.test.TEST_CLASSES"),
  /** [java.io.File.pathSeparator] separated list of jar files or folders for the runtime classpath of the test classes */
  @Incubating TEST_CLASSPATH("com.android.agp.test.TEST_CLASSPATH"),

  /** Path to the merged manifest file. */
  @Incubating MERGED_MANIFEST("com.android.agp.test.MERGED_MANIFEST"),

  /**
   * TODO: provide an access through the BuiltArtifactsLoader (moved out of gradle-api) ?
   *
   * [java.io.File.pathSeparator] separated list of APK files to be tested.
   *
   * The test suite must have been configured using the [AgpTestSuite.testApk].
   */
  @Incubating TESTED_APKS("com.android.agp.test.TESTED_APKS"),

  /** Path to the testing APK file. */
  @Incubating TESTING_APK("com.android.agp.test.TESTING_APK"),

  /** Path to the ADB executable. */
  @Incubating ADB_EXECUTABLE("com.android.agp.test.ADB_EXECUTABLE"),
}
