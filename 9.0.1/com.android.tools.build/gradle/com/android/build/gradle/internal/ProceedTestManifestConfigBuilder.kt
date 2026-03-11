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

package com.android.build.gradle.internal

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.variant.impl.getApiString
import com.android.build.gradle.internal.component.DeviceTestCreationConfig
import com.android.build.gradle.internal.component.HostTestCreationConfig
import com.android.build.gradle.internal.component.InstrumentedTestCreationConfig
import com.android.build.gradle.internal.component.TaskCreationConfig
import com.android.build.gradle.internal.component.TestCreationConfig
import com.android.build.gradle.internal.component.TestVariantCreationConfig
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.tasks.creationconfig.ProceedTestManifestCreationConfig
import com.android.build.gradle.internal.utils.parseTargetHash
import com.android.build.gradle.internal.variant.VariantPathHelper
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import java.io.File

fun <T : Any> TestCreationConfig.emptyProvider(): Provider<T> = this.services.provider { null }

/**
 * Create test manifest config for configurations like:
 * - unit test fot library
 * - unit test for application
 * - application instrumented test
 */
fun createProcessTestManifestConfig(creationConfig: TestCreationConfig): ProceedTestManifestCreationConfig {
    val isHostTestWithTestedApk =
        creationConfig is HostTestCreationConfig && creationConfig.mainVariant.componentType.isApk
    return if (isHostTestWithTestedApk) {
        object : BaseProceedTestManifestCreationConfig(creationConfig) {
            override val applicationId: Provider<String>
                get() = creationConfig.mainVariant.applicationId
            override val testedApplicationId: Provider<String>
                get() = creationConfig.mainVariant.applicationId
            override val namespace: Provider<String>
                get() = creationConfig.mainVariant.namespace
            override val instrumentationRunner: Provider<String>
                get() = creationConfig.emptyProvider()
            override val testedApkVariantArtifacts: ArtifactsImpl
                get() = creationConfig.mainVariant.artifacts
        }
    } else if (creationConfig is InstrumentedTestCreationConfig) {
        object : BaseProceedTestManifestCreationConfig(creationConfig) {
            override val handleProfiling: Provider<Boolean>
                get() = creationConfig.handleProfiling
            override val functionalTest: Provider<Boolean>
                get() = creationConfig.functionalTest
            override val testLabel: Provider<String>
                get() = creationConfig.testLabel
        }
    } else {
        BaseProceedTestManifestCreationConfig(creationConfig)
    }
}

/**
 * Config for library unit test config (no instrumentation, not an apk related unit test)
 */
open class BaseProceedTestManifestCreationConfig(val creationConfig: TestCreationConfig) :
    ProceedTestManifestCreationConfig,
    TaskCreationConfig by creationConfig {

    override val baseName: String
        get() = creationConfig.baseName
    override val dirName: String
        get() = creationConfig.dirName
    override val paths: VariantPathHelper
        get() = creationConfig.paths

    override val applicationId: Provider<String>
        get() = creationConfig.applicationId
    override val testedApplicationId: Provider<String>
        get() = creationConfig.testedApplicationId
    override val namespace: Provider<String>
        get() = creationConfig.namespace
    override val instrumentationRunner: Provider<String>
        get() = creationConfig.instrumentationRunner
    override val testedApkVariantArtifacts: ArtifactsImpl?
        get() = null

    // return non-empty values for instrumented test
    override val handleProfiling: Provider<Boolean>
        get() = creationConfig.emptyProvider()
    override val functionalTest: Provider<Boolean>
        get() = creationConfig.emptyProvider()
    override val testLabel: Provider<String>
        get() = creationConfig.emptyProvider()

    override val debuggable: Boolean
        get() = creationConfig.debuggable

    override val compileSdk: Int?
        get() = parseTargetHash(creationConfig.global.compileSdkHashString).apiLevel
    override val manifestFile: File
        get() = creationConfig.sources.manifestFile
    override val manifestOverlayFiles: Provider<List<File>>
        get() = creationConfig.sources.manifestOverlayFiles

    override val minSdk: String
        get() = creationConfig.minSdk.getApiString()
    override val targetSdkVersion: String
        get() = creationConfig.targetSdkVersion.getApiString()

    override val variantDependencies: VariantDependencies
        get() = creationConfig.variantDependencies
    override val useLegacyPackaging: Provider<Boolean>
        get() = if (creationConfig is DeviceTestCreationConfig || creationConfig is TestVariantCreationConfig)
            creationConfig.packaging.jniLibs.useLegacyPackaging
        else creationConfig.services.provider { null }
    override val placeholderValues: MapProperty<String, String>
        get() = creationConfig.manifestPlaceholdersCreationConfig?.placeholders
            ?: creationConfig.services.mapProperty(String::class.java, String::class.java)
}
