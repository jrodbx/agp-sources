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

import com.android.build.gradle.internal.testing.utp.UtpDependencies
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkParameters

/**
 * Parameters of [RunUtpWorkAction].
 */
interface RunUtpWorkParameters : WorkParameters {
    // Java executable to run JAVA commands
    val jvm: RegularFileProperty

    /** List of configurations for each UTP test run to be executed. */
    val utpRunConfigs: ListProperty<UtpRunConfig>

    /** A property holding the resolved UTP dependency artifacts. */
    val utpDependencies: Property<UtpDependencies>

    /** The project path, used for creating the XML test report. */
    val projectPath: Property<String>

    /** The variant name, used for creating the XML test report. */
    val variantName: Property<String>

    /** The directory where XML test reports should be generated. */
    val xmlTestReportOutputDirectory: DirectoryProperty

    /**
     * Configuration for a single UTP test run.
     */
    interface UtpRunConfig {
        /** The UTP runner config binary proto file. */
        val runnerConfigFile: RegularFileProperty
        /** The Java logging properties file for this UTP process. */
        val loggingPropertiesFile: RegularFileProperty
        /** The device ID (e.g., serial number) for this run. */
        val deviceId: Property<String>
        /** The device name, used in the XML test report. */
        val deviceName: Property<String>
        /** The unique name for this test shard, used as the test suite name in the XML report. */
        val deviceShardName: Property<String>
        /** The output file where the binary test-result.pb for this run should be written. */
        val utpResultProtoOutputFile: RegularFileProperty
    }
}
