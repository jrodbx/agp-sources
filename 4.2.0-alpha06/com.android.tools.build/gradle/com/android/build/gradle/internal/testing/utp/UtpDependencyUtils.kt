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

import org.gradle.api.NonExtensible
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional

private const val NITROGEN_MAVEN_GROUP_ID = "com.google.testing.platform"
private const val NITROGEN_DEFAULT_VERSION = "0.0.1-dev"

/**
 * Available Unified Test Platform dependencies.
 */
enum class UtpDependency(
        val artifactId: String,
        val mainClass: String,
        val configurationName: String = "_internal-unified-test-platform-${artifactId}",
        private val groupId: String = NITROGEN_MAVEN_GROUP_ID,
        private val version: String = NITROGEN_DEFAULT_VERSION) {
    LAUNCHER(
            "launcher",
            "com.google.testing.platform.launcher.Launcher"),
    CORE(
            "core",
            "com.google.testing.platform.main.MainKt"),
    ANDROID_DEVICE_PROVIDER_LOCAL(
            "android-device-provider-local",
            "com.google.testing.platform.runtime.android.provider.local.LocalAndroidDeviceProvider"),
    ANDROID_DEVICE_CONTROLLER_ADB(
            "android-device-controller-adb",
            "com.google.testing.platform.runtime.android.adb.controller.AdbDeviceController"),
    ANDROID_DRIVER_INSTRUMENTATION(
            "android-driver-instrumentation",
            "com.google.testing.platform.runtime.android.driver.AndroidInstrumentationDriver"),
    ANDROID_TEST_PLUGIN(
        "android-test-plugin",
        "com.google.testing.platform.plugin.android.AndroidDevicePlugin"),
    ANDROID_TEST_DEVICE_INFO_PLUGIN(
        "android-test-plugin-host-device-info",
        "com.google.testing.platform.plugin.android.info.host.AndroidTestDeviceInfoPlugin"),
    ANDROID_TEST_PLUGIN_HOST_RETENTION(
            "android-test-plugin-host-retention",
            "com.google.testing.platform.plugin.android.icebox.host.IceboxPlugin"),
    ;

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
    abstract val deviceProviderLocal: ConfigurableFileCollection
    @get:Optional
    @get:Classpath
    abstract val deviceControllerAdb: ConfigurableFileCollection
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
    abstract val testPluginHostRetention: ConfigurableFileCollection
}

/**
 * Looks for Nitrogen configurations in a project and creates and add to the project with default
 * values if missing.
 *
 * @param project a project for the resulted [Configuration] to be added to.
 */
fun maybeCreateUtpConfigurations(project: Project) {
    UtpDependency.values().forEach { nitrogenDependency ->
        project.configurations.maybeCreate(nitrogenDependency.configurationName).apply {
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
