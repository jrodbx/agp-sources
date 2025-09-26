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

import org.gradle.api.NamedDomainObjectContainer

/**
 * Extension for the Android Dynamic Feature Gradle Plugin.
 *
 * This is the `android` block when the `com.android.dynamic-feature` plugin is applied.
 *
 * Only the Android Gradle Plugin should create instances of interfaces in com.android.build.api.dsl.
 */
interface DynamicFeatureExtension: CommonExtension, ApkExtension, TestedExtension {

    /**
     * Specifies options related to the processing of Android Resources.
     *
     * For more information about the properties you can configure in this block, see [DynamicFeatureAndroidResources].
     */
    override val androidResources: DynamicFeatureAndroidResources
    /**
     * Specifies options related to the processing of Android Resources.
     *
     * For more information about the properties you can configure in this block, see [DynamicFeatureAndroidResources].
     */
    fun androidResources(action: DynamicFeatureAndroidResources.() -> Unit)

    /**
     * A list of build features that can be enabled or disabled on the Android Project.
     */
    override val buildFeatures: DynamicFeatureBuildFeatures

    /**
     * A list of build features that can be enabled or disabled on the Android Project.
     */
    fun buildFeatures(action: DynamicFeatureBuildFeatures.() -> Unit)



    /**
     * Encapsulates all build type configurations for this project.
     *
     * Unlike using [DynamicFeatureProductFlavor] to create
     * different versions of your project that you expect to co-exist on a single device, build
     * types determine how Gradle builds and packages each version of your project. Developers
     * typically use them to configure projects for various stages of a development lifecycle. For
     * example, when creating a new project from Android Studio, the Android plugin configures a
     * 'debug' and 'release' build type for you.
     * You can then combine build types with product flavors to
     * [create build variants](https://developer.android.com/studio/build/build-variants.html).
     *
     * @see BuildType
     */
    override val buildTypes: NamedDomainObjectContainer<out DynamicFeatureBuildType>

    /**
     * Encapsulates all build type configurations for this project.
     *
     * For more information about the properties you can configure in this block, see [DynamicFeatureBuildType]
     */
    fun buildTypes(action: NamedDomainObjectContainer<DynamicFeatureBuildType>.() -> Unit)

    /**
     * Shortcut extension method to allow easy access to the predefined `debug` [DynamicFeatureBuildType]
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
    fun NamedDomainObjectContainer<DynamicFeatureBuildType>.debug(action: DynamicFeatureBuildType.() -> Unit)
    /**
     * Shortcut extension method to allow easy access to the predefined `release` [DynamicFeatureBuildType]
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
    fun NamedDomainObjectContainer<DynamicFeatureBuildType>.release(action: DynamicFeatureBuildType.() -> Unit)

    /**
     * Specifies options for the
     * [Android Debug Bridge (ADB)](https://developer.android.com/studio/command-line/adb.html),
     * such as APK installation options.
     *
     * For more information about the properties you can configure in this block, see [AdbOptions].
     */
    override val installation: DynamicFeatureInstallation

    /**
     * Specifies options for the
     * [Android Debug Bridge (ADB)](https://developer.android.com/studio/command-line/adb.html),
     * such as APK installation options.
     *
     * For more information about the properties you can configure in this block, see [AdbOptions].
     */
    fun installation(action: DynamicFeatureInstallation.() -> Unit)


    /**
     * Encapsulates all product flavors configurations for this project.
     *
     * Product flavors represent different versions of your project that you expect to co-exist
     * on a single device, the Google Play store, or repository. For example, you can configure
     * 'demo' and 'full' product flavors for your app, and each of those flavors can specify
     * different features, device requirements, resources, and application ID's--while sharing
     * common source code and resources. So, product flavors allow you to output different versions
     * of your project by simply changing only the components and settings that are different
     * between them.
     *
     *
     * Configuring product flavors is similar to
     * [configuring build types](https://developer.android.com/studio/build/build-variants.html#build-types):
     * add them to the `productFlavors` block of your project's `build.gradle` file
     * and configure the settings you want.
     * Product flavors support the same properties as the `defaultConfig`
     * block--this is because `defaultConfig` defines an object that the plugin uses as the base
     * configuration for all other flavors. Each flavor you configure can then override any of the
     * default values in `defaultConfig`, such as the
     * [`applicationId`](https://d.android.com/studio/build/application-id.html).
     *
     *
     * When using Android plugin 3.0.0 and higher, *each flavor must belong to a
     * [`flavorDimension`](com.android.build.gradle.BaseExtension.html#com.android.build.gradle.BaseExtension:flavorDimensions(java.lang.String[]))
     * value*. By default, when you specify only one
     * dimension, all flavors you configure belong to that dimension. If you specify more than one
     * flavor dimension, you need to manually assign each flavor to a dimension. To learn more, read
     * [Use Flavor Dimensions for variant-aware dependency management](https://developer.android.com/studio/build/gradle-plugin-3-0-0-migration.html#variant_aware).
     *
     *
     * When you configure product flavors, the Android plugin automatically combines them with
     * your [DynamicFeatureBuildType] configurations to
     * [create build variants](https://developer.android.com/studio/build/build-variants.html).
     * If the plugin creates certain build variants that you don't want, you can
     * [filter variants](https://developer.android.com/studio/build/build-variants.html#filter-variants).
     *
     * @see [DynamicFeatureProductFlavor]
     */
    override val productFlavors: NamedDomainObjectContainer<out DynamicFeatureProductFlavor>

    /**
     * Encapsulates all product flavors configurations for this project.
     *
     * For more information about the properties you can configure in this block,
     * see [DynamicFeatureProductFlavor]
     */
    fun productFlavors(action: NamedDomainObjectContainer<DynamicFeatureProductFlavor>.() -> Unit)


    /**
     * Specifies defaults for variant properties that the Android plugin applies to all build
     * variants.
     *
     * You can override any `defaultConfig` property when
     * [configuring product flavors](https://developer.android.com/studio/build/build-variants.html#product-flavors)
     *
     * For more information about the properties you can configure in this block, see [DynamicFeatureDefaultConfig].
     */
    override val defaultConfig: DynamicFeatureDefaultConfig

    /**
     * Specifies defaults for variant properties that the Android plugin applies to all build
     * variants.
     *
     * You can override any `defaultConfig` property when
     * [configuring product flavors](https://developer.android.com/studio/build/build-variants.html#product-flavors)
     *
     * For more information about the properties you can configure in this block, see [DynamicFeatureDefaultConfig].
     */
    fun defaultConfig(action: DynamicFeatureDefaultConfig.() -> Unit)



    val privacySandbox: PrivacySandbox
    fun privacySandbox(action: PrivacySandbox.() -> Unit)
}
