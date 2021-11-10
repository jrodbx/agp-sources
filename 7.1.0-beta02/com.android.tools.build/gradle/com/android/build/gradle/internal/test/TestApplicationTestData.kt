/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.build.api.component.impl.ComponentImpl
import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.BuiltArtifacts
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.internal.component.TestVariantCreationConfig
import com.android.builder.testing.api.DeviceConfigProvider
import com.android.utils.ILogger
import com.google.common.collect.ImmutableList
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import java.io.File
import java.util.stream.Collectors

/** Implementation of [TestData] for separate test modules.  */
class TestApplicationTestData constructor(
    namespace: Provider<String>,
    creationConfig: TestVariantCreationConfig,
    testApkDir: Provider<Directory>,
    testedApksDir: FileCollection
) : AbstractTestDataImpl(
    namespace,
    creationConfig,
    creationConfig,
    creationConfig.variantSources,
    testApkDir,
    testedApksDir
) {

    override val libraryType = creationConfig.services.provider { false }

    // AbstractTestDataImpl.testedApplicationId relies on creationConfig.testedApplicationId,
    // which returns the instrumentation target application name. If
    // ModulePropertyKeys.SELF_INSTRUMENTING is enabled, the instrumentation targets to the test
    // application instead of tested application. To always return the tested application ID from
    // this method, we override this method.
    override val testedApplicationId: Provider<String> =
        testedApksDir.elements.map { BuiltArtifactsLoaderImpl().load(it.single())?.applicationId!! }

    override fun findTestedApks(
        deviceConfigProvider: DeviceConfigProvider,
        logger: ILogger
    ): List<File> {
        testedApksDir ?: return emptyList()

        // retrieve all the published files.
        val builtArtifacts: BuiltArtifacts? = BuiltArtifactsLoaderImpl().load(testedApksDir)
        return if (builtArtifacts != null) builtArtifacts.elements.stream()
            .map(BuiltArtifact::outputFile)
            .map { pathname: String -> File(pathname) }
            .collect(Collectors.toList()) else ImmutableList.of()
    }
}
