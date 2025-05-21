/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.factory

import com.android.utils.appendCapitalized
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin

sealed interface GlobalTaskNames {
    val compileLintChecks: String
    val mainPreBuild: String
    val uninstallAll: String
    val connectedCheck: String
    val allDevicesCheck: String
    val deviceCheck: String
    val createMockableJar: String
    val test: String
}

private const val COMPILE_LINT_CHECKS_TASK = "compileLintChecks"
private const val MAIN_PREBUILD = "preBuild"
private const val UNINSTALL_ALL = "uninstallAll"
private const val DEVICE_CHECK = "deviceCheck"
private const val CONNECTED_CHECK = "connectedCheck"
private const val ALL_DEVICES_CHECK = "allDevicesCheck"
private const val CREATE_MOCKABLE_JAR = "createMockableJar"
private const val TEST = JavaPlugin.TEST_TASK_NAME

internal object GlobalTaskNamesImpl: GlobalTaskNames {
    override val compileLintChecks = COMPILE_LINT_CHECKS_TASK
    override val mainPreBuild = MAIN_PREBUILD
    override val uninstallAll = UNINSTALL_ALL
    override val connectedCheck = CONNECTED_CHECK
    override val allDevicesCheck = ALL_DEVICES_CHECK
    override val deviceCheck = DEVICE_CHECK
    override val createMockableJar = CREATE_MOCKABLE_JAR
    override val test = TEST
}

// For kmp android, there are no "global" tasks that are only applicable for android. So we prefix
// all global task names with `android`.
internal object KmpAndroidGlobalTaskNamesImpl: GlobalTaskNames {
    private const val androidPrefix = "android"

    override val compileLintChecks = androidPrefix.appendCapitalized(COMPILE_LINT_CHECKS_TASK)
    override val mainPreBuild = androidPrefix.appendCapitalized(MAIN_PREBUILD)
    override val uninstallAll = androidPrefix.appendCapitalized(UNINSTALL_ALL)
    override val connectedCheck = androidPrefix.appendCapitalized(CONNECTED_CHECK)
    override val allDevicesCheck = androidPrefix.appendCapitalized(ALL_DEVICES_CHECK)
    override val deviceCheck = androidPrefix.appendCapitalized(DEVICE_CHECK)
    override val createMockableJar = androidPrefix.appendCapitalized(CREATE_MOCKABLE_JAR)
    override val test = TEST.appendCapitalized(androidPrefix) // testAndroid
}
