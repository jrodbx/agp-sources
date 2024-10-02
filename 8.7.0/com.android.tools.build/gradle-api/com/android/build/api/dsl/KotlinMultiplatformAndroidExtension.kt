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

package com.android.build.api.dsl

import org.gradle.api.Incubating

/**
 * Extension properties for Kotlin multiplatform Android libraries.
 *
 * Only the Kotlin Multiplatform Android Plugin should create instances of this interface.
 *
 * Warning: this is an experimental API and will change in the near future,
 * and you shouldn't publish plugins depending on it.
 */
 @Incubating
interface KotlinMultiplatformAndroidExtension {
    /**
     * The minimum SDK version.
     * Setting this it will override previous calls of [minSdk] and [minSdkPreview] setters. Only
     * one of [minSdk] and [minSdkPreview] should be set.
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    @get:Incubating
    @set:Incubating
    var minSdk: Int?

    @get:Incubating
    @set:Incubating
    var minSdkPreview: String?

    /**
     * Specifies the API level to compile your project against. The Android plugin requires you to
     * configure this property.
     *
     * This means your code can use only the Android APIs included in that API level and lower.
     * You can configure the compile sdk version by adding the following to the `android`
     * block: `compileSdk = 26`.
     *
     * You should generally
     * [use the most up-to-date API level](https://developer.android.com/guide/topics/manifest/uses-sdk-element.html#ApiLevels)
     * available.
     * If you are planning to also support older API levels, it's good practice to
     * [use the Lint tool](https://developer.android.com/studio/write/lint.html)
     * to check if you are using APIs that are not available in earlier API levels.
     *
     * The value you assign to this property is parsed and stored in a normalized form, so
     * reading it back may return a slightly different value.
     */
    @get:Incubating
    @set:Incubating
    var compileSdk: Int?

    @get:Incubating
    @set:Incubating
    var compileSdkExtension: Int?

    @get:Incubating
    @set:Incubating
    var compileSdkPreview: String?

    /**
     * The maxSdkVersion, or null if not specified. This is only the value set on this produce
     * flavor.
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    @get:Incubating
    @set:Incubating
    var maxSdk: Int?

    /**
     * Includes the specified library to the classpath.
     *
     * You typically use this property to support optional platform libraries that ship with the
     * Android SDK. The following sample adds the Apache HTTP API library to the project classpath:
     *
     * ```
     * android {
     *     // Adds a platform library that ships with the Android SDK.
     *     useLibrary 'org.apache.http.legacy'
     * }
     * ```
     *
     * To include libraries that do not ship with the SDK, such as local library modules or
     * binaries from remote repositories,
     * [add the libraries as dependencies](https://developer.android.com/studio/build/dependencies.html)
     * in the `dependencies` block. Note that Android plugin 3.0.0 and later introduce
     * [new dependency configurations](https://developer.android.com/studio/build/gradle-plugin-3-0-0-migration.html#new_configurations).
     * To learn more about Gradle dependencies, read
     * [Dependency Management Basics](https://docs.gradle.org/current/userguide/artifact_dependencies_tutorial.html).
     *
     * @param name the name of the library.
     */
    @Incubating
    fun useLibrary(name: String)

    /**
     * Includes the specified library to the classpath.
     *
     * You typically use this property to support optional platform libraries that ship with the
     * Android SDK. The following sample adds the Apache HTTP API library to the project classpath:
     *
     * ```
     * android {
     *     // Adds a platform library that ships with the Android SDK.
     *     useLibrary 'org.apache.http.legacy'
     * }
     * ```
     *
     * To include libraries that do not ship with the SDK, such as local library modules or
     * binaries from remote repositories,
     * [add the libraries as dependencies]("https://developer.android.com/studio/build/dependencies.html)
     * in the `dependencies` block. Note that Android plugin 3.0.0 and later introduce
     * [new dependency configurations](new dependency configurations).
     * To learn more about Gradle dependencies, read
     * [Dependency Management Basics](https://docs.gradle.org/current/userguide/artifact_dependencies_tutorial.html)
     *
     * @param name the name of the library.
     * @param required if using the library requires a manifest entry, the entry will indicate that
     *     the library is not required.
     */
    @Incubating
    fun useLibrary(name: String, required: Boolean)

    /**
     * The namespace of the generated R and BuildConfig classes. Also, the namespace used to resolve
     * any relative class names that are declared in the AndroidManifest.xml.
     */
    @get:Incubating
    @set:Incubating
    var namespace: String?

    /**
     * The namespace used by the android test and unit test components for the generated R and
     * BuildConfig classes.
     */
    @get:Incubating
    @set:Incubating
    var testNamespace: String?

    /**
     * Specifies the version of the
     * [SDK Build Tools](https://developer.android.com/studio/releases/build-tools.html)
     * to use when building your project.
     *
     * By default, the plugin uses the minimum version of the build tools required by the
     * [version of the plugin](https://developer.android.com/studio/releases/gradle-plugin.html#revisions)
     * you're using.
     * To specify a different version of the build tools for the plugin to use,
     * specify the version as follows:
     *
     * ```
     * android {
     *     // Specifying this property is optional.
     *     buildToolsVersion "26.0.0"
     * }
     * ```
     *
     * For a list of build tools releases, read
     * [the release notes](https://developer.android.com/studio/releases/build-tools.html#notes).
     *
     * Note that the value assigned to this property is parsed and stored in a normalized form,
     * so reading it back may give a slightly different result.
     */
    @get:Incubating
    @set:Incubating
    var buildToolsVersion: String

    /**
     * Additional per module experimental properties.
     */
    @get:Incubating
    val experimentalProperties: MutableMap<String, Any>

    /**
     * Specifies options for doing variant selection for external Android dependencies
     * based on build types and product flavours
     *
     * For more information about the properties you can configure in this block, see [DependencyVariantSelection].
     */
    @get:Incubating
    val dependencyVariantSelection: DependencyVariantSelection

    /**
     * Specifies options for doing variant selection for external Android dependencies
     * based on build types and product flavours
     *
     * For more information about the properties you can configure in this block, see [DependencyVariantSelection].
     */
    @Incubating
    fun dependencyVariantSelection(action: DependencyVariantSelection.() -> Unit)

    /**
     * Specifies options for the lint tool.
     *
     * For more information about the properties you can configure in this block, see [Lint].
     */
    @get:Incubating
    val lint: Lint

    /**
     * Specifies options for the lint tool.
     *
     * For more information about the properties you can configure in this block, see [Lint].
     */
    @Incubating
    fun lint(action: Lint.() -> Unit)

    /**
     * Options for configuring AAR metadata.
     */
    @get:Incubating
    val aarMetadata: AarMetadata

    /**
     * Options for configuring AAR metadata.
     */
    @Incubating
    fun aarMetadata(action: AarMetadata.() -> Unit)

    /**
     * Specifies options and rules that determine which files the Android plugin packages into your
     * APK.
     *
     * For more information about the properties you can configure in this block, see [Packaging].
     */
    @get:Incubating
    val packaging: Packaging

    /**
     * Specifies options and rules that determine which files the Android plugin packages into your
     * APK.
     *
     * For more information about the properties you can configure in this block, see [Packaging].
     */
    @Incubating
    fun packaging(action: Packaging.() -> Unit)

    /**
     * Specifies options for the R8/D8 optimization tool.
     *
     * For more information about the properties you can configure in this block, see [KmpOptimization].
     */
    @get:Incubating
    val optimization: KmpOptimization

    /**
     * Specifies options for the R8/D8 optimization tool.
     *
     * For more information about the properties you can configure in this block, see [KmpOptimization].
     */
    @Incubating
    fun optimization(action: KmpOptimization.() -> Unit)

    /**
     * Creates and configures a compilation for tests that run on the JVM (previously referred to as
     * unit tests). Invoking this method will create a [KotlinMultiplatformAndroidTestOnJvmCompilation]
     * object with the following defaults (You can change these defaults by using
     * [withAndroidTestOnJvmBuilder] instead):
     *
     * * compilation name is "testOnJvm"
     * * default sourceSet name is "androidTestOnJvm" (sources would be located at `$project/src/androidTestOnJvm`)
     * * sourceSet tree is `test`, which means that the `commonTest` sourceSet would be included in
     *   the compilation.
     *
     * Only a single compilation of this test type can be created. If you want to configure [KotlinMultiplatformAndroidTestOnJvm]
     * options, you can modify it on the kotlin compilation as follows:
     * ```
     * kotlin {
     *   androidLibrary {
     *     compilations.withType(com.android.build.api.dsl.KotlinMultiplatformAndroidTestOnJvm::class.java) {
     *       // configure options
     *     }
     *   }
     * }
     * ```
     */
    @Incubating
    fun withAndroidTestOnJvm(
        action: KotlinMultiplatformAndroidTestOnJvm.() -> Unit
    )

    /**
     * Creates and configures a compilation for tests that run on the JVM (previously referred to as
     * unit tests). Invoking this method will create a [KotlinMultiplatformAndroidTestOnJvmCompilation]
     * object using the values set in the [KotlinMultiplatformAndroidCompilationBuilder].
     *
     * The returned object can be used to configure [KotlinMultiplatformAndroidTestOnJvm] as follows:
     * ```
     * kotlin {
     *   androidLibrary {
     *     withAndroidTestOnJvmBuilder {
     *       compilationName = "unitTest"
     *       defaultSourceSetName = "androidUnitTest"
     *     }.configure {
     *       isIncludeAndroidResources = true
     *     }
     *   }
     * }
     * ```
     *
     * Only a single compilation of this test type can be created. If you want to configure [KotlinMultiplatformAndroidTestOnJvm]
     * options, you can modify it on the kotlin compilation as follows:
     * ```
     * kotlin {
     *   androidLibrary {
     *     compilations.withType(com.android.build.api.dsl.KotlinMultiplatformAndroidTestOnJvm::class.java) {
     *       // configure options
     *     }
     *   }
     * }
     * ```
     */
    @Incubating
    fun withAndroidTestOnJvmBuilder(
        action: KotlinMultiplatformAndroidCompilationBuilder.() -> Unit
    ): HasConfigurableValue<KotlinMultiplatformAndroidTestOnJvm>

    /**
     * Creates and configures a compilation for tests that run on the device (previously referred to as
     * instrumented tests). Invoking this method will create a [KotlinMultiplatformAndroidTestOnDeviceCompilation]
     * object with the following defaults:
     *
     * * compilation name is "testOnDevice"
     * * default sourceSet name is "androidTestOnDevice" (sources would be located at `$project/src/androidTestOnDevice`)
     * * sourceSet tree is `null`, which means that the `commonTest` sourceSet will **not** be included in
     *   the compilation.
     *
     * Only a single compilation of this test type can be created. If you want to configure [KotlinMultiplatformAndroidTestOnDevice]
     * options, you can modify it on the kotlin compilation as follows:
     * ```
     * kotlin {
     *   androidLibrary {
     *     compilations.withType(com.android.build.api.dsl.KotlinMultiplatformAndroidTestOnDevice::class.java) {
     *       // configure options
     *     }
     *   }
     * }
     * ```
     */
    @Incubating
    fun withAndroidTestOnDevice(
        action: KotlinMultiplatformAndroidTestOnDevice.() -> Unit
    )

    /**
     * Creates and configures a compilation for tests that run on the device (previously referred to as
     * instrumented tests). Invoking this method will create a [KotlinMultiplatformAndroidTestOnDeviceCompilation]
     * object using the values set in the [KotlinMultiplatformAndroidCompilationBuilder].
     *
     * The returned object can be used to configure [KotlinMultiplatformAndroidTestOnDevice] as follows:
     * ```
     * kotlin {
     *   androidLibrary {
     *     withAndroidTestOnDeviceBuilder {
     *       compilationName = "instrumentedTest"
     *       defaultSourceSetName = "androidInstrumentedTest"
     *     }.configure {
     *       instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
     *     }
     *   }
     * }
     * ```
     *
     * Only a single compilation of this test type can be created. If you want to configure [KotlinMultiplatformAndroidTestOnDevice]
     * options, you can modify it on the kotlin compilation as follows:
     * ```
     * kotlin {
     *   androidLibrary {
     *     compilations.withType(com.android.build.api.dsl.KotlinMultiplatformAndroidTestOnDevice::class.java) {
     *       // configure options
     *     }
     *   }
     * }
     * ```
     */
    @Incubating
    fun withAndroidTestOnDeviceBuilder(
        action: KotlinMultiplatformAndroidCompilationBuilder.() -> Unit
    ): HasConfigurableValue<KotlinMultiplatformAndroidTestOnDevice>

    /**
     * Whether core library desugaring is enabled.
     */
    @get:Incubating
    @set:Incubating
    var isCoreLibraryDesugaringEnabled: Boolean

    /**
     * Configure the gathering of code-coverage from tests.
     *
     */
    @get:Incubating
    val testCoverage: TestCoverage
}
