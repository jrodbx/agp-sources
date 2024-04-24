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
@file:Suppress("DEPRECATION")

package com.android.build.api.variant

import com.android.build.api.component.UnitTest
import org.gradle.api.Incubating
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import java.io.Serializable

/**
 * Model for variant components that only contains build-time properties
 *
 * Variant components are the main output of the plugin (e.g. APKs, AARs). They contain references
 * to optional secondary components (tests, fixtures)
 *
 * It is the object passed to the [AndroidComponentsExtension.onVariants] method, like this:
 *
 * ```kotlin
 * androidComponents {
 *   onVariants(selector().all()) { variant: Variant ->
 *   }
 * }
 * ```
 *
 * Note that depending on the actual implementation of [AndroidComponentsExtension], the object
 * received may be of a child type. For instance [ApplicationAndroidComponentsExtension.onVariants]
 * will pass [ApplicationVariant] to the lambda.
 *
 * See [here](https://developer.android.com/build/extend-agp#variant-api-artifacts-tasks) for
 * more information
 */
interface Variant : Component, HasAndroidResources {

    /**
     * Gets the minimum supported SDK Version for this variant.
     */
    val minSdk: AndroidVersion

    @get:Deprecated(
            "Will be removed in v9.0",
            replaceWith = ReplaceWith("minSdk")
    )
    val minSdkVersion: AndroidVersion

    /**
     * Gets the maximum supported SDK Version for this variant.
     */
    val maxSdk: Int?

    @get:Deprecated(
            "Will be removed in v9.0",
            replaceWith = ReplaceWith("maxSdk")
    )
    val maxSdkVersion: Int?

    /**
     * Gets the target SDK Version for this variant.
     */
    @get:Deprecated(
        "Will be removed in v9.0",
        replaceWith = ReplaceWith("GeneratesApk.targetSdk")
    )
    val targetSdkVersion: AndroidVersion

    /**
     * Variant's [BuildConfigField] which will be generated in the BuildConfig class.
     */
    val buildConfigFields: MapProperty<String, BuildConfigField<out Serializable>>

    /**
     * [MapProperty] of the variant's manifest placeholders.
     *
     * Placeholders are organized with a key and a value. The value is a [String] that will be
     * used as is in the merged manifest.
     *
     * @return the [MapProperty] with keys as [String]
     */
    val manifestPlaceholders: MapProperty<String, String>

    /**
     * Variant's packagingOptions, initialized by the corresponding global DSL element.
     */
    val packaging: Packaging

    /**
     * Variant's cmake [ExternalNativeBuild], initialized by merging the product flavor values or
     * null if no cmake external build is configured for this variant.
     */
    val externalNativeBuild: ExternalNativeBuild?

    /**
     * Variant's [UnitTest], or null if the unit tests for this variant are disabled.
     */
    @get:Deprecated(
        "Will be removed in v9.0",
        replaceWith = ReplaceWith("(Variant.Subtype).unitTest where available")
    )
    val unitTest: UnitTest?

    /**
     * Returns an extension object registered via the [VariantBuilder.registerExtension] API or
     * null if none were registered under the passed [type].
     *
     * @return the registered object or null.
     */
    fun <T> getExtension(type: Class<T>): T?

    /**
     * List of proguard configuration files for this variant. The list is initialized from the
     * corresponding DSL element, and cannot be queried at configuration time. At configuration time,
     * you can only add new elements to the list.
     *
     * This list will be initialized from [com.android.build.api.dsl.VariantDimension#proguardFile]
     * for non test related variants and from
     * [com.android.build.api.dsl.VariantDimension.testProguardFiles] for test related variants.
     */
    val proguardFiles: ListProperty<RegularFile>

    /**
     * Additional per variant experimental properties.
     *
     * Initialized from [com.android.build.api.dsl.CommonExtension.experimentalProperties]
     */
    @get:Incubating
    val experimentalProperties: MapProperty<String, Any>

    /**
     * List of the components nested in this variant, the returned list will contain:
     *
     * * [UnitTest] component if the unit tests for this variant are enabled,
     * * [AndroidTest] component if this variant [HasAndroidTest] and android tests for this variant
     * are enabled,
     * * [TestFixtures] component if this variant [HasTestFixtures] and test fixtures for this
     * variant are enabled.
     *
     * Use this list to do operations on all nested components of this variant without having to
     * manually check whether the variant has each component.
     *
     * Example:
     *
     * ```kotlin
     *  androidComponents.onVariants(selector().withName("debug")) {
     *      // will return unitTests, androidTests, testFixtures for the debug variant (if enabled).
     *      nestedComponents.forEach { component ->
     *          component.instrumentation.transformClassesWith(NestedComponentsClassVisitorFactory::class.java,
     *                                         InstrumentationScope.Project) {}
     *      }
     *  }
     *  ```
     */
    @get:Incubating
    val nestedComponents: List<Component>

    /**
     * List containing this variant and all of its [nestedComponents]
     *
     * Example:
     *
     * ```kotlin
     *  androidComponents.onVariants(selector().withName("debug")) {
     *      // components contains the debug variant along with its unitTests, androidTests, and
     *      // testFixtures (if enabled).
     *      components.forEach { component ->
     *          component.runtimeConfiguration
     *              .resolutionStrategy
     *              .dependencySubstitution {
     *                  substitute(project(":foo")).using(project(":bar"))
     *              }
     *      }
     *  }
     *  ```
     */
    @get:Incubating
    val components: List<Component>

    /**
     * Set up a new matching request for a given flavor dimension and value.
     *
     * @see [com.android.build.api.dsl.BaseFlavor.missingDimensionStrategy]
     * To learn more, read [Select default flavors for missing dimensions](d.android.com//build/build-variants).
     *
     * @param dimension the flavor dimension
     * @param requestedValues the flavor name(s)
     */
    @Incubating
    fun missingDimensionStrategy(dimension: String, vararg requestedValues: String)
}
