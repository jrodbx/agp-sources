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
import com.android.build.gradle.internal.testing.utp.UtpDependency.ANDROID_DEVICE_CONTROLLER_ADB
import com.android.build.gradle.internal.testing.utp.UtpDependency.ANDROID_DEVICE_PROVIDER_LOCAL
import com.android.build.gradle.internal.testing.utp.UtpDependency.ANDROID_DRIVER_INSTRUMENTATION
import com.android.build.gradle.internal.testing.utp.UtpDependency.ANDROID_TEST_PLUGIN
import com.android.build.gradle.internal.testing.utp.UtpDependency.ANDROID_TEST_PLUGIN_HOST_RETENTION
import com.android.builder.testing.api.DeviceConnector
import com.android.sdklib.BuildToolInfo
import com.google.protobuf.Any
import com.google.test.platform.config.v1.proto.AdbDeviceControllerProto
import com.google.test.platform.config.v1.proto.DeviceControllerProto
import com.google.test.platform.config.v1.proto.DeviceProto
import com.google.test.platform.config.v1.proto.DeviceProviderProto
import com.google.test.platform.config.v1.proto.EnvironmentProto
import com.google.test.platform.config.v1.proto.ExecutorProto
import com.google.test.platform.config.v1.proto.FixtureProto
import com.google.test.platform.config.v1.proto.HostPluginProto
import com.google.test.platform.config.v1.proto.LocalAndroidDeviceProviderProto
import com.google.test.platform.config.v1.proto.AndroidInstrumentationDriverProto
import com.google.test.platform.config.v1.proto.RunnerConfigProto
import com.google.test.platform.plugin.android.icebox.host.proto.IceboxPluginProto.IceboxPlugin
import com.google.test.platform.core.proto.ExtensionProto
import com.google.test.platform.core.proto.PathProto
import com.google.test.platform.core.proto.TestArtifactProto
import com.google.test.platform.server.proto.ServerConfigProto
import org.gradle.api.artifacts.ConfigurationContainer
import java.io.File

// This is an arbitrary string. This ID is used to lookup test results from UTP.
// UTP can run multiple test fixtures at a time so we have to give a name for
// each test fixture. However, AGP always has only one test fixture in the
// runner-config so this string is arbitrary.
private const val UTP_TEST_FIXTURE_ID = "AGP_Test_Fixture"

// A UTP gRPC server address.
private const val UTP_SERVER_ADDRESS = "localhost:20000"

// Emulator gRPC address
private const val DEFAULT_EMULATOR_GRPC_ADDRESS = "localhost";
// Emulator gRPC port
private const val DEFAULT_EMULATOR_GRPC_PORT = 8554;
/**
 * A factory class to construct UTP runner and server configuration protos.
 */
class UtpConfigFactory {

    /**
     * Creates a runner config proto which you can pass into the Unified Test Platform's
     * test executor.
     */
    fun createRunnerConfigProto(device: DeviceConnector,
                                testData: StaticTestData,
                                apks: Iterable<File>,
                                configurations: ConfigurationContainer,
                                sdkComponents: SdkComponentsBuildService,
                                outputDir: File,
                                tmpDir: File,
                                testLogDir: File,
                                testRunLogDir: File,
                                usesIcebox: Boolean): RunnerConfigProto.RunnerConfig {
        return RunnerConfigProto.RunnerConfig.newBuilder().apply {
            addDevice(createLocalDevice(device, testData, configurations))
            addTestFixture(createTestFixture(
                    device, apks, testData, configurations, sdkComponents,
                    outputDir, tmpDir, testLogDir, testRunLogDir,
                    usesIcebox))
            singleDeviceExecutor = createSingleDeviceExecutor(device)
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

    private fun createLocalDevice(device: DeviceConnector,
                                  testData: StaticTestData,
                                  configurations: ConfigurationContainer): DeviceProto.Device {
        return DeviceProto.Device.newBuilder().apply {
            deviceIdBuilder.apply {
                id = device.serialNumber
            }
            provider = createLocalDeviceProvider(device, configurations)
            controller = createAdbDeviceController(configurations)
        }.build()
    }

    private fun createLocalDeviceProvider(device: DeviceConnector,
                                          configurations: ConfigurationContainer): DeviceProviderProto.DeviceProvider {
        return DeviceProviderProto.DeviceProvider.newBuilder().apply {
            providerClass = ANDROID_DEVICE_PROVIDER_LOCAL.mainClass
            addAllProviderJar(configurations.getByName(ANDROID_DEVICE_PROVIDER_LOCAL.configurationName).files.map {
                PathProto.Path.newBuilder().apply {
                    path = it.absolutePath
                }.build()
            })
            deviceProviderConfig = Any.pack(LocalAndroidDeviceProviderProto.LocalAndroidDeviceProvider.newBuilder().apply {
                serial = device.serialNumber
            }.build())
        }.build()
    }

    private fun createAdbDeviceController(configurations: ConfigurationContainer): DeviceControllerProto.DeviceController {
        return DeviceControllerProto.DeviceController.newBuilder().apply {
            controllerClass = ANDROID_DEVICE_CONTROLLER_ADB.mainClass
            addAllControllerJar(configurations.getByName(ANDROID_DEVICE_CONTROLLER_ADB.configurationName).files.map {
                PathProto.Path.newBuilder().apply {
                    path = it.absolutePath
                }.build()
            })
            deviceControllerConfig = Any.pack(AdbDeviceControllerProto.AdbDeviceController.getDefaultInstance())
        }.build()
    }

    private fun createTestFixture(device: DeviceConnector,
                                  apks: Iterable<File>,
                                  testData: StaticTestData,
                                  configurations: ConfigurationContainer,
                                  sdkComponents: SdkComponentsBuildService,
                                  outputDir: File,
                                  tmpDir: File,
                                  testLogDir: File,
                                  testRunLogDir: File,
                                  usesRetention: Boolean): FixtureProto.TestFixture {
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
                    testRunLogDir,
                    sdkComponents)

            if (usesRetention) {
                var retentionTestData = testData.copy(
                    instrumentationRunnerArguments = testData.instrumentationRunnerArguments
                        .toMutableMap()
                        .apply { put("debug", "true") })
                testDriver = createTestDriver(retentionTestData, configurations)
                addHostPlugin(HostPluginProto.HostPlugin.newBuilder().apply {
                    pluginClass = ANDROID_TEST_PLUGIN_HOST_RETENTION.mainClass
                    pluginConfig = Any.pack(IceboxPlugin.newBuilder().apply{
                        appPackage = testData.testedApplicationId
                        // TODO(155308548): query device for the following fields
                        emulatorGrpcAddress = DEFAULT_EMULATOR_GRPC_ADDRESS
                        emulatorGrpcPort = DEFAULT_EMULATOR_GRPC_PORT
                    }.build())
                    addAllPluginJar(
                        configurations.getByName(
                            ANDROID_TEST_PLUGIN_HOST_RETENTION.configurationName).files.map {
                                PathProto.Path.newBuilder().apply {
                                    path = it.absolutePath
                                }.build()
                    })
                }.build())
            } else {
                testDriver = createTestDriver(testData, configurations)
            }
            addHostPlugin(createAndroidTestPlugin(configurations))
        }.build()
    }

    private fun createEnvironment(
            outputDir: File,
            tmpDir: File,
            testLogDir: File,
            testRunLogDir: File,
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
                        path = sdkComponents.buildToolInfoProvider.get().getPath(BuildToolInfo.PathId.AAPT)
                    }
                    dexdumpPathBuilder.apply {
                        path = sdkComponents.buildToolInfoProvider.get().getPath(BuildToolInfo.PathId.DEXDUMP)
                    }
                    testLogDirBuilder.apply {
                        path = testLogDir.absolutePath
                    }
                    testRunLogBuilder.apply {
                        path = testRunLogDir.absolutePath
                    }
                }
            }
        }.build()
    }

    private fun createTestDriver(testData: StaticTestData,
                                 configurations: ConfigurationContainer): ExtensionProto.Extension {
        return ExtensionProto.Extension.newBuilder().apply {
            className = ANDROID_DRIVER_INSTRUMENTATION.mainClass
            labelBuilder.apply {
                label = ANDROID_DRIVER_INSTRUMENTATION.name
            }
            addAllJar(configurations.getByName(ANDROID_DRIVER_INSTRUMENTATION.configurationName).files.map {
                PathProto.Path.newBuilder().apply {
                    path = it.absolutePath
                }.build()
            })
            config = Any.pack(AndroidInstrumentationDriverProto.AndroidInstrumentationDriver.newBuilder().apply {
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
            }.build())
        }.build()
    }

    private fun createAndroidTestPlugin(configurations: ConfigurationContainer): HostPluginProto.HostPlugin {
        return HostPluginProto.HostPlugin.newBuilder().apply {
            pluginClass = ANDROID_TEST_PLUGIN.mainClass
            addAllPluginJar(configurations.getByName(ANDROID_TEST_PLUGIN.configurationName).files.map {
                PathProto.Path.newBuilder().apply {
                    path = it.absolutePath
                }.build()
            })
        }.build()
    }

    private fun createAndroidTestPluginHostRetention(configurations: ConfigurationContainer): HostPluginProto.HostPlugin {
        return HostPluginProto.HostPlugin.newBuilder().apply {
            pluginClass = ANDROID_TEST_PLUGIN_HOST_RETENTION.mainClass
            addAllPluginJar(configurations.getByName(ANDROID_TEST_PLUGIN_HOST_RETENTION.configurationName).files.map {
                PathProto.Path.newBuilder().apply {
                    path = it.absolutePath
                }.build()
            })
        }.build()
    }

    private fun createSingleDeviceExecutor(device: DeviceConnector): ExecutorProto.SingleDeviceExecutor {
        return ExecutorProto.SingleDeviceExecutor.newBuilder().apply {
            deviceExecutionBuilder.apply {
                deviceIdBuilder.apply {
                    id = device.serialNumber
                }
                testFixtureIdBuilder.apply {
                    id = UTP_TEST_FIXTURE_ID
                }
            }
        }.build()
    }
}