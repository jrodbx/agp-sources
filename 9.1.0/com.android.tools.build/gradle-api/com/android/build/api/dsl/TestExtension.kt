/*
 * Copyright (C) 2019 The Android Open Source Project
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
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.declarative.dsl.model.annotations.Configuring

/**
 * Extension for the Android Test Gradle Plugin.
 *
 * This is the `android` block when the `com.android.test` plugin is applied.
 *
 * Only the Android Gradle Plugin should create instances of interfaces in com.android.build.api.dsl.
 */
interface TestExtension : CommonExtension {

  /**
   * Specifies options for the Android Asset Packaging Tool (AAPT).
   *
   * For more information about the properties you can configure in this block, see [AaptOptions].
   */
  @Suppress("DEPRECATION") @Deprecated("Replaced by ", replaceWith = ReplaceWith("androidResources")) override val aaptOptions: AaptOptions

  /**
   * Specifies options for the Android Asset Packaging Tool (AAPT).
   *
   * For more information about the properties you can configure in this block, see [AaptOptions].
   */
  @Suppress("DEPRECATION")
  @Deprecated("Replaced by ", replaceWith = ReplaceWith("androidResources"))
  fun aaptOptions(action: AaptOptions.() -> Unit)

  /**
   * Specifies options for the [Android Debug Bridge (ADB)](https://developer.android.com/studio/command-line/adb.html), such as APK
   * installation options.
   *
   * For more information about the properties you can configure in this block, see [AdbOptions].
   */
  @Suppress("DEPRECATION")
  @Deprecated("Replaced by installation", replaceWith = ReplaceWith("installation"))
  override val adbOptions: AdbOptions

  /**
   * Specifies options for the [Android Debug Bridge (ADB)](https://developer.android.com/studio/command-line/adb.html), such as APK
   * installation options.
   *
   * For more information about the properties you can configure in this block, see [AdbOptions].
   */
  @Suppress("DEPRECATION")
  @Deprecated("Replaced by installation", replaceWith = ReplaceWith("installation"))
  fun adbOptions(action: AdbOptions.() -> Unit)

  /**
   * Specifies options related to the processing of Android Resources.
   *
   * For more information about the properties you can configure in this block, see [TestAndroidResources].
   */
  override val androidResources: TestAndroidResources

  /**
   * Specifies options related to the processing of Android Resources.
   *
   * For more information about the properties you can configure in this block, see [TestAndroidResources].
   */
  fun androidResources(action: TestAndroidResources.() -> Unit)

  /** A list of build features that can be enabled or disabled on the Android Project. */
  override val buildFeatures: TestBuildFeatures

  /** A list of build features that can be enabled or disabled on the Android Project. */
  fun buildFeatures(action: TestBuildFeatures.() -> Unit)

  /**
   * Encapsulates all build type configurations for this project.
   *
   * Unlike using [TestProductFlavor] to create different versions of your project that you expect to co-exist on a single device, build
   * types determine how Gradle builds and packages each version of your project. Developers typically use them to configure projects for
   * various stages of a development lifecycle. For example, when creating a new project from Android Studio, the Android plugin configures
   * a 'debug' and 'release' build type for you. You can then combine build types with product flavors to
   * [create build variants](https://developer.android.com/studio/build/build-variants.html).
   *
   * @see BuildType
   */
  override val buildTypes: NamedDomainObjectContainer<out TestBuildType>

  /**
   * Encapsulates all build type configurations for this project.
   *
   * For more information about the properties you can configure in this block, see [TestBuildType]
   */
  fun buildTypes(action: NamedDomainObjectContainer<TestBuildType>.() -> Unit)

  /**
   * Shortcut extension method to allow easy access to the predefined `debug` [TestBuildType]
   *
   * For example:
   * ```
   *  android {
   *      buildTypes {
   *          debug {
   *              // ...
   *          }
   *      }
   * }
   * ```
   */
  fun NamedDomainObjectContainer<TestBuildType>.debug(action: TestBuildType.() -> Unit)

  /**
   * Shortcut extension method to allow easy access to the predefined `release` [TestBuildType]
   *
   * For example:
   * ```
   *  android {
   *      buildTypes {
   *          release {
   *              // ...
   *          }
   *      }
   * }
   * ```
   */
  fun NamedDomainObjectContainer<TestBuildType>.release(action: TestBuildType.() -> Unit)

  /**
   * Specifies Java compiler options, such as the language level of the Java source code and generated bytecode.
   *
   * For more information about the properties you can configure in this block, see [CompileOptions].
   */
  override val compileOptions: CompileOptions

  /**
   * Specifies Java compiler options, such as the language level of the Java source code and generated bytecode.
   *
   * For more information about the properties you can configure in this block, see [CompileOptions].
   */
  @Configuring fun compileOptions(action: CompileOptions.() -> Unit)

  override val composeOptions: ComposeOptions

  fun composeOptions(action: ComposeOptions.() -> Unit)

  /**
   * Specifies options for the [Data Binding Library](https://developer.android.com/topic/libraries/data-binding/index.html).
   *
   * For more information about the properties you can configure in this block, see [DataBinding]
   */
  override val dataBinding: DataBinding

  /**
   * Specifies options for the [Data Binding Library](https://developer.android.com/topic/libraries/data-binding/index.html).
   *
   * For more information about the properties you can configure in this block, see [DataBinding]
   */
  fun dataBinding(action: DataBinding.() -> Unit)

  /**
   * Specifies options for the [View Binding Library](https://developer.android.com/topic/libraries/view-binding/index.html).
   *
   * For more information about the properties you can configure in this block, see [ViewBinding]
   */
  override val viewBinding: ViewBinding

  /**
   * Specifies options for the [View Binding Library](https://developer.android.com/topic/libraries/view-binding/index.html).
   *
   * For more information about the properties you can configure in this block, see [ViewBinding]
   */
  fun viewBinding(action: ViewBinding.() -> Unit)

  /**
   * Configure the gathering of code-coverage from tests.
   *
   * This is replaced by [testCoverage].
   */
  @Suppress("DEPRECATION")
  @get:Incubating
  @Deprecated("Renamed to testCoverage", replaceWith = ReplaceWith("testCoverage"))
  override val jacoco: JacocoOptions

  /**
   * Configure the gathering of code-coverage from tests.
   *
   * This is replaced by [testCoverage].
   */
  @Suppress("DEPRECATION")
  @Incubating
  @Deprecated("Renamed to testCoverage", replaceWith = ReplaceWith("testCoverage"))
  fun jacoco(action: JacocoOptions.() -> Unit)

  /**
   * Configure the gathering of code-coverage from tests.
   *
   * To override the JaCoCo version that is used for offline instrumentation and coverage report, add the following to `build.gradle` file:
   * ```
   * android {
   *     testCoverage {
   *         jacocoVersion "<jacoco-version>"
   *     }
   * }
   * ```
   *
   * For more information about the properties you can configure in this block, see [TestCoverage].
   */
  override val testCoverage: TestCoverage

  /**
   * Configure the gathering of code-coverage from tests.
   *
   * To override the JaCoCo version that is used for offline instrumentation and coverage report, add the following to `build.gradle` file:
   * ```
   * android {
   *     testCoverage {
   *         jacocoVersion "<jacoco-version>"
   *     }
   * }
   * ```
   *
   * For more information about the properties you can configure in this block, see [TestCoverage].
   */
  fun testCoverage(action: TestCoverage.() -> Unit)

  /**
   * Specifies options for how the Android plugin should run local and instrumented tests.
   *
   * For more information about the properties you can configure in this block, see [TestOptions].
   */
  override val testOptions: TestOptions

  /**
   * Specifies options for how the Android plugin should run local and instrumented tests.
   *
   * For more information about the properties you can configure in this block, see [TestOptions].
   */
  fun testOptions(action: TestOptions.() -> Unit)

  /**
   * Specifies configurations for [building multiple APKs](https://developer.android.com/studio/build/configure-apk-splits.html) or APK
   * splits.
   *
   * For more information about the properties you can configure in this block, see [Splits].
   */
  override val splits: Splits

  /**
   * Specifies configurations for [building multiple APKs](https://developer.android.com/studio/build/configure-apk-splits.html) or APK
   * splits.
   *
   * For more information about the properties you can configure in this block, see [Splits].
   */
  fun splits(action: Splits.() -> Unit)

  /**
   * Encapsulates source set configurations for all variants.
   *
   * Note that the Android plugin uses its own implementation of source sets. For more information about the properties you can configure in
   * this block, see [AndroidSourceSet].
   */
  override val sourceSets: NamedDomainObjectContainer<out AndroidSourceSet>

  /**
   * Encapsulates source set configurations for all variants.
   *
   * Note that the Android plugin uses its own implementation of source sets. For more information about the properties you can configure in
   * this block, see [AndroidSourceSet].
   */
  fun sourceSets(action: NamedDomainObjectContainer<out AndroidSourceSet>.() -> Unit)

  /**
   * Specifies options for the lint tool.
   *
   * For more information about the properties you can configure in this block, see [Lint].
   */
  override val lint: Lint

  /**
   * Specifies options for the lint tool.
   *
   * For more information about the properties you can configure in this block, see [Lint].
   */
  @Configuring fun lint(action: Lint.() -> Unit)

  /**
   * Specifies options for the lint tool.
   *
   * For more information about the properties you can configure in this block, see [LintOptions].
   */
  @Suppress("DEPRECATION")
  @get:Incubating
  @Deprecated("Renamed to lint", replaceWith = ReplaceWith("lint"))
  override val lintOptions: LintOptions

  /**
   * Specifies options for the lint tool.
   *
   * For more information about the properties you can configure in this block, see [LintOptions].
   */
  @Suppress("DEPRECATION")
  @Incubating
  @Deprecated("Renamed to lint", replaceWith = ReplaceWith("lint"))
  fun lintOptions(action: LintOptions.() -> Unit)

  /**
   * Specifies options for the [Android Debug Bridge (ADB)](https://developer.android.com/studio/command-line/adb.html), such as APK
   * installation options.
   *
   * For more information about the properties you can configure in this block, see [AdbOptions].
   */
  override val installation: TestInstallation

  /**
   * Specifies options for the [Android Debug Bridge (ADB)](https://developer.android.com/studio/command-line/adb.html), such as APK
   * installation options.
   *
   * For more information about the properties you can configure in this block, see [AdbOptions].
   */
  fun installation(action: TestInstallation.() -> Unit)

  override val packaging: Packaging

  /**
   * Specifies options and rules that determine which files the Android plugin packages into your APK.
   *
   * For more information about the properties you can configure in this block, see [Packaging].
   */
  fun packaging(action: Packaging.() -> Unit)

  @Deprecated("Renamed to packaging", replaceWith = ReplaceWith("packaging")) override val packagingOptions: Packaging

  /**
   * Specifies options and rules that determine which files the Android plugin packages into your APK.
   *
   * For more information about the properties you can configure in this block, see [Packaging].
   */
  @Deprecated("Renamed to packaging", replaceWith = ReplaceWith("packaging")) fun packagingOptions(action: Packaging.() -> Unit)

  /**
   * Encapsulates all product flavors configurations for this project.
   *
   * Product flavors represent different versions of your project that you expect to co-exist on a single device, the Google Play store, or
   * repository. For example, you can configure 'demo' and 'full' product flavors for your app, and each of those flavors can specify
   * different features, device requirements, resources, and application ID's--while sharing common source code and resources. So, product
   * flavors allow you to output different versions of your project by simply changing only the components and settings that are different
   * between them.
   *
   * Configuring product flavors is similar to
   * [configuring build types](https://developer.android.com/studio/build/build-variants.html#build-types): add them to the `productFlavors`
   * block of your project's `build.gradle` file and configure the settings you want. Product flavors support the same properties as the
   * `defaultConfig` block--this is because `defaultConfig` defines an object that the plugin uses as the base configuration for all other
   * flavors. Each flavor you configure can then override any of the default values in `defaultConfig`, such as the
   * [`applicationId`](https://d.android.com/studio/build/application-id.html).
   *
   * When using Android plugin 3.0.0 and higher, *each flavor must belong to a
   * [`flavorDimension`](com.android.build.gradle.BaseExtension.html#com.android.build.gradle.BaseExtension:flavorDimensions(java.lang.String[]))
   * value*. By default, when you specify only one dimension, all flavors you configure belong to that dimension. If you specify more than
   * one flavor dimension, you need to manually assign each flavor to a dimension. To learn more, read
   * [Use Flavor Dimensions for variant-aware dependency management](https://developer.android.com/studio/build/gradle-plugin-3-0-0-migration.html#variant_aware).
   *
   * When you configure product flavors, the Android plugin automatically combines them with your [BuildType] configurations to
   * [create build variants](https://developer.android.com/studio/build/build-variants.html). If the plugin creates certain build variants
   * that you don't want, you can [filter variants](https://developer.android.com/studio/build/build-variants.html#filter-variants).
   *
   * @see [ProductFlavor]
   */
  override val productFlavors: NamedDomainObjectContainer<out TestProductFlavor>

  /**
   * Encapsulates all product flavors configurations for this project.
   *
   * For more information about the properties you can configure in this block, see [ProductFlavor]
   */
  fun productFlavors(action: NamedDomainObjectContainer<TestProductFlavor>.() -> Unit)

  /**
   * Specifies defaults for variant properties that the Android plugin applies to all build variants.
   *
   * You can override any `defaultConfig` property when
   * [configuring product flavors](https://developer.android.com/studio/build/build-variants.html#product-flavors)
   *
   * For more information about the properties you can configure in this block, see [DefaultConfig].
   */
  override val defaultConfig: TestDefaultConfig

  /**
   * Specifies defaults for variant properties that the Android plugin applies to all build variants.
   *
   * You can override any `defaultConfig` property when
   * [configuring product flavors](https://developer.android.com/studio/build/build-variants.html#product-flavors)
   *
   * For more information about the properties you can configure in this block, see [DefaultConfig].
   */
  fun defaultConfig(action: TestDefaultConfig.() -> Unit)

  /**
   * Encapsulates signing configurations that you can apply to [ ] and [ ] configurations.
   *
   * Android requires that all APKs be digitally signed with a certificate before they can be installed onto a device. When deploying a
   * debug version of your project from Android Studio, the Android plugin automatically signs your APK with a generic debug certificate.
   * However, to build an APK for release, you must [sign the APK](https://developer.android.com/studio/publish/app-signing.html) with a
   * release key and keystore. You can do this by either
   * [using the Android Studio UI](https://developer.android.com/studio/publish/app-signing.html#sign-apk) or manually
   * [configuring your `build.gradle` file](https://developer.android.com/studio/publish/app-signing.html#gradle-sign).
   *
   * @see [ApkSigningConfig]
   */
  override val signingConfigs: NamedDomainObjectContainer<out ApkSigningConfig>

  /**
   * Encapsulates signing configurations that you can apply to [BuildType] and [ProductFlavor] configurations.
   *
   * For more information about the properties you can configure in this block, see [ApkSigningConfig].
   */
  fun signingConfigs(action: NamedDomainObjectContainer<out ApkSigningConfig>.() -> Unit)

  /**
   * Specifies options for external native build using [CMake](https://cmake.org/) or
   * [ndk-build](https://developer.android.com/ndk/guides/ndk-build.html).
   *
   * When using [Android Studio 2.2 or higher](https://developer.android.com/studio/index.html) with
   * [Android plugin 2.2.0 or higher](https://developer.android.com/studio/releases/gradle-plugin.html), you can compile C and C++ code into
   * a native library that Gradle packages into your APK.
   *
   * To learn more, read [Add C and C++ Code to Your Project](https://developer.android.com/studio/projects/add-native-code.html).
   *
   * @see ExternalNativeBuild
   *
   * since 2.2.0
   */
  override val externalNativeBuild: ExternalNativeBuild

  /**
   * Specifies options for external native build using [CMake](https://cmake.org/) or
   * [ndk-build](https://developer.android.com/ndk/guides/ndk-build.html).
   *
   * When using [Android Studio 2.2 or higher](https://developer.android.com/studio/index.html) with
   * [Android plugin 2.2.0 or higher](https://developer.android.com/studio/releases/gradle-plugin.html), you can compile C and C++ code into
   * a native library that Gradle packages into your APK.
   *
   * To learn more, read [Add C and C++ Code to Your Project](https://developer.android.com/studio/projects/add-native-code.html).
   *
   * @see ExternalNativeBuild
   *
   * since 2.2.0
   */
  fun externalNativeBuild(action: ExternalNativeBuild.() -> Unit)

  /** The Gradle path of the project that this test project tests. */
  var targetProjectPath: String?
}
