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
import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.services.BuiltInKotlinServices.AvailabilityReason.BuiltInKotlinPluginApplied
import com.android.build.gradle.internal.services.BuiltInKotlinServices.AvailabilityReason.KotlinAndroidPluginAppliedAndTestFixturesOrScreenshotTestEnabled
import com.android.build.gradle.internal.utils.ANDROID_BUILT_IN_KAPT_PLUGIN_ID
import com.android.build.gradle.internal.utils.ANDROID_BUILT_IN_KOTLIN_PLUGIN_ID
import com.android.build.gradle.internal.utils.KOTLIN_ANDROID_PLUGIN_ID
import com.android.build.gradle.internal.utils.KOTLIN_KAPT_PLUGIN_ID
import com.android.build.gradle.internal.utils.KgpVersion
import com.android.build.gradle.internal.utils.KgpVersion.Companion.MINIMUM_BUILT_IN_KOTLIN_VERSION
import com.android.build.gradle.internal.utils.disallowPlugin
import com.android.build.gradle.internal.utils.getKotlinPluginVersionFromPlugin
import com.android.build.gradle.internal.utils.requirePlugin
import com.android.build.gradle.options.BooleanOption
import com.android.builder.errors.IssueReporter.Type
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBaseApiPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilationFactory

/**
 * Services related to the built-in Kotlin support, to be used when
 * [com.android.build.gradle.internal.component.ComponentCreationConfig.useBuiltInKotlinSupport] == true.
 */
class BuiltInKotlinServices(

    /** The reason why [BuiltInKotlinServices] is available. */
    val reason: AvailabilityReason,

    val kotlinBaseApiPlugin: KotlinBaseApiPlugin,
    val kotlinAndroidProjectExtension: KotlinAndroidProjectExtension,
    val baseExtension: BaseExtension? // Currently required (KT-77300)
) {

    val kgpVersion: KgpVersion = KgpVersion.parse(kotlinBaseApiPlugin.pluginVersion)

    companion object {

        fun createFromPlugin(
            reason: AvailabilityReason,
            kotlinBaseApiPlugin: KotlinBaseApiPlugin,
            kotlinAndroidProjectExtension: KotlinAndroidProjectExtension,
            baseExtension: BaseExtension?,
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
                baseExtension
            )
        }
    }

    /** The reason why [BuiltInKotlinServices] is available. */
    sealed interface AvailabilityReason {

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

/** Performs preliminary actions required for built-in Kotlin support. */
fun initBuiltInKotlinSupportIfRequired(project: Project, projectServices: ProjectServices) {
    // Handle the case when built-in Kotlin plugin is applied
    project.pluginManager.withPlugin(ANDROID_BUILT_IN_KOTLIN_PLUGIN_ID) {
        initBuiltInKotlinSupport(project)
        projectServices.initBuiltInKotlinServices(BuiltInKotlinPluginApplied)
    }

    // Built-in Kapt plugin requires built-in Kotlin plugin
    project.pluginManager.withPlugin(ANDROID_BUILT_IN_KAPT_PLUGIN_ID) {
        project.requirePlugin(ANDROID_BUILT_IN_KAPT_PLUGIN_ID, ANDROID_BUILT_IN_KOTLIN_PLUGIN_ID)
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
    project.disallowPlugin(
        mainPlugin = ANDROID_BUILT_IN_KOTLIN_PLUGIN_ID,
        incompatiblePlugin = KOTLIN_ANDROID_PLUGIN_ID
    )

    // Apply KotlinBaseApiPlugin
    val kotlinBaseApiPlugin = project.plugins.apply(KotlinBaseApiPlugin::class.java)

    // Add the `kotlin` extension
    val kotlinAndroidExtension = kotlinBaseApiPlugin.createKotlinAndroidExtension() as KotlinAndroidProjectExtension
    kotlinAndroidExtension.setDefaults(project.name, kotlinBaseApiPlugin.pluginVersion)
    project.extensions.add("kotlin", kotlinAndroidExtension)

    // Also provide built-in Kapt support
    initBuiltInKaptSupportIfRequired(project)
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
    project.disallowPlugin(
        mainPlugin = ANDROID_BUILT_IN_KAPT_PLUGIN_ID,
        incompatiblePlugin = KOTLIN_KAPT_PLUGIN_ID
    )

    // Get KotlinBaseApiPlugin
    val kotlinBaseApiPlugin = project.plugins.getPlugin(KotlinBaseApiPlugin::class.java)

    // Add the `kapt` extension
    project.extensions.add("kapt", kotlinBaseApiPlugin.kaptExtension)
}

internal fun ComponentCreationConfig.createKotlinCompilation(
    baseVariant: BaseVariant,
): KotlinCompilation<Any> {
    val kotlinServices = services.builtInKotlinServices

    // TODO(b/409528883): Use KGP API to create a KotlinCompilation instance once it is
    // available (KT-77023).
    // For now, we need to make use of KotlinJvmAndroidCompilationFactory, and because its
    // constructor is `internal`, we need to use reflection.
    val constructor = KotlinJvmAndroidCompilationFactory::class.java.getConstructor(KotlinAndroidTarget::class.java, BaseVariant::class.java)
    constructor.isAccessible = true
    val kotlinCompilationFactory = constructor.newInstance(kotlinServices.kotlinAndroidProjectExtension.target, baseVariant)
    val kotlinCompilation = kotlinCompilationFactory.create(name)

    // Set Kotlin source directories. Note that we're setting instead of adding the directories
    // because we want to overwrite any directories that were previously set and make it consistent
    // with the Kotlin source directories managed by AGP. For example, for compilation
    // `debugUnitTest`, KGP automatically adds a source directory named `src/debugUnitTest/kotlin`,
    // but this directory is not intended by AGP. The directories should be `src/test/kotlin`,
    // `src/test/java`, `src/testDebug/kotlin`, `src/testDebug/java`.
    kotlinCompilation.defaultSourceSet.kotlin.setSrcDirs(listOf(sources.kotlin!!.directories))

    // Also add kotlinCompilation to KotlinAndroidTarget.compilations
    @Suppress("UNCHECKED_CAST")
    (kotlinServices.kotlinAndroidProjectExtension.target.compilations as NamedDomainObjectContainer<KotlinJvmAndroidCompilation>)
        .add(kotlinCompilation)

    return kotlinCompilation
}

/**
 * Returns the corresponding old [BaseVariant] for this component, or null if such an instance
 * doesn't exist (for screenshot-test and test-fixtures components).
 */
internal fun ComponentCreationConfig.toBaseVariant(baseExtension: BaseExtension): BaseVariant? {
    return when (this) {
        is ComponentImpl<*> -> when (this) {
            is ApplicationVariantImpl, is DynamicFeatureVariantImpl -> (baseExtension as AppExtension).applicationVariants.single { it.name == name }
            is LibraryVariantImpl -> (baseExtension as LibraryExtension).libraryVariants.single { it.name == name }
            is TestVariantImpl -> (baseExtension as TestExtension).applicationVariants.single { it.name == name }
            is HostTestImpl -> if (hostTestName == com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE) {
                (baseExtension as TestedExtension).unitTestVariants.single { it.name == name }
            } else {
                check(hostTestName == com.android.build.api.variant.HostTestBuilder.SCREENSHOT_TEST_TYPE)
                null // Not available screenshot-test components
            }
            is DeviceTestImpl -> (baseExtension as TestedExtension).testVariants.single { it.name == name }
            is TestFixturesImpl -> null // Not available for test-fixtures components
            else -> error("Unknown component: ${this::class.java.name}")
        }
        is KmpComponentImpl<*> -> error("KmpComponentImpl is not expected here (built-in Kotlin support is not available for KMP)")
        else -> error("Unknown component: ${this::class.java.name}")
    }
}
