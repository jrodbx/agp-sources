/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.AarMetadata
import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.dsl.Installation
import com.android.build.api.dsl.Optimization
import com.android.build.api.dsl.Packaging
import com.android.build.api.dsl.TestOptions
import java.io.File

/**
 * Temporary interface to develop the kotlin multiplatform android plugin.
 *
 * TODO(b/243387425): Move to gradle-api
 */
interface KotlinMultiplatformAndroidExtension {
    var minSdk: Int?
    var minSdkPreview: String?
    var buildToolsVersion: String
    var namespace: String?

    fun useLibrary(name: String)
    fun useLibrary(name: String, required: Boolean)

    var compileSdk: Int?
    var compileSdkExtension: Int?
    var compileSdkPreview: String?

    val maxSdkVersion: Int?

    val experimentalProperties: MutableMap<String, Any>

    val buildTypeMatching: MutableList<String>
    val productFlavorsMatching: MutableMap<String, MutableList<String>>

    val aarMetadata: AarMetadata

    val packagingOptions: Packaging

    // optimization stuff

    var isMinifyEnabled: Boolean

    val proguardFiles: MutableList<File>
    val consumerProguardFiles: MutableList<File>
    val testProguardFiles: MutableList<File>

    val optimization: Optimization

    // test stuff. todo, combine to a test block

    var testNamespace: String?
    val testOptions: TestOptions

    var testInstrumentationRunner: String?

    val testInstrumentationRunnerArguments: MutableMap<String, String>

    var testHandleProfiling: Boolean?

    var testFunctionalTest: Boolean?

    val installation: Installation

    // should this be here?
    fun testSigningConfig(action: ApkSigningConfig.() -> Unit)

    var testTargetSdk: Int?
    var testTargetSdkPreview: String?

    var enableUnitTest: Boolean
    var enableAndroidTest: Boolean
}
