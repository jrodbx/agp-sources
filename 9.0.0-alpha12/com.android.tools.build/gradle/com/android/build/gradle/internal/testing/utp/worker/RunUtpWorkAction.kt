/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.testing.utp.worker

import org.gradle.api.provider.ProviderFactory
import org.gradle.workers.WorkAction
import javax.inject.Inject

/**
 * A work action that runs UTP in an external java process.
 */
abstract class RunUtpWorkAction : WorkAction<RunUtpWorkParameters> {

    @get:Inject
    abstract val provider: ProviderFactory

    override fun execute() {
        // This Gradle property key is hard coded in Android Studio.
        // We will remove it once Android Studio can consume test report using
        // the tooling api.
        val enableUtpTestReportingForAndroidStudio =
            provider.gradleProperty(
                "com.android.tools.utp.GradleAndroidProjectResolverExtension.enable"
            ).orNull?.toBoolean() ?: false

        val utpRunner = UtpRunner(
            parameters.jvm.asFile.get(),
            parameters.utpDependencies.get(),
            enableUtpTestReportingForAndroidStudio,
        )

        val utpRunConfigs = parameters.utpRunConfigs.get()

        utpRunner.execute(
            utpRunConfigs.map { it.runnerConfigFile.get().asFile },
            utpRunConfigs.map { it.loggingPropertiesFile.get().asFile },
            utpRunConfigs.map { it.deviceId.get() },
            utpRunConfigs.map { it.deviceName.get() },
            utpRunConfigs.map { it.deviceShardName.get() },
            parameters.projectPath.get(),
            parameters.variantName.get(),
            parameters.xmlTestReportOutputDirectory.asFile.get(),
            utpRunConfigs.map { it.utpResultProtoOutputFile.get().asFile },
        )
    }
}
