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

import com.android.build.api.component.impl.ComponentImpl
import com.android.build.api.component.impl.DeviceTestImpl
import com.android.build.api.component.impl.HostTestImpl
import com.android.build.api.component.impl.KmpComponentImpl
import com.android.build.api.component.impl.TestFixturesImpl
import com.android.build.api.variant.impl.ApplicationVariantImpl
import com.android.build.api.variant.impl.DynamicFeatureVariantImpl
import com.android.build.api.variant.impl.LibraryVariantImpl
import com.android.build.api.variant.impl.TestVariantImpl
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.services.BuiltInKotlinServices.AvailabilityReason.BuiltInKotlinBooleanOptionEnabled
import com.android.build.gradle.internal.services.BuiltInKotlinServices.AvailabilityReason.BuiltInKotlinPluginApplied
import com.android.build.gradle.internal.services.BuiltInKotlinServices.AvailabilityReason.KotlinAndroidPluginAppliedAndTestFixturesOrScreenshotTestEnabled
import com.android.build.gradle.internal.utils.ANDROID_BUILT_IN_KAPT_PLUGIN_ID
import com.android.build.gradle.internal.utils.ANDROID_BUILT_IN_KOTLIN_PLUGIN_ID
import com.android.build.gradle.internal.utils.ANDROID_KOTLIN_MPP_LIBRARY_PLUGIN_ID
import com.android.build.gradle.internal.utils.KOTLIN_ANDROID_PLUGIN_ID
import com.android.build.gradle.internal.utils.KOTLIN_KAPT_PLUGIN_ID
import com.android.build.gradle.internal.utils.KOTLIN_MPP_PLUGIN_ID
import com.android.build.gradle.internal.utils.KgpVersion
import com.android.build.gradle.internal.utils.KgpVersion.Companion.MINIMUM_BUILT_IN_KOTLIN_VERSION
import com.android.build.gradle.internal.utils.getKotlinPluginVersionFromPlugin
import com.android.build.gradle.internal.utils.requirePlugin
import com.android.build.gradle.options.BooleanOption
import com.android.builder.errors.IssueReporter.Type
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBaseApiPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import org.jetbrains.kotlin.gradle.plugin.sources.android.AndroidVariantType

/**
 * Services related to the built-in Kotlin support, to be used when
 * [com.android.build.gradle.internal.component.ComponentCreationConfig.useBuiltInKotlinSupport] == true.
 */
class BuiltInKotlinServices(

    /** The reason why [BuiltInKotlinServices] is available. */
    val reason: AvailabilityReason,

    val kotlinBaseApiPlugin: KotlinBaseApiPlugin,
    val kotlinAndroidProjectExtension: KotlinAndroidProjectExtension,
) {

    val kgpVersion: KgpVersion = KgpVersion.parse(kotlinBaseApiPlugin.pluginVersion)

    companion object {

        fun createFromPlugin(
            reason: AvailabilityReason,
            kotlinBaseApiPlugin: KotlinBaseApiPlugin,
            kotlinAndroidProjectExtension: KotlinAndroidProjectExtension,
        ): BuiltInKotlinServices {
            getKotlinPluginVersionFromPlugin(kotlinBaseApiPlugin)?.let {
                if (KgpVersion.parse(it) < MINIMUM_BUILT_IN_KOTLIN_VERSION) {
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

            return BuiltInKotlinServices(
                reason,
                kotlinBaseApiPlugin,
                kotlinAndroidProjectExtension,
            )
        }
    }

    /** The reason why [BuiltInKotlinServices] is available. */
    sealed interface AvailabilityReason {

        /**
         * [BuiltInKotlinServices] is available because [BooleanOption.BUILT_IN_KOTLIN] is enabled.
         */
        object BuiltInKotlinBooleanOptionEnabled: AvailabilityReason

        /** [BuiltInKotlinServices] is available because the built-in Kotlin plugin is applied. */
        object BuiltInKotlinPluginApplied: AvailabilityReason

        /**
         * [BuiltInKotlinServices] is available because the `kotlin-android` plugin is applied
         * and either [BooleanOption.ENABLE_TEST_FIXTURES_KOTLIN_SUPPORT] or
         * [BooleanOption.ENABLE_SCREENSHOT_TEST] is enabled .
         */
        object KotlinAndroidPluginAppliedAndTestFixturesOrScreenshotTestEnabled: AvailabilityReason

    }
}

/** Indicates whether built-in Kotlin support is available and why. */
sealed class BuiltInKotlinSupportMode {

    sealed class Supported : BuiltInKotlinSupportMode() {

        /**
         * Built-in Kotlin support is available because [BooleanOption.BUILT_IN_KOTLIN] is enabled.
         */
        object BuiltInKotlinBooleanOptionEnabled : Supported()

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

    object NotSupported : BuiltInKotlinSupportMode()
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

    object NotSupported : BuiltInKaptSupportMode()
}

/** Performs preliminary actions required for built-in Kotlin support. */
fun initBuiltInKotlinSupportIfRequired(project: Project, projectServices: ProjectServices) {
    // Provide built-in Kotlin support when the BooleanOption is enabled or the built-in Kotlin
    // plugin is applied
    if (projectServices.projectOptions.get(BooleanOption.BUILT_IN_KOTLIN)) {
        initBuiltInKotlinSupport(project)
        projectServices.initBuiltInKotlinServices(BuiltInKotlinBooleanOptionEnabled)
    } else {
        project.pluginManager.withPlugin(ANDROID_BUILT_IN_KOTLIN_PLUGIN_ID) {
            initBuiltInKotlinSupport(project)
            projectServices.initBuiltInKotlinServices(BuiltInKotlinPluginApplied)
        }
    }

    // Built-in Kapt plugin requires built-in Kotlin plugin
    project.pluginManager.withPlugin(ANDROID_BUILT_IN_KAPT_PLUGIN_ID) {
        if (!projectServices.projectOptions.get(BooleanOption.BUILT_IN_KOTLIN)) {
            project.requirePlugin(ANDROID_BUILT_IN_KAPT_PLUGIN_ID, ANDROID_BUILT_IN_KOTLIN_PLUGIN_ID)
        }
    }

    // Handle the case when test-fixtures/screenshot-test feature is enabled
    if (projectServices.projectOptions.get(BooleanOption.ENABLE_TEST_FIXTURES_KOTLIN_SUPPORT)
        || projectServices.projectOptions.get(BooleanOption.ENABLE_SCREENSHOT_TEST)
    ) {
        // If `kotlin-android` plugin is applied, we will provide built-in Kotlin support
        // TODO: Once KGP is always available on the build script classpath (b/431147146),
        //  we can provide built-in Kotlin support even if `kotlin-android` plugin is not applied.
        project.pluginManager.withPlugin(KOTLIN_ANDROID_PLUGIN_ID) {
            project.plugins.apply(KotlinBaseApiPlugin::class.java)
            projectServices.initBuiltInKotlinServices(KotlinAndroidPluginAppliedAndTestFixturesOrScreenshotTestEnabled)
        }

        // TODO: Remove this check once KGP is always available on the build script classpath
        //  (b/431147146),
        try {
            Class.forName(KotlinBaseApiPlugin::class.java.name)
        } catch (e: Throwable) {
            if (e is ClassNotFoundException || e is NoClassDefFoundError) {
                val message =
                    """
                    The Kotlin Gradle plugin was not found on the project's buildscript
                    classpath. Add "org.jetbrains.kotlin:kotlin-gradle-plugin:$MINIMUM_BUILT_IN_KOTLIN_VERSION" to the
                    buildscript classpath in order to use any of the following Gradle
                    properties:

                    ${BooleanOption.ENABLE_SCREENSHOT_TEST.propertyName},
                    ${BooleanOption.ENABLE_TEST_FIXTURES_KOTLIN_SUPPORT.propertyName}

                    """.trimIndent()
                projectServices.issueReporter.reportError(Type.GENERIC, message)
            } else {
                throw e
            }
        }
    }
}

private fun initBuiltInKotlinSupport(project: Project) {
    failIfIncompatiblePluginsArePresent(project)

    // Apply KotlinBaseApiPlugin
    val kotlinBaseApiPlugin = project.plugins.apply(KotlinBaseApiPlugin::class.java)

    // Add the `kotlin` extension
    val kotlinAndroidExtension = kotlinBaseApiPlugin.createKotlinAndroidExtension() as KotlinAndroidProjectExtension
    kotlinAndroidExtension.setDefaults(project.name, kotlinBaseApiPlugin.pluginVersion)
    project.extensions.add("kotlin", kotlinAndroidExtension)

    // Also provide built-in Kapt support
    initBuiltInKaptSupportIfRequired(project)
}

private fun failIfIncompatiblePluginsArePresent(project: Project) {
    project.pluginManager.withPlugin(KOTLIN_ANDROID_PLUGIN_ID) {
        error(
            """
            The '$KOTLIN_ANDROID_PLUGIN_ID' plugin is no longer required for Kotlin support since AGP 9.0.
            Solution:
              - [Recommended] Migrate this project to built-in Kotlin (https://developer.android.com/r/tools/built-in-kotlin).
              - Or set the Gradle property '${BooleanOption.BUILT_IN_KOTLIN.propertyName}=false' to temporarily bypass this issue.
            """.trimIndent()
        )
    }

    project.pluginManager.withPlugin(KOTLIN_KAPT_PLUGIN_ID) {
        error(
            """
            The '$KOTLIN_KAPT_PLUGIN_ID' plugin is not compatible with built-in Kotlin support.
            Solution:
              - [Recommended] Migrate this project to built-in Kotlin (https://developer.android.com/r/tools/built-in-kotlin).
              - Or set the Gradle property '${BooleanOption.BUILT_IN_KOTLIN.propertyName}=false' to temporarily bypass this issue.
            """.trimIndent()
        )
    }

    project.pluginManager.withPlugin(KOTLIN_MPP_PLUGIN_ID) {
        error(
            """
            The 'com.android.library' (or 'com.android.application') plugin is not compatible with the '$KOTLIN_MPP_PLUGIN_ID' plugin since AGP 9.0.
            Solution:
              - [Recommended] Replace the 'com.android.library' plugin with the '$ANDROID_KOTLIN_MPP_LIBRARY_PLUGIN_ID' plugin (see https://developer.android.com/r/tools/built-in-kotlin).
              - Or set the Gradle property '${BooleanOption.BUILT_IN_KOTLIN.propertyName}=false' to temporarily bypass this issue.
            """.trimIndent()
        )
    }
}

private fun KotlinAndroidProjectExtension.setDefaults(
    projectName: String,
    kotlinBaseApiPluginVersion: String
) {
    // Kotlin Gradle plugin requires `coreLibrariesVersion` to be set
    coreLibrariesVersion = kotlinBaseApiPluginVersion

    // KotlinCompile task requires `moduleName` to be set
    compilerOptions.moduleName.convention(projectName)
}

/** Performs preliminary actions required for built-in Kapt support. */
fun initBuiltInKaptSupportIfRequired(project: Project) {
    project.pluginManager.withPlugin(ANDROID_BUILT_IN_KAPT_PLUGIN_ID) {
        initBuiltInKaptSupport(project)
    }
}

private fun initBuiltInKaptSupport(project: Project) {
    // Get KotlinBaseApiPlugin
    val kotlinBaseApiPlugin = project.plugins.getPlugin(KotlinBaseApiPlugin::class.java)

    // Add the `kapt` extension
    project.extensions.add("kapt", kotlinBaseApiPlugin.kaptExtension)
}

@OptIn(InternalKotlinGradlePluginApi::class)
internal fun ComponentCreationConfig.createKotlinCompilation(): KotlinCompilation<Any> {
    val kotlinServices = services.builtInKotlinServices

    val kotlinCompilation: KotlinJvmAndroidCompilation =
        kotlinServices.kotlinBaseApiPlugin.createKotlinAndroidCompilation(
            name = name,
            androidTarget = kotlinServices.kotlinAndroidProjectExtension.target,
            androidVariantJavaCompileTask = taskContainer.javacTask as TaskProvider<JavaCompile>,
            androidVariantType = toAndroidVariantType()
        )

    // Set Kotlin source directories. Note that we're setting instead of adding the directories
    // because we want to overwrite any directories that were previously set and make it consistent
    // with the Kotlin source directories managed by AGP. For example, for compilation
    // `debugUnitTest`, KGP automatically adds a source directory named `src/debugUnitTest/kotlin`,
    // but this directory is not intended by AGP. The directories should be `src/test/kotlin`,
    // `src/test/java`, `src/testDebug/kotlin`, `src/testDebug/java`.
    kotlinCompilation.defaultSourceSet.kotlin.setSrcDirs(listOf(sources.kotlin!!.directories))

    // Also add kotlinCompilation to KotlinAndroidTarget.compilations (the IDE requires this info to
    // configure Kotlin).
    @Suppress("UNCHECKED_CAST")
    (kotlinServices.kotlinAndroidProjectExtension.target.compilations as NamedDomainObjectContainer<KotlinJvmAndroidCompilation>)
        .add(kotlinCompilation)

    return kotlinCompilation
}

@OptIn(InternalKotlinGradlePluginApi::class)
private fun ComponentCreationConfig.toAndroidVariantType(): AndroidVariantType {
    return when (this) {
        is ComponentImpl<*> -> when (this) {
            is ApplicationVariantImpl -> AndroidVariantType.Main
            is DynamicFeatureVariantImpl -> AndroidVariantType.Main
            is LibraryVariantImpl -> AndroidVariantType.Main
            is TestVariantImpl -> AndroidVariantType.Main
            is HostTestImpl -> if (hostTestName == com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE) {
                AndroidVariantType.UnitTest
            } else {
                AndroidVariantType.Unknown // Not available a screenshot-test component
            }
            is DeviceTestImpl -> AndroidVariantType.InstrumentedTest
            is TestFixturesImpl -> AndroidVariantType.Unknown  // Not available for a test-fixtures component
            else -> error("Unknown component: ${this::class.java.name}")
        }
        is KmpComponentImpl<*> -> error("KmpComponentImpl is not expected here (built-in Kotlin support is not available for KMP)")
        else -> error("Unknown component: ${this::class.java.name}")
    }
}
