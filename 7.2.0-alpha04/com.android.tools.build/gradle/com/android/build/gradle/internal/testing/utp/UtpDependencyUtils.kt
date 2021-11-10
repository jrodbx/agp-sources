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

import com.android.Version.ANDROID_TOOLS_BASE_VERSION
import org.gradle.api.NonExtensible
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Optional

private const val UTP_MAVEN_GROUP_ID = "com.google.testing.platform"
private const val UTP_DEFAULT_VERSION = "0.0.8-alpha07"
private const val ANDROID_TOOLS_UTP_PLUGIN_MAVEN_GROUP_ID = "com.android.tools.utp"
private val ANDROID_TOOLS_UTP_PLUGIN_VERSION = ANDROID_TOOLS_BASE_VERSION

/**
 * Available Unified Test Platform dependencies.
 */
enum class UtpDependency(
        val artifactId: String,
        val mainClass: String,
        val mapperFunc: (UtpDependencies) -> ConfigurableFileCollection,
        private val groupId: String = UTP_MAVEN_GROUP_ID,
        private val version: String = UTP_DEFAULT_VERSION) {
    LAUNCHER(
            "launcher",
            "com.google.testing.platform.launcher.Launcher",
            UtpDependencies::launcher),
    CORE(
            "core",
            "com.google.testing.platform.main.MainKt",
            UtpDependencies::core),
    ANDROID_DEVICE_PROVIDER_DDMLIB(
            "android-device-provider-ddmlib",
            "com.android.tools.utp.plugins.deviceprovider.ddmlib.DdmlibAndroidDeviceProvider",
            UtpDependencies::deviceControllerDdmlib,
            ANDROID_TOOLS_UTP_PLUGIN_MAVEN_GROUP_ID,
            ANDROID_TOOLS_UTP_PLUGIN_VERSION),
    ANDROID_DEVICE_PROVIDER_GRADLE(
            "android-device-provider-gradle",
            "com.android.tools.utp.plugins.deviceprovider.gradle.GradleManagedAndroidDeviceProvider",
            UtpDependencies::deviceProviderGradle,
            ANDROID_TOOLS_UTP_PLUGIN_MAVEN_GROUP_ID,
            ANDROID_TOOLS_UTP_PLUGIN_VERSION),
    ANDROID_DRIVER_INSTRUMENTATION(
            "android-driver-instrumentation",
            "com.google.testing.platform.runtime.android.driver.AndroidInstrumentationDriver",
            UtpDependencies::driverInstrumentation),
    ANDROID_TEST_PLUGIN(
            "android-test-plugin",
            "com.google.testing.platform.plugin.android.AndroidDevicePlugin",
            UtpDependencies::testPlugin),
    ANDROID_TEST_DEVICE_INFO_PLUGIN(
            "android-test-plugin-host-device-info",
            "com.android.tools.utp.plugins.host.device.info.AndroidTestDeviceInfoPlugin",
            UtpDependencies::testDeviceInfoPlugin,
            ANDROID_TOOLS_UTP_PLUGIN_MAVEN_GROUP_ID,
            ANDROID_TOOLS_UTP_PLUGIN_VERSION),
    ANDROID_TEST_ADDITIONAL_TEST_OUTPUT_PLUGIN(
            "android-test-plugin-host-additional-test-output",
            "com.android.tools.utp.plugins.host.additionaltestoutput.AndroidAdditionalTestOutputPlugin",
            UtpDependencies::additionalTestOutputPlugin,
            ANDROID_TOOLS_UTP_PLUGIN_MAVEN_GROUP_ID,
            ANDROID_TOOLS_UTP_PLUGIN_VERSION),
    ANDROID_TEST_COVERAGE_PLUGIN(
            "android-test-plugin-host-coverage",
            "com.android.tools.utp.plugins.host.coverage.AndroidTestCoveragePlugin",
            UtpDependencies::testCoveragePlugin,
            ANDROID_TOOLS_UTP_PLUGIN_MAVEN_GROUP_ID,
            ANDROID_TOOLS_UTP_PLUGIN_VERSION),
    ANDROID_TEST_LOGCAT_PLUGIN(
            "android-test-plugin-host-logcat",
            "com.android.tools.utp.plugins.host.logcat.AndroidTestLogcatPlugin",
            UtpDependencies::testLogcatPlugin,
            ANDROID_TOOLS_UTP_PLUGIN_MAVEN_GROUP_ID,
            ANDROID_TOOLS_UTP_PLUGIN_VERSION),
    ANDROID_TEST_PLUGIN_HOST_RETENTION(
            "android-test-plugin-host-retention",
            "com.android.tools.utp.plugins.host.icebox.IceboxPlugin",
            UtpDependencies::testPluginHostRetention,
            ANDROID_TOOLS_UTP_PLUGIN_MAVEN_GROUP_ID,
            ANDROID_TOOLS_UTP_PLUGIN_VERSION),
    ANDROID_TEST_PLUGIN_RESULT_LISTENER_GRADLE(
            "android-test-plugin-result-listener-gradle",
            "com.android.tools.utp.plugins.result.listener.gradle.GradleAndroidTestResultListener",
            UtpDependencies::testPluginResultListenerGradle,
            ANDROID_TOOLS_UTP_PLUGIN_MAVEN_GROUP_ID,
            ANDROID_TOOLS_UTP_PLUGIN_VERSION)
    ;

    val configurationName: String = "_internal-unified-test-platform-${artifactId}"

    /**
     * Returns a maven coordinate string to download dependencies from the Maven repository.
     */
    fun mavenCoordinate(): String = "${groupId}:${artifactId}:${version}"
}

@NonExtensible
abstract class UtpDependencies {
    @get:Optional
    @get:Classpath
    abstract val launcher: ConfigurableFileCollection

    @get:Optional
    @get:Classpath
    abstract val core: ConfigurableFileCollection

    @get:Optional
    @get:Classpath
    abstract val deviceControllerDdmlib: ConfigurableFileCollection

    @get:Optional
    @get:Classpath
    abstract val deviceProviderGradle: ConfigurableFileCollection

    @get:Optional
    @get:Classpath
    abstract val deviceProviderVirtual: ConfigurableFileCollection

    @get:Optional
    @get:Classpath
    abstract val driverInstrumentation: ConfigurableFileCollection

    @get:Optional
    @get:Classpath
    abstract val testPlugin: ConfigurableFileCollection

    @get:Optional
    @get:Classpath
    abstract val testDeviceInfoPlugin: ConfigurableFileCollection

    @get:Optional
    @get:Classpath
    abstract val additionalTestOutputPlugin: ConfigurableFileCollection

    @get:Optional
    @get:Classpath
    abstract val testCoveragePlugin: ConfigurableFileCollection

    @get:Optional
    @get:Classpath
    abstract val testLogcatPlugin: ConfigurableFileCollection

    @get:Optional
    @get:Classpath
    abstract val testPluginHostRetention: ConfigurableFileCollection

    @get:Optional
    @get:Classpath
    abstract val testPluginResultListenerGradle: ConfigurableFileCollection
}

/**
 * Looks for Nitrogen configurations in a project and creates and add to the project with default
 * values if missing.
 *
 * @param project a project for the resulted [Configuration] to be added to.
 */
fun maybeCreateUtpConfigurations(project: Project) {
    UtpDependency.values().forEach { nitrogenDependency ->
        project.configurations.findByName(nitrogenDependency.configurationName) ?:
            project.configurations.create(nitrogenDependency.configurationName).apply {
                isVisible = false
                isTransitive = true
                isCanBeConsumed = false
                description = "A configuration to resolve the Unified Test Platform dependencies."
            }
        project.dependencies.add(
                nitrogenDependency.configurationName,
                nitrogenDependency.mavenCoordinate())
    }
}

/**
 * Resolves the UTP dependencies and populates this [UtpDependencies] object from the
 * given [ConfigurationContainer].
 */
fun UtpDependencies.resolveDependencies(configurationsContainer: ConfigurationContainer) {
    UtpDependency.values().forEach { utpDependency ->
        utpDependency.mapperFunc(this)
                .from(configurationsContainer.getByName(utpDependency.configurationName))
    }
}
