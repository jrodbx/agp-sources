/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.build.gradle.internal.utils.MINIMUM_BUILT_IN_KOTLIN_VERSION
import com.android.build.gradle.internal.utils.getKotlinPluginVersionFromPlugin
import com.android.build.gradle.options.BooleanOption
import com.android.ide.common.gradle.Version
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBaseApiPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinJvmFactory

/**
 * Services related to the built-in Kotlin support, to be used when
 * [com.android.build.gradle.internal.component.ComponentCreationConfig.useBuiltInKotlinSupport] == true.
 */
interface BuiltInKotlinServices {

    val kgpVersion: String
    val factory: KotlinJvmFactory
    val kotlinBaseApiVersion: KotlinBaseApiVersion

    val kotlinAndroidProjectExtension: KotlinAndroidProjectExtension

    companion object {

        fun createFromPlugin(
            kotlinBaseApiPlugin: KotlinBaseApiPlugin,
            kotlinAndroidProjectExtension: KotlinAndroidProjectExtension,
            projectName: String
        ): BuiltInKotlinServices {
            getKotlinPluginVersionFromPlugin(kotlinBaseApiPlugin)?.let {
                if (Version.parse(it) < Version.parse(MINIMUM_BUILT_IN_KOTLIN_VERSION)) {
                    val message =
                        """
                        The current Kotlin Gradle plugin version ($it) is below the required
                        minimum version ($MINIMUM_BUILT_IN_KOTLIN_VERSION).

                        The following Gradle properties require the Kotlin Gradle plugin version
                        to be at least $MINIMUM_BUILT_IN_KOTLIN_VERSION:

                        ${BooleanOption.ENABLE_SCREENSHOT_TEST.propertyName},
                        ${BooleanOption.ENABLE_TEST_FIXTURES_KOTLIN_SUPPORT.propertyName}
                        """.trimIndent()
                    throw RuntimeException(message)
                }
            }

            return object : BuiltInKotlinServices {
                override val kgpVersion: String = kotlinBaseApiPlugin.pluginVersion
                override val factory: KotlinJvmFactory = kotlinBaseApiPlugin
                override val kotlinBaseApiVersion = kgpVersion.kotlinBaseApiVersion()
                override val kotlinAndroidProjectExtension: KotlinAndroidProjectExtension = kotlinAndroidProjectExtension
            }
        }
    }
}

/**
 *  AGP's internal versioning of [KotlinBaseApiPlugin] to track availability of APIs.
 */
enum class KotlinBaseApiVersion {
    /** Represents versions < 2.1.0-Beta2  */
    VERSION_1,

    /** Represents versions >= 2.1.0-Beta2  */
    VERSION_2;
}

/**
 * Calculate the [KotlinBaseApiVersion] for the given KGP version
 */
private fun String.kotlinBaseApiVersion(): KotlinBaseApiVersion =
    when {
        Version.parse(this) >= Version.parse("2.1.0-Beta2") -> {
            KotlinBaseApiVersion.VERSION_2
        }
        else -> KotlinBaseApiVersion.VERSION_1
    }

/** Indicates whether built-in Kotlin support is available and why. */
sealed class BuiltInKotlinSupportMode {

    sealed class Supported : BuiltInKotlinSupportMode() {

        /** Built-in Kotlin support is available because the built-in Kotlin plugin is applied. */
        object BuiltInKotlinPluginApplied : Supported()

        /**
         * Built-in Kotlin support is available because this is a screenshot test component and
         * the `kotlin-android` plugin is applied.
         */
        object ScreenshotTestAndKgpApplied : Supported()

        /**
         * Built-in Kotlin support is available because this is a test-fixtures component and
         * [BooleanOption.ENABLE_TEST_FIXTURES_KOTLIN_SUPPORT] is enabled and the `kotlin-android`
         * plugin is applied.
         */
        object TestFixturesSupportEnabledAndKgpApplied : Supported()
    }

    sealed class NotSupported : BuiltInKotlinSupportMode() {

        /**
         * Built-in Kotlin support is not available because the built-in Kotlin plugin is not
         * applied.
         */
        object BuiltInKotlinPluginNotApplied : NotSupported()

        /** Built-in Kotlin support is not available because the KMP plugin is applied. */
        object KmpPluginApplied : NotSupported()
    }
}

/** Indicates whether built-in Kapt support is available and why. */
sealed class BuiltInKaptSupportMode {

    sealed class Supported : BuiltInKaptSupportMode() {

        /** Built-in Kapt support is available because the built-in Kapt plugin is applied. */
        object BuiltInKaptPluginApplied : Supported()

        /**
         * Built-in Kapt support is available because this is a screenshot test component and the
         * `kotlin-kapt` plugin is applied.
         */
        object ScreenshotTestAndKaptApplied : Supported()

        /**
         * Built-in Kapt support is available because this is a test-fixtures component and
         * [BooleanOption.ENABLE_TEST_FIXTURES_KOTLIN_SUPPORT] is enabled and the `kotlin-kapt`
         * plugin is applied.
         */
        object TestFixturesSupportEnabledAndKaptApplied : Supported()
    }

    sealed class NotSupported : BuiltInKaptSupportMode() {

        /**
         * Built-in Kapt support is not available because the built-in Kapt plugin is not applied.
         */
        object BuiltInKaptPluginNotApplied : NotSupported()

        /** Built-in Kapt support is not available because the KMP plugin is applied. */
        object KmpPluginApplied : NotSupported()
    }
}
