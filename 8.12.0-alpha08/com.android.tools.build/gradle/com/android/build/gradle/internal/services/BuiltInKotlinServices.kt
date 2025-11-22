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
import com.android.build.gradle.internal.BuiltInKotlinJvmAndroidCompilation
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.utils.ANDROID_BUILT_IN_KAPT_PLUGIN_ID
import com.android.build.gradle.internal.utils.ANDROID_BUILT_IN_KOTLIN_PLUGIN_ID
import com.android.build.gradle.internal.utils.KOTLIN_ANDROID_PLUGIN_ID
import com.android.build.gradle.internal.utils.KOTLIN_KAPT_PLUGIN_ID
import com.android.build.gradle.internal.utils.MINIMUM_BUILT_IN_KOTLIN_VERSION
import com.android.build.gradle.internal.utils.disallowPlugin
import com.android.build.gradle.internal.utils.getKotlinPluginVersionFromPlugin
import com.android.build.gradle.internal.utils.requirePlugin
import com.android.build.gradle.options.BooleanOption
import com.android.ide.common.gradle.Version
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinBaseApiPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinJvmFactory
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilationFactory
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/**
 * Services related to the built-in Kotlin support, to be used when
 * [com.android.build.gradle.internal.component.ComponentCreationConfig.useBuiltInKotlinSupport] == true.
 */
interface BuiltInKotlinServices {

    val kgpVersion: String
    val factory: KotlinJvmFactory
    val kotlinBaseApiVersion: KotlinBaseApiVersion

    val kotlinAndroidProjectExtension: KotlinAndroidProjectExtension
    val baseExtension: BaseExtension? // Currently required (KT-77300)

    companion object {

        fun createFromPlugin(
            kotlinBaseApiPlugin: KotlinBaseApiPlugin,
            kotlinAndroidProjectExtension: KotlinAndroidProjectExtension,
            baseExtension: BaseExtension?,
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
                override val baseExtension: BaseExtension? = baseExtension
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

/** Performs preliminary actions required for built-in Kotlin support. */
fun initBuiltInKotlinSupportIfRequired(project: Project) {
    project.pluginManager.withPlugin(ANDROID_BUILT_IN_KOTLIN_PLUGIN_ID) {
        initBuiltInKotlinSupport(project)
    }
    project.pluginManager.withPlugin(ANDROID_BUILT_IN_KAPT_PLUGIN_ID) {
        project.requirePlugin(ANDROID_BUILT_IN_KAPT_PLUGIN_ID, ANDROID_BUILT_IN_KOTLIN_PLUGIN_ID)
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
    project: Project,
    kotlinCompileTaskProvider: TaskProvider<out KotlinJvmCompile>
): KotlinCompilation<KotlinJvmOptions> {
    val kotlinServices = services.builtInKotlinServices
    // Creating a KotlinCompilation instance currently requires access to the old BaseVariant (KT-77300)
    val variant = kotlinServices.baseExtension?.let { toBaseVariant(it) }
    return if (variant != null) {
        // TODO(b/409528883): Use KGP API to create a KotlinCompilation instance once it is
        // available (KT-77023).
        // For now, we need to make use of KotlinJvmAndroidCompilationFactory, and because its
        // constructor is `internal`, we need to use reflection.
        val constructor = KotlinJvmAndroidCompilationFactory::class.java.getConstructor(KotlinAndroidTarget::class.java, BaseVariant::class.java)
        constructor.isAccessible = true
        val kotlinCompilationFactory = constructor.newInstance(kotlinServices.kotlinAndroidProjectExtension.target, variant)
        kotlinCompilationFactory.create(name).also {
            // Also add it to KotlinAndroidTarget.compilations
            @Suppress("UNCHECKED_CAST")
            (kotlinServices.kotlinAndroidProjectExtension.target.compilations as NamedDomainObjectContainer<KotlinJvmAndroidCompilation>)
                .add(it)
        }
    } else {
        // For a screenshot-test or test-fixtures component, there isn't a corresponding old
        // BaseVariant, so we need to create a custom KotlinCompilation instance.
        BuiltInKotlinJvmAndroidCompilation(
            project = project,
            compilationName = name,
            compileTaskProvider = kotlinCompileTaskProvider,
            kotlinServices = kotlinServices,
            kotlinSourceDirectories = sources.kotlin!!.directories,
        )
    }
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
