/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.build.gradle.internal.test

import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.test.BuiltArtifactsSplitOutputMatcher.computeBestOutput
import com.android.builder.testing.api.DeviceConfigProvider
import com.google.common.collect.ImmutableList
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import java.io.File

/**
 * Implementation of [TestData] on top of a [AndroidTestCreationConfig]
 */
class TestDataImpl(
    namespace: Provider<String>,
    testConfig: AndroidTestCreationConfig,
    testApkDir: Provider<Directory>,
    testedApksDir: FileCollection?
) : AbstractTestDataImpl(
    namespace,
    testConfig,
    testApkDir,
    testedApksDir
) {

    override val libraryType =
        testConfig.services.provider { testConfig.mainVariant.componentType.isAar }

    @get:Input
    val supportedAbis: Set<String> =
        testConfig.nativeBuildCreationConfig?.supportedAbis ?: emptySet()

    override fun findTestedApks(deviceConfigProvider: DeviceConfigProvider): List<File> {
        testedApksDir ?: return emptyList()
        val apks = ImmutableList.builder<File>()
        val builtArtifacts = BuiltArtifactsLoaderImpl().load(testedApksDir)
            ?: return ImmutableList.of()
        apks.addAll(computeBestOutput(deviceConfigProvider, builtArtifacts, supportedAbis))
        return apks.build()
    }

}
