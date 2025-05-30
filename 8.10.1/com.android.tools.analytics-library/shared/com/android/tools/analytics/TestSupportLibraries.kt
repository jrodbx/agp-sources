/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:JvmName("TestSupportLibraries")

package com.android.tools.analytics

import com.google.common.collect.ImmutableTable
import com.google.wireless.android.sdk.stats.TestLibraries.Builder

private val setters =
  ImmutableTable.Builder<String, String, (Builder, String) -> Builder>()
    .apply {
      put("androidx.fragment", "fragment-testing", Builder::setFragmentTestingVersion)
      put("androidx.test", "core", Builder::setTestCoreVersion)
      put("androidx.test", "core-ktx", Builder::setTestCoreKtxVersion)
      put("androidx.test", "orchestrator", Builder::setTestOrchestratorVersion)
      put("androidx.test", "rules", Builder::setTestRulesVersion)
      put("androidx.test", "runner", Builder::setTestRunnerVersion)
      put(
        "androidx.test.espresso",
        "espresso-accessibility",
        Builder::setEspressoAccessibilityVersion,
      )
      put("androidx.test.espresso", "espresso-contrib", Builder::setEspressoContribVersion)
      put("androidx.test.espresso", "espresso-core", Builder::setEspressoVersion)
      put(
        "androidx.test.espresso",
        "espresso-idling-resource",
        Builder::setEspressoIdlingResourceVersion,
      )
      put("androidx.test.espresso", "espresso-intents", Builder::setEspressoIntentsVersion)
      put("androidx.test.espresso", "espresso-web", Builder::setEspressoWebVersion)
      put("androidx.test.espresso", "espresso-device", Builder::setEspressoDeviceVersion)
      put("androidx.test.ext", "junit", Builder::setTestExtJunitVersion)
      put("androidx.test.ext", "junit-ktx", Builder::setTestExtJunitKtxVersion)
      put("androidx.test.ext", "truth", Builder::setTestExtTruthVersion)
      put("androidx.test.services", "storage", Builder::setTestServicesStorageVersion)
      put("androidx.test.services", "test-services", Builder::setTestServicesTestServicesVersion)
      put("com.android.support.test", "orchestrator", Builder::setTestSupportOrchestratorVersion)
      put("com.android.support.test", "rules", Builder::setTestSupportRulesVersion)
      put("com.android.support.test", "runner", Builder::setTestSupportLibraryVersion)
      put(
        "com.android.support.test.espresso",
        "espresso-accessibility",
        Builder::setTestSupportEspressoAccessibilityVersion,
      )
      put(
        "com.android.support.test.espresso",
        "espresso-contrib",
        Builder::setTestSupportEspressoContribVersion,
      )
      put(
        "com.android.support.test.espresso",
        "espresso-core",
        Builder::setTestSupportEspressoVersion,
      )
      put(
        "com.android.support.test.espresso",
        "espresso-idling-resource",
        Builder::setTestSupportEspressoIdlingResourceVersion,
      )
      put(
        "com.android.support.test.espresso",
        "espresso-intents",
        Builder::setTestSupportEspressoIntentsVersion,
      )
      put(
        "com.android.support.test.espresso",
        "espresso-web",
        Builder::setTestSupportEspressoWebVersion,
      )
      put("com.google.truth", "truth", Builder::setTruthVersion)
      put("junit", "junit", Builder::setJunitVersion)
      put("org.mockito", "mockito-core", Builder::setMockitoVersion)
      put("org.robolectric", "robolectric", Builder::setRobolectricVersion)
      put("androidx.benchmark", "benchmark-common", Builder::setBenchmarkCommonVersion)
      put("androidx.benchmark", "benchmark-junit4", Builder::setBenchmarkJunit4Version)
      put("androidx.benchmark", "benchmark-macro", Builder::setBenchmarkMacroVersion)
      put("androidx.benchmark", "benchmark-macro-junit4", Builder::setBenchmarkMacroJunit4Version)
      put("androidx.compose.ui", "ui-test", Builder::setComposeUiTestVersion)
      put("androidx.compose.ui", "ui-test-junit4", Builder::setComposeUiTestJunit4Version)
      put("androidx.compose.ui", "ui-test-manifest", Builder::setComposeUiTestManifestVersion)
    }
    .build()

/** Fills in the right field of a [Builder] based on the maven coordinates. */
fun Builder.recordTestLibrary(groupId: String, artifactId: String, version: String) = apply {
  setters[groupId, artifactId]?.invoke(this, version)
}
