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
import com.android.build.gradle.internal.testing.utp.UtpDependency.ANDROID_TEST_PLUGIN
import com.android.build.gradle.internal.testing.utp.UtpDependency.ANDROID_TEST_DEVICE_INFO_PLUGIN
import com.android.build.gradle.internal.testing.utp.UtpDependency.ANDROID_TEST_PLUGIN_HOST_RETENTION
import com.android.builder.testing.api.DeviceConnector
import com.android.sdklib.BuildToolInfo
import com.google.protobuf.Any
import com.google.testing.platform.plugin.android.icebox.host.proto.IceboxPluginProto
import com.google.testing.platform.plugin.android.icebox.host.proto.IceboxPluginProto.IceboxPlugin
import com.google.testing.platform.proto.api.config.AndroidInstrumentationDriverProto
import com.google.testing.platform.proto.api.config.DeviceProto
import com.google.testing.platform.proto.api.config.EnvironmentProto
import com.google.testing.platform.proto.api.config.ExecutorProto
import com.google.testing.platform.proto.api.config.FixtureProto
import com.google.testing.platform.proto.api.config.GradleManagedAndroidDeviceProviderProto
import com.google.testing.platform.proto.api.config.LocalAndroidDeviceProviderProto
import com.google.testing.platform.proto.api.config.RunnerConfigProto
import com.google.testing.platform.proto.api.core.ExtensionProto
import com.google.testing.platform.proto.api.core.LabelProto
import com.google.testing.platform.proto.api.core.PathProto
import com.google.testing.platform.proto.api.core.TestArtifactProto
import com.google.testing.platform.proto.api.service.ServerConfigProto
import java.io.File
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

/**
 * A factory class to construct UTP runner and server configuration protos.
 */
class UtpConfigFactory {

    val logger = Logging.getLogger(this.javaClass)
    /**
     * Creates a runner config proto which you can pass into the Unified Test Platform's
     * test executor.
     */
    fun createRunnerConfigProtoForLocalDevice(
        device: DeviceConnector,
        testData: StaticTestData,
        apks: Iterable<File>,
        utpDependencies: UtpDependencies,
        sdkComponents: SdkComponentsBuildService,
        outputDir: File,
        tmpDir: File,
        testLogDir: File,
        retentionConfig: RetentionConfig,
        useOrchestrator: Boolean
    ): RunnerConfigProto.RunnerConfig {
        return RunnerConfigProto.RunnerConfig.newBuilder().apply {
            addDevice(createLocalDevice(device, testData, utpDependencies))
            addTestFixture(
                createTestFixture(
                    findGrpcPort(device.serialNumber),
                    apks,
                    testData,
                    utpDependencies,
                    sdkComponents,
                    outputDir,
                    tmpDir,
                    testLogDir,
                    retentionConfig,
                    useOrchestrator
                )
            )
            singleDeviceExecutor = createSingleDeviceExecutor(device.serialNumber)
        }.build()
    }

    /**
     * Creates a runner config proto which you can pass into the Unified Test Platform's
     * test executor.
     *
     * This is for devices managed by the Gradle Plugin for Android as defined in the dsl.
     */
    fun createRunnerConfigProtoForManagedDevice(
        device: UtpManagedDevice,
        testData: StaticTestData,
        apks: Iterable<File>,
        utpDependencies: UtpDependencies,
        sdkComponents: SdkComponentsBuildService,
        outputDir: File,
        tmpDir: File,
        testLogDir: File,
        retentionConfig: RetentionConfig,
        useOrchestrator: Boolean
    ): RunnerConfigProto.RunnerConfig {
        return RunnerConfigProto.RunnerConfig.newBuilder().apply {
            addDevice(createGradleManagedDevice(device, testData, utpDependencies))
            addTestFixture(
                createTestFixture(
                    null, apks, testData, utpDependencies, sdkComponents,
                    outputDir, tmpDir, testLogDir, retentionConfig, useOrchestrator
                )
            )
            singleDeviceExecutor = createSingleDeviceExecutor(device.id)
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
        testData: StaticTestData,
        utpDependencies: UtpDependencies
    ): DeviceProto.Device {
        return DeviceProto.Device.newBuilder().apply {
            deviceIdBuilder.apply {
                id = device.serialNumber
            }
            provider = createLocalDeviceProvider(device, utpDependencies)
        }.build()
    }

    private fun createLocalDeviceProvider(
        device: DeviceConnector,
        utpDependencies: UtpDependencies
    ): ExtensionProto.Extension {
        return ExtensionProto.Extension.newBuilder().apply {
            label = LabelProto.Label.newBuilder().apply {
                label = "local_android_device_provider"
            }.build()
            className = ANDROID_DEVICE_PROVIDER_DDMLIB.mainClass
            addAllJar(utpDependencies.deviceControllerDdmlib.files.map {
                PathProto.Path.newBuilder().apply {
                    path = it.absolutePath
                }.build()
            })
            config =
                Any.pack(LocalAndroidDeviceProviderProto.LocalAndroidDeviceProvider.newBuilder().apply {
                    serial = device.serialNumber
                }.build())
        }.build()
    }

    private fun createGradleManagedDevice(
        managedDevice: UtpManagedDevice,
        testData: StaticTestData,
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
        return ExtensionProto.Extension.newBuilder().apply {
            label = LabelProto.Label.newBuilder().apply {
                label = "gradle_managed_android_device_provider_config"
            }.build()
            className = ANDROID_DEVICE_PROVIDER_GRADLE.mainClass
            addAllJar(utpDependencies.deviceProviderGradle.files.map {
                PathProto.Path.newBuilder().apply {
                    path = it.absolutePath
                }.build()
            })
            config =
                Any.pack(
                    GradleManagedAndroidDeviceProviderProto
                        .GradleManagedAndroidDeviceProviderConfig
                            .newBuilder().apply {
                                managedDeviceBuilder.apply {
                                    avdFolder = PathProto.Path.newBuilder().apply {
                                        path = deviceInfo.avdFolder
                                    }.build()
                                    avdName = deviceInfo.avdName
                                    avdId = deviceInfo.id
                                    enableDisplay = deviceInfo.displayEmulator
                                    emulatorPath = PathProto.Path.newBuilder().apply {
                                        path = deviceInfo.emulatorPath
                                    }.build()
                                }
                                adbServerPort = DEFAULT_ADB_SERVER_PORT
                            }.build())
        }.build()
    }

    private fun createTestFixture(
        grpcPort: Int?,
        apks: Iterable<File>,
        testData: StaticTestData,
        utpDependencies: UtpDependencies,
        sdkComponents: SdkComponentsBuildService,
        outputDir: File,
        tmpDir: File,
        testLogDir: File,
        retentionConfig: RetentionConfig,
        useOrchestrator: Boolean
    ): FixtureProto.TestFixture {
        return FixtureProto.TestFixture.newBuilder().apply {
            testFixtureIdBuilder.apply {
                id = UTP_TEST_FIXTURE_ID
            }
            setupBuilder.apply {
                addAllInstallable(apks.map { apk ->
                    TestArtifactProto.Artifact.newBuilder().apply {
                        type = TestArtifactProto.ArtifactType.ANDROID_APK
                        sourcePathBuilder.apply {
                            path = apk.absolutePath
                        }
                    }.build()
                })
            }
            environment = createEnvironment(
                outputDir,
                tmpDir,
                testLogDir,
                sdkComponents
            )

            if (retentionConfig.enabled && !useOrchestrator && grpcPort != null) {
                val retentionTestData = testData.copy(
                    instrumentationRunnerArguments = testData.instrumentationRunnerArguments
                        .toMutableMap()
                        .apply { put("debug", "true") })
                testDriver = createTestDriver(
                    retentionTestData, utpDependencies, useOrchestrator
                )
                addHostPlugin(ExtensionProto.Extension.newBuilder().apply {
                    label = LabelProto.Label.newBuilder().apply {
                        label = "icebox_plugin"
                    }.build()
                    className = ANDROID_TEST_PLUGIN_HOST_RETENTION.mainClass
                    config = Any.pack(IceboxPlugin.newBuilder().apply {
                        appPackage = testData.testedApplicationId
                        // TODO(155308548): query device for the following fields
                        emulatorGrpcAddress = DEFAULT_EMULATOR_GRPC_ADDRESS
                        emulatorGrpcPort = grpcPort
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
                    }.build())
                    addAllJar(
                        utpDependencies.testPluginHostRetention.files.map {
                            PathProto.Path.newBuilder().apply {
                                path = it.absolutePath
                            }.build()
                        })
                }.build())
            } else {
                if (retentionConfig.enabled) {
                    if (useOrchestrator) {
                        logger.error("Currently Retention does not work with orchestrator. " +
                                "Disabling Android Test Retention.");
                    } else if (grpcPort == null) {
                        logger.error(
                            "GRPC port of the emulator not set. Disabling Android Test Retention."
                        );
                    }
                }
                testDriver = createTestDriver(testData, utpDependencies, useOrchestrator)
            }
            addHostPlugin(createAndroidTestPlugin(utpDependencies))
            addHostPlugin(createAndroidTestDeviceInfoPlugin(utpDependencies))
        }.build()
    }

    private fun createEnvironment(
        outputDir: File,
        tmpDir: File,
        testLogDir: File,
        sdkComponents: SdkComponentsBuildService
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
                        path = sdkComponents.sdkDirectoryProvider.get().asFile.absolutePath
                    }
                    adbPathBuilder.apply {
                        path = sdkComponents.adbExecutableProvider.get().asFile.absolutePath
                    }
                    aaptPathBuilder.apply {
                        path = sdkComponents.buildToolInfoProvider.get()
                            .getPath(BuildToolInfo.PathId.AAPT)
                    }
                    dexdumpPathBuilder.apply {
                        path = sdkComponents.buildToolInfoProvider.get()
                            .getPath(BuildToolInfo.PathId.DEXDUMP)
                    }
                    testLogDirBuilder.apply {
                        path = testLogDir.path // Must be relative path to outputDir
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
        useOrchestrator: Boolean
    ): ExtensionProto.Extension {
        return ExtensionProto.Extension.newBuilder().apply {
            className = ANDROID_DRIVER_INSTRUMENTATION.mainClass
            labelBuilder.apply {
                label = ANDROID_DRIVER_INSTRUMENTATION.name
            }
            addAllJar(utpDependencies.driverInstrumentation.files.map {
                PathProto.Path.newBuilder().apply {
                    path = it.absolutePath
                }.build()
            })
            config =
                Any.pack(AndroidInstrumentationDriverProto.AndroidInstrumentationDriver.newBuilder().apply {
                    androidInstrumentationRuntimeBuilder.apply {
                        instrumentationInfoBuilder.apply {
                            appPackage = testData.testedApplicationId
                            testPackage = testData.applicationId
                            testRunnerClass = testData.instrumentationRunner
                        }
                        instrumentationArgsBuilder.apply {
                            putAllArgsMap(testData.instrumentationRunnerArguments)
                        }
                    }
                    this.useOrchestrator = useOrchestrator
                }.build())
        }.build()
    }

    private fun createAndroidTestPlugin(utpDependencies: UtpDependencies): ExtensionProto.Extension {
        return ExtensionProto.Extension.newBuilder().apply {
            label = LabelProto.Label.newBuilder().apply {
                label = "android_device_plugin"
            }.build()
            className = ANDROID_TEST_PLUGIN.mainClass
            addAllJar(utpDependencies.testPlugin.files.map {
                PathProto.Path.newBuilder().apply {
                    path = it.absolutePath
                }.build()
            })
        }.build()
    }

    private fun createAndroidTestDeviceInfoPlugin(utpDependencies: UtpDependencies): ExtensionProto.Extension {
        return ExtensionProto.Extension.newBuilder().apply {
            label = LabelProto.Label.newBuilder().apply {
                label = "android_test_device_info_plugin"
            }.build()
            className = ANDROID_TEST_DEVICE_INFO_PLUGIN.mainClass
            addAllJar(utpDependencies.testDeviceInfoPlugin.files.map {
                PathProto.Path.newBuilder().apply {
                    path = it.absolutePath
                }.build()
            })
        }.build()
    }

    private fun createSingleDeviceExecutor(identifier: String): ExecutorProto.SingleDeviceExecutor {
        return ExecutorProto.SingleDeviceExecutor.newBuilder().apply {
            deviceExecutionBuilder.apply {
                deviceIdBuilder.apply {
                    id = identifier
                }
                testFixtureIdBuilder.apply {
                    id = UTP_TEST_FIXTURE_ID
                }
            }
        }.build()
    }
}
