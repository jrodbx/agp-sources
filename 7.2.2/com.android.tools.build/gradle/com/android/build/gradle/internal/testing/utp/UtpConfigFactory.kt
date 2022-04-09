/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.testing.utp

import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.testing.StaticTestData
import com.android.build.gradle.internal.testing.utp.UtpDependency.ANDROID_DEVICE_PROVIDER_DDMLIB
import com.android.build.gradle.internal.testing.utp.UtpDependency.ANDROID_DEVICE_PROVIDER_GRADLE
import com.android.build.gradle.internal.testing.utp.UtpDependency.ANDROID_DRIVER_INSTRUMENTATION
import com.android.build.gradle.internal.testing.utp.UtpDependency.ANDROID_TEST_ADDITIONAL_TEST_OUTPUT_PLUGIN
import com.android.build.gradle.internal.testing.utp.UtpDependency.ANDROID_TEST_COVERAGE_PLUGIN
import com.android.build.gradle.internal.testing.utp.UtpDependency.ANDROID_TEST_DEVICE_INFO_PLUGIN
import com.android.build.gradle.internal.testing.utp.UtpDependency.ANDROID_TEST_LOGCAT_PLUGIN
import com.android.build.gradle.internal.testing.utp.UtpDependency.ANDROID_TEST_PLUGIN
import com.android.build.gradle.internal.testing.utp.UtpDependency.ANDROID_TEST_PLUGIN_HOST_RETENTION
import com.android.build.gradle.internal.testing.utp.UtpDependency.ANDROID_TEST_PLUGIN_RESULT_LISTENER_GRADLE
import com.android.builder.testing.api.DeviceConnector
import com.android.sdklib.BuildToolInfo
import com.android.tools.utp.plugins.deviceprovider.ddmlib.proto.AndroidDeviceProviderDdmlibConfigProto.DdmlibAndroidDeviceProviderConfig
import com.android.tools.utp.plugins.deviceprovider.gradle.proto.GradleManagedAndroidDeviceProviderProto.GradleManagedAndroidDeviceProviderConfig
import com.android.tools.utp.plugins.host.additionaltestoutput.proto.AndroidAdditionalTestOutputConfigProto.AndroidAdditionalTestOutputConfig
import com.android.tools.utp.plugins.host.coverage.proto.AndroidTestCoverageConfigProto.AndroidTestCoverageConfig
import com.android.tools.utp.plugins.host.icebox.proto.IceboxPluginProto
import com.android.tools.utp.plugins.host.icebox.proto.IceboxPluginProto.IceboxPlugin
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerConfigProto.GradleAndroidTestResultListenerConfig
import com.google.protobuf.Any
import com.google.protobuf.Message
import com.google.testing.platform.plugin.android.proto.AndroidDevicePluginProto.AndroidDevicePlugin
import com.google.testing.platform.proto.api.config.AndroidInstrumentationDriverProto.AndroidInstrumentationDriver
import com.google.testing.platform.proto.api.config.DeviceProto
import com.google.testing.platform.proto.api.config.EnvironmentProto
import com.google.testing.platform.proto.api.config.ExecutorProto
import com.google.testing.platform.proto.api.config.FixtureProto
import com.google.testing.platform.proto.api.config.LocalAndroidDeviceProviderProto.LocalAndroidDeviceProvider
import com.google.testing.platform.proto.api.config.RunnerConfigProto
import com.google.testing.platform.proto.api.core.ExtensionProto
import com.google.testing.platform.proto.api.core.LabelProto
import com.google.testing.platform.proto.api.core.PathProto
import com.google.testing.platform.proto.api.core.TestArtifactProto
import com.google.testing.platform.proto.api.service.ServerConfigProto
import java.io.File
import java.util.concurrent.TimeUnit
import org.gradle.api.logging.Logging

// This is an arbitrary string. This ID is used to lookup test results from UTP.
// UTP can run multiple test fixtures at a time so we have to give a name for
// each test fixture. However, AGP always has only one test fixture in the
// runner-config so this string is arbitrary.
private const val UTP_TEST_FIXTURE_ID = "AGP_Test_Fixture"

// A UTP gRPC server address.
private const val UTP_SERVER_ADDRESS = "localhost:20000"

// Emulator gRPC address
private const val DEFAULT_EMULATOR_GRPC_ADDRESS = "localhost"

// Default port for adb.
private const val DEFAULT_ADB_SERVER_PORT = 5037

private const val TEST_RUNNER_LOG_FILE_NAME = "test-results.log"

private  val AM_INSTRUMENT_COMMAND_TIME_OUT_SECONDS = TimeUnit.DAYS.toSeconds(365)

// Relative path to the UTP outputDir for test log directory.
private const val TEST_LOG_DIR = "testlog"

/**
 * A factory class to construct UTP runner and server configuration protos.
 */
class UtpConfigFactory {

    private val logger = Logging.getLogger(this.javaClass)

    /**
     * Creates a runner config proto which you can pass into the Unified Test Platform's
     * test executor.
     *
     * @param uninstallIncompatibleApks uninstalls APKs on the device when an installation failure
     * occurs due to incompatible APKs such as INSTALL_FAILED_UPDATE_INCOMPATIBLE,
     * INCONSISTENT_CERTIFICATES, etc.
     * @param additionalTestOutputDir an additional test output directory on host machine, or null
     *     when disabled.
     */
    fun createRunnerConfigProtoForLocalDevice(
        device: DeviceConnector,
        testData: StaticTestData,
        appApks: Iterable<File>,
        additionalInstallOptions: Iterable<String>,
        helperApks: Iterable<File>,
        uninstallIncompatibleApks: Boolean,
        utpDependencies: UtpDependencies,
        versionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader,
        outputDir: File,
        tmpDir: File,
        retentionConfig: RetentionConfig,
        coverageOutputDir: File,
        useOrchestrator: Boolean,
        additionalTestOutputDir: File?,
        testResultListenerServerPort: Int,
        resultListenerClientCert: File,
        resultListenerClientPrivateKey: File,
        trustCertCollection: File,
        shardConfig: ShardConfig? = null
    ): RunnerConfigProto.RunnerConfig {
        return RunnerConfigProto.RunnerConfig.newBuilder().apply {
            val grpcInfo = findGrpcInfo(device.serialNumber)
            addDevice(createLocalDevice(device, uninstallIncompatibleApks, utpDependencies))
            addTestFixture(
                createTestFixture(
                    grpcInfo.port,
                    grpcInfo.token,
                    appApks,
                    additionalInstallOptions,
                    helperApks,
                    testData,
                    utpDependencies,
                    versionedSdkLoader,
                    outputDir,
                    tmpDir,
                    retentionConfig,
                    useOrchestrator,
                    additionalTestOutputDir,
                    additionalTestOutputDir?.let {
                        findAdditionalTestOutputDirectoryOnDevice(device, testData)
                    },
                    device.name,
                    coverageOutputDir,
                    shardConfig
                )
            )
            singleDeviceExecutor = createSingleDeviceExecutor(device.serialNumber, shardConfig)
            addTestResultListener(
                    createTestResultListener(
                            utpDependencies,
                            testResultListenerServerPort,
                            resultListenerClientCert,
                            resultListenerClientPrivateKey,
                            trustCertCollection,
                            device.serialNumber))
        }.build()
    }

    private fun createTestResultListener(
            utpDependencies: UtpDependencies,
            testResultListenerServerPort: Int,
            resultListenerClientCert: File,
            resultListenerClientPrivateKey: File,
            trustCertCollection: File,
            deviceId: String): ExtensionProto.Extension {
        return ANDROID_TEST_PLUGIN_RESULT_LISTENER_GRADLE.toExtensionProto(
            utpDependencies, GradleAndroidTestResultListenerConfig::newBuilder) {
            resultListenerServerPort = testResultListenerServerPort
            resultListenerClientCertFilePath = resultListenerClientCert.absolutePath
            resultListenerClientPrivateKeyFilePath = resultListenerClientPrivateKey.absolutePath
            trustCertCollectionFilePath = trustCertCollection.absolutePath
            this.deviceId = deviceId
        }
    }

    /**
     * Creates a runner config proto which you can pass into the Unified Test Platform's
     * test executor.
     *
     * This is for devices managed by the Gradle Plugin for Android as defined in the dsl.
     *
     * @param additionalTestOutputDir output directory for additional test output, or null if disabled
     */
    fun createRunnerConfigProtoForManagedDevice(
        device: UtpManagedDevice,
        testData: StaticTestData,
        appApks: Iterable<File>,
        additionalInstallOptions: Iterable<String>,
        helperApks: Iterable<File>,
        utpDependencies: UtpDependencies,
        versionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader,
        outputDir: File,
        tmpDir: File,
        retentionConfig: RetentionConfig,
        coverageOutputDir: File,
        additionalTestOutputDir: File?,
        useOrchestrator: Boolean,
        testResultListenerServerMetadata: UtpTestResultListenerServerMetadata,
        shardConfig: ShardConfig? = null
    ): RunnerConfigProto.RunnerConfig {
        return RunnerConfigProto.RunnerConfig.newBuilder().apply {
            addDevice(createGradleManagedDevice(device, utpDependencies))
            addTestFixture(
                createTestFixture(
                    null, null, appApks, additionalInstallOptions, helperApks, testData,
                    utpDependencies, versionedSdkLoader,
                    outputDir, tmpDir, retentionConfig, useOrchestrator,
                    additionalTestOutputDir,
                    additionalTestOutputDir?.let {
                        findAdditionalTestOutputDirectoryOnManagedDevice(device, testData)
                    },
                    device.deviceName, coverageOutputDir, shardConfig
                )
            )
            singleDeviceExecutor = createSingleDeviceExecutor(device.id, shardConfig)
            addTestResultListener(
                    createTestResultListener(
                            utpDependencies,
                            testResultListenerServerMetadata.serverPort,
                            testResultListenerServerMetadata.clientCert,
                            testResultListenerServerMetadata.clientPrivateKey,
                            testResultListenerServerMetadata.serverCert,
                            device.id))
        }.build()
    }

    /**
     * Creates a server config proto which you can pass into the Unified Test Platform's
     * test executor.
     */
    fun createServerConfigProto(): ServerConfigProto.ServerConfig {
        return ServerConfigProto.ServerConfig.newBuilder().apply {
            address = UTP_SERVER_ADDRESS
        }.build()
    }

    private fun createLocalDevice(
        device: DeviceConnector,
        uninstallIncompatibleApks: Boolean,
        utpDependencies: UtpDependencies
    ): DeviceProto.Device {
        return DeviceProto.Device.newBuilder().apply {
            deviceIdBuilder.apply {
                id = device.serialNumber
            }
            provider = createLocalDeviceProvider(device, uninstallIncompatibleApks, utpDependencies)
        }.build()
    }

    private fun createLocalDeviceProvider(
        device: DeviceConnector,
        uninstallIncompatibleApks: Boolean,
        utpDependencies: UtpDependencies
    ): ExtensionProto.Extension {
        val localConfig =  LocalAndroidDeviceProvider.newBuilder().apply {
            serial = device.serialNumber
        }.build()
        return ANDROID_DEVICE_PROVIDER_DDMLIB.toExtensionProto(
            utpDependencies, DdmlibAndroidDeviceProviderConfig::newBuilder) {
            localAndroidDeviceProviderConfig = Any.pack(localConfig)
            this.uninstallIncompatibleApks = uninstallIncompatibleApks
        }
    }

    private fun createGradleManagedDevice(
        managedDevice: UtpManagedDevice,
        utpDependencies: UtpDependencies
    ): DeviceProto.Device {
        return DeviceProto.Device.newBuilder().apply {
            deviceIdBuilder.apply {
                id = managedDevice.id
            }
            provider = createGradleDeviceProvider(managedDevice, utpDependencies)
        }.build()
    }

    private fun createGradleDeviceProvider(
        deviceInfo: UtpManagedDevice,
        utpDependencies: UtpDependencies
    ): ExtensionProto.Extension {
        return ANDROID_DEVICE_PROVIDER_GRADLE.toExtensionProto(
            utpDependencies, GradleManagedAndroidDeviceProviderConfig::newBuilder) {
            managedDeviceBuilder.apply {
                avdFolder = Any.pack(PathProto.Path.newBuilder().apply {
                    path = deviceInfo.avdFolder
                }.build())
                avdName = deviceInfo.avdName
                avdId = deviceInfo.id
                enableDisplay = deviceInfo.displayEmulator
                emulatorPath = Any.pack(PathProto.Path.newBuilder().apply {
                    path = deviceInfo.emulatorPath
                }.build())
                gradleDslDeviceName = deviceInfo.deviceName
            }
            adbServerPort = DEFAULT_ADB_SERVER_PORT
        }
    }

    /**
     * Creates the test fixture proto for the device to be run against.
     *
     * @param grpcPort the grpc port to communicate between UTP and the Android emulator. If null,
     * then the Icebox plugin will attempt to determine it (if enabled by the retentionConfig).
     */
    private fun createTestFixture(
        grpcPort: Int?,
        grpcToken: String?,
        appApks: Iterable<File>,
        additionalInstallOptions: Iterable<String>,
        helperApks: Iterable<File>,
        testData: StaticTestData,
        utpDependencies: UtpDependencies,
        versionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader,
        outputDir: File,
        tmpDir: File,
        retentionConfig: RetentionConfig,
        useOrchestrator: Boolean,
        additionalTestOutputDir: File?,
        additionalTestOutputOnDeviceDir: String?,
        deviceName: String,
        coverageOutputDir: File,
        shardConfig: ShardConfig?
    ): FixtureProto.TestFixture {
        return FixtureProto.TestFixture.newBuilder().apply {
            testFixtureIdBuilder.apply {
                id = UTP_TEST_FIXTURE_ID
            }
            environment = createEnvironment(
                outputDir,
                tmpDir,
                versionedSdkLoader
            )

            val debug =
                (testData.instrumentationRunnerArguments
                    .getOrDefault("debug", "false")
                    .toBoolean())
            if (retentionConfig.enabled && !debug) {
                val retentionTestData = testData.copy(
                    instrumentationRunnerArguments = testData.instrumentationRunnerArguments
                        .toMutableMap()
                        .apply { put("debug", "true") })
                testDriver = createTestDriver(
                    retentionTestData, utpDependencies, useOrchestrator,
                    additionalTestOutputOnDeviceDir, shardConfig
                )
                addHostPlugin(
                    createIceboxPlugin(
                        grpcPort, grpcToken, testData, utpDependencies, retentionConfig,
                        useOrchestrator
                    )
                )
            } else {
                if (retentionConfig.enabled && debug) {
                    logger.warn(
                        "Automated test snapshot does not work with debugging. Disabling " +
                                "automated test snapshot."
                    )
                }
                testDriver = createTestDriver(testData, utpDependencies, useOrchestrator,
                                              additionalTestOutputOnDeviceDir, shardConfig)
            }
            addHostPlugin(createAndroidTestPlugin(
                    testData, appApks, additionalInstallOptions, helperApks, utpDependencies))
            addHostPlugin(createAndroidTestDeviceInfoPlugin(utpDependencies))
            addHostPlugin(createAndroidTestLogcatPlugin(utpDependencies))
            if (testData.isTestCoverageEnabled) {
                addHostPlugin(createAndroidTestCoveragePlugin(
                    deviceName, coverageOutputDir, useOrchestrator, testData, utpDependencies
                ))
            }
            if (additionalTestOutputDir != null) {
                addHostPlugin(
                    createAdditionalTestOutputPlugin(
                        deviceName,
                        additionalTestOutputDir,
                        additionalTestOutputOnDeviceDir,
                        utpDependencies))
            }
        }.build()
    }

    private fun createIceboxPlugin(
            grpcPort: Int?,
            grpcToken: String?,
            testData: StaticTestData,
            utpDependencies: UtpDependencies,
            retentionConfig: RetentionConfig,
            rebootBetweenTestCases: Boolean
    ): ExtensionProto.Extension {
        return ANDROID_TEST_PLUGIN_HOST_RETENTION.toExtensionProto(
            utpDependencies, IceboxPlugin::newBuilder) {
            appPackage = testData.testedApplicationId
            emulatorGrpcAddress = DEFAULT_EMULATOR_GRPC_ADDRESS
            emulatorGrpcPort = grpcPort?:0
            emulatorGrpcToken = grpcToken?:""
            snapshotCompression = if (retentionConfig.compressSnapshots) {
                IceboxPluginProto.Compression.TARGZ
            } else {
                IceboxPluginProto.Compression.NONE
            }
            skipSnapshot = false
            maxSnapshotNumber = if (retentionConfig.retainAll) {
                0
            } else {
                retentionConfig.maxSnapshots
            }
            setupStrategy = if (rebootBetweenTestCases) {
                IceboxPluginProto.IceboxSetupStrategy.RECONNECT_BETWEEN_TEST_CASES
            } else {
                IceboxPluginProto.IceboxSetupStrategy.CONNECT_BEFORE_ALL_TEST
            }
        }
    }

    private fun createEnvironment(
        outputDir: File,
        tmpDir: File,
        versionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader
    ): EnvironmentProto.Environment {
        return EnvironmentProto.Environment.newBuilder().apply {
            outputDirBuilder.apply {
                path = outputDir.absolutePath
            }
            tmpDirBuilder.apply {
                path = tmpDir.absolutePath
            }
            androidEnvironmentBuilder.apply {
                androidSdkBuilder.apply {
                    sdkPathBuilder.apply {
                        path = versionedSdkLoader.sdkDirectoryProvider.get().asFile.absolutePath
                    }
                    adbPathBuilder.apply {
                        path = versionedSdkLoader.adbExecutableProvider.get().asFile.absolutePath
                    }
                    aaptPathBuilder.apply {
                        path = versionedSdkLoader.buildToolInfoProvider.get()
                            .getPath(BuildToolInfo.PathId.AAPT)
                    }
                    dexdumpPathBuilder.apply {
                        path = versionedSdkLoader.buildToolInfoProvider.get()
                            .getPath(BuildToolInfo.PathId.DEXDUMP)
                    }
                    testLogDirBuilder.apply {
                        path = TEST_LOG_DIR // Must be relative path to outputDir
                    }
                    testRunLogBuilder.apply {
                        path = TEST_RUNNER_LOG_FILE_NAME
                    }
                }
            }
        }.build()
    }

    private fun createTestDriver(
        testData: StaticTestData,
        utpDependencies: UtpDependencies,
        useOrchestrator: Boolean,
        additionalTestOutputOnDeviceDir: String?,
        // TODO(b/201577913): remove
        shardConfig: ShardConfig?
    ): ExtensionProto.Extension {
        return ANDROID_DRIVER_INSTRUMENTATION.toExtensionProto(
            utpDependencies, AndroidInstrumentationDriver::newBuilder) {
            androidInstrumentationRuntimeBuilder.apply {
                instrumentationInfoBuilder.apply {
                    appPackage = testData.testedApplicationId
                    testPackage = testData.applicationId
                    testRunnerClass = testData.instrumentationRunner
                }
                instrumentationArgsBuilder.apply {
                    putAllArgsMap(testData.instrumentationRunnerArguments)

                    useTestStorageService = testData.instrumentationRunnerArguments.getOrDefault(
                        "useTestStorageService", "false").toBoolean()

                    if (testData.isTestCoverageEnabled) {
                        putArgsMap("coverage", "true")
                        val testCoverageArgName = if (useOrchestrator) {
                            "coverageFilePath"
                        } else {
                            "coverageFile"
                        }
                        putArgsMap(
                            testCoverageArgName,
                            testData.getTestCoverageFilePath(useOrchestrator))
                    }

                    if (additionalTestOutputOnDeviceDir != null) {
                        putArgsMap("additionalTestOutputDir", additionalTestOutputOnDeviceDir)
                    }

                    // TODO(b/201577913): remove
                    if (shardConfig != null) {
                        require(
                            !testData.instrumentationRunnerArguments.containsKey("numShards") &&
                            !testData.instrumentationRunnerArguments.containsKey("shardIndex")) {
                            "testInstrumentationRunnerArguments.[numShards | shardIndex] is " +
                            "currently incompatible with sharding support for Gradle Managed " +
                            "Devices, and you should try running this test again without setting " +
                            "this property."
                        }
                        putArgsMap("numShards", shardConfig.totalCount.toString())
                        putArgsMap("shardIndex", shardConfig.index.toString())
                    }

                    noWindowAnimation = testData.animationsDisabled
                }
            }
            this.useOrchestrator = useOrchestrator
            amInstrumentTimeout = AM_INSTRUMENT_COMMAND_TIME_OUT_SECONDS
        }
    }

    /**
     * @param additionalInstallOptions an additional install options to be used for installing
     *   app (tested-) APKs and test APK. These options are not used for the test helper APKs.
     */
    private fun createAndroidTestPlugin(
            testData: StaticTestData,
            appApks: Iterable<File>,
            additionalInstallOptions: Iterable<String>,
            helperApks: Iterable<File>,
            utpDependencies: UtpDependencies
    ): ExtensionProto.Extension {
        return ANDROID_TEST_PLUGIN.toExtensionProto(
            utpDependencies, AndroidDevicePlugin::newBuilder) {
            addTestApksBuilder().apply {
                testApkBuilder.apply {
                    type = TestArtifactProto.ArtifactType.ANDROID_APK
                    sourcePathBuilder.apply {
                        path = testData.testApk.absolutePath
                    }
                }
                additionalInstallOptions.forEach { option ->
                    addInstallOptionsBuilder().apply {
                        commandLineParameter = option
                    }
                }
            }
            appApks.forEach { appApk ->
                addTestApksBuilder().apply {
                    testApkBuilder.apply {
                        type = TestArtifactProto.ArtifactType.ANDROID_APK
                        sourcePathBuilder.apply {
                            path = appApk.absolutePath
                        }
                    }
                    additionalInstallOptions.forEach { option ->
                        addInstallOptionsBuilder().apply {
                            commandLineParameter = option
                        }
                    }
                }
            }
            helperApks.forEach { helperApk ->
                addTestServiceApksBuilder().apply {
                    type = TestArtifactProto.ArtifactType.ANDROID_APK
                    sourcePathBuilder.apply {
                        path = helperApk.absolutePath
                    }
                }
            }
        }
    }

    private fun createAndroidTestDeviceInfoPlugin(utpDependencies: UtpDependencies): ExtensionProto.Extension {
        return ANDROID_TEST_DEVICE_INFO_PLUGIN.toExtensionProto(utpDependencies)
    }

    /**
     * Creates and configures AndroidTestCoverage UTP plugin.
     *
     * It specifies two paths, a directory or file path to writes test coverage files on device and
     * a destination directory on a host. This logic used to be implemented in SimpleTestRunnable
     * and this new implementation is compatible with it (a drop-in replacemant).
     */
    private fun createAndroidTestCoveragePlugin(
        deviceName: String,
        coverageOutputDir: File,
        useOrchestrator: Boolean,
        testData: StaticTestData,
        utpDependencies:UtpDependencies): ExtensionProto.Extension {
        val coverageFilePath = testData.getTestCoverageFilePath(useOrchestrator)

        return ANDROID_TEST_COVERAGE_PLUGIN.toExtensionProto(
            utpDependencies, AndroidTestCoverageConfig::newBuilder) {
            if (useOrchestrator) {
                multipleCoverageFilesInDirectory = coverageFilePath
            } else {
                singleCoverageFile = coverageFilePath
            }
            outputDirectoryOnHost = coverageOutputDir.absolutePath +
                    File.separator + deviceName + File.separator
            runAsPackageName = testData.instrumentationTargetPackageId
            useTestStorageService = testData.instrumentationRunnerArguments.getOrDefault(
                "useTestStorageService", "false").toBoolean()
        }
    }

    private fun StaticTestData.getTestCoverageFilePath(useOrchestrator: Boolean): String {
        val customCoveragePath =
            instrumentationRunnerArguments.getOrDefault("coverageFilePath", "")
        return when {
            customCoveragePath.isNotBlank() -> {
                customCoveragePath
            }
            useOrchestrator -> {
                "/data/data/${instrumentationTargetPackageId}/coverage_data/"
            }
            else -> {
                "/data/data/${instrumentationTargetPackageId}/coverage.ec"
            }
        }
    }

    private fun createAdditionalTestOutputPlugin(
        deviceName: String,
        additionalTestOutputDir: File,
        additionalTestOutputOnDeviceDir: String?,
        utpDependencies:UtpDependencies): ExtensionProto.Extension {
        return ANDROID_TEST_ADDITIONAL_TEST_OUTPUT_PLUGIN.toExtensionProto(
            utpDependencies, AndroidAdditionalTestOutputConfig::newBuilder) {
            additionalOutputDirectoryOnHost =
                additionalTestOutputDir.absolutePath + File.separator + deviceName + File.separator
            additionalTestOutputOnDeviceDir?.let {
                additionalOutputDirectoryOnDevice = it
            }
        }
    }

    private fun createAndroidTestLogcatPlugin(utpDependencies:UtpDependencies): ExtensionProto.Extension {
        return ANDROID_TEST_LOGCAT_PLUGIN.toExtensionProto(utpDependencies)
    }

    private fun createSingleDeviceExecutor(
        identifier: String,
        shardConfig: ShardConfig?): ExecutorProto.SingleDeviceExecutor {
        return ExecutorProto.SingleDeviceExecutor.newBuilder().apply {
            deviceExecutionBuilder.apply {
                deviceIdBuilder.apply {
                    id = identifier
                }
                testFixtureIdBuilder.apply {
                    id = UTP_TEST_FIXTURE_ID
                }
            }
            shardConfig?.let {
                shardingConfigBuilder.apply {
                    shardCount = it.totalCount
                    shardIndex = it.index
                }
            }
        }.build()
    }

    /**
     * Creates [ExtensionProto.Extension] for the given [UtpDependency] with a config.
     */
    private fun UtpDependency.toExtensionProto(
            utpDependencies: UtpDependencies,
            config: Any? = null): ExtensionProto.Extension {
        val builder = ExtensionProto.Extension.newBuilder().apply {
            label = LabelProto.Label.newBuilder().apply {
                label = name
            }.build()
            className = mainClass
            addAllJar(mapperFunc(utpDependencies).files.map {
                PathProto.Path.newBuilder().apply {
                    path = it.absolutePath
                }.build()
            })
        }
        if (config != null) {
            builder.config = config
        }
        return builder.build()
    }

    private fun <T : Message.Builder> UtpDependency.toExtensionProto(
        utpDependencies: UtpDependencies,
        newBuilder: () -> T,
        configFunc: T.() -> Unit): ExtensionProto.Extension {
        val config = newBuilder().apply {
            configFunc(this)
        }.build()
        return toExtensionProto(utpDependencies, Any.pack(config))
    }
}
