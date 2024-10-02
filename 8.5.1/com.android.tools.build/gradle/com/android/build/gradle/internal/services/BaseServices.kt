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

package com.android.build.gradle.internal.services

import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.utils.GradleEnvironmentProvider
import com.android.build.gradle.internal.utils.MINIMUM_INTEGRATED_KOTLIN_VERSION
import com.android.build.gradle.internal.utils.getKotlinPluginVersionFromPlugin
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.errors.IssueReporter
import com.android.ide.common.gradle.Version
import org.gradle.api.services.BuildServiceRegistry
import org.jetbrains.kotlin.gradle.plugin.KotlinBaseApiPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinJvmFactory
import java.io.File

/**
 * Interface providing services useful everywhere.
 */
interface BaseServices {

    val issueReporter: IssueReporter
    val deprecationReporter: DeprecationReporter
    val projectOptions: ProjectOptions
    val buildServiceRegistry: BuildServiceRegistry
    val gradleEnvironmentProvider: GradleEnvironmentProvider
    val projectInfo: ProjectInfo
    val kotlinServices: KotlinServices?

    fun <T> newInstance(type: Class<T>, vararg args: Any?): T

    fun file(file: Any): File
}

interface KotlinServices {

    val kgpVersion: String
    val factory: KotlinJvmFactory

    companion object {

        fun createFromPlugin(plugin: KotlinBaseApiPlugin?): KotlinServices? {
            plugin ?: return null
            getKotlinPluginVersionFromPlugin(plugin)?.let {
                if (Version.parse(it) < Version.parse(MINIMUM_INTEGRATED_KOTLIN_VERSION)) {
                    val message =
                        """
                            The current Kotlin Gradle plugin version ($it) is below the required
                            minimum version ($MINIMUM_INTEGRATED_KOTLIN_VERSION).

                            The following Gradle properties require the Kotlin Gradle plugin version
                            to be at least $MINIMUM_INTEGRATED_KOTLIN_VERSION:

                            ${BooleanOption.ENABLE_SCREENSHOT_TEST.propertyName},
                            ${BooleanOption.ENABLE_TEST_FIXTURES_KOTLIN_SUPPORT.propertyName}

                        """.trimIndent()
                    throw RuntimeException(message)
                }
            }

            return object : KotlinServices {
                override val kgpVersion: String = plugin.pluginVersion
                override val factory: KotlinJvmFactory = plugin
            }
        }
    }
}
