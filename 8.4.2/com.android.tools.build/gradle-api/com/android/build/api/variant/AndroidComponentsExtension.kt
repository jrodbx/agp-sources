/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.api.variant

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Action
import org.gradle.api.Incubating

/**
 * Generic extension for Android Gradle Plugin related components.
 *
 * Each component has a type, like application or library and will have a dedicated extension with
 * methods that are related to the particular component type.
 *
 * This can be used via
 * ```kotlin
 * androidComponents {
 * }
 * ```
 *
 * In a plugin this can be queried via
 * ```kotlin
 * project.plugins.withType(AppPlugin::class.java) {
 *   val androidComponents = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
 * }
 * ````
 *
 * @param DslExtensionT the type of the DSL to be used in [DslLifecycle.finalizeDsl]
 * @param VariantBuilderT the [ComponentBuilder] type produced by this variant.
 * @param VariantT the [Variant] type produced by this variant.
 */
interface AndroidComponentsExtension<
        DslExtensionT: CommonExtension<*, *, *, *, *, *>,
        VariantBuilderT: VariantBuilder,
        VariantT: Variant>
    : DslLifecycle<DslExtensionT>, AndroidComponents {

    /**
     * [Action] based version of [finalizeDsl] above.
     */
    @Deprecated("Replaced by finalizeDsl", replaceWith = ReplaceWith("finalizeDsl(callback)"))
    fun finalizeDSl(callback: Action<DslExtensionT>)

    /**
     * Creates a [VariantSelector] instance that can be configured
     * to reduce the set of [ComponentBuilder] instances participating in the [beforeVariants]
     * and [onVariants] callback invocation.
     *
     * @return [VariantSelector] to select the variants of interest.
     */
    fun selector(): VariantSelector

    /**
     * Method to register a [callback] to be called with [VariantBuilderT] instances that
     * satisfies the [selector]. The [callback] will be called as soon as the [VariantBuilderT]
     * instance has been created but before any [com.android.build.api.artifact.Artifact] has been
     * determined, therefore the build flow can still be changed when the [callback] is invoked.
     *
     * At this stage, access to the DSL objects is disallowed, use [finalizeDsl] method to
     * programmatically access the DSL objects before the [VariantBuilderT] object is built.
     *
     * The goal of this callback is to make changes before the variants and components are created.
     * This includes enabling/disabling components and making decisions on properties that can
     * impact task creation and build flow. This guarantees that the matching [onVariants] callback
     * does not have to deal with changing configuration and can focus on updating task inputs.
     * See [ComponentBuilder] for more information.
     *
     * Example without selection:
     * ```kotlin
     *  androidComponents {
     *      beforeVariants {
     *      }
     *  }
     * ```
     *
     * Example with selection:
     * ```kotlin
     *  androidComponents {
     *      val debug = selector().withBuildType("debug")
     *      beforeVariants(debug) {
     *      }
     *  }
     * ```
     *
     * See [here](https://developer.android.com/build/extend-agp#variant-api-artifacts-tasks) for
     * more information
     *
     * @param selector [VariantSelector] to select which instance of [VariantBuilderT] are
     * of interest. By default, all instances are of interest.
     * @param callback lambda to be called with each instance of [VariantBuilderT] of interest.
     */
    fun beforeVariants(
            selector: VariantSelector = selector().all(),
            callback: (VariantBuilderT) -> Unit)

    /**
     * [Action] based version of [beforeVariants] above.
     */
    fun beforeVariants(
            selector: VariantSelector = selector().all(),
            callback: Action<VariantBuilderT>)

    /**
     * Allow for registration of a [callback] to be called with variant instances of type [VariantT]
     * once the list of [com.android.build.api.artifact.Artifact] has been determined.
     *
     * At this stage, access to the DSL objects is disallowed and access to the [VariantBuilderT]
     * instance is limited to read-only access.
     *
     * At this stage, the build flow is final, with the callback for [beforeVariants] having
     * modified properties that can impact which tasks are created and how they are configured.
     * The ability to query or modify the build intermediate files between tasks, including adding
     * additional new steps between existing tasks must be done via the [Component.artifacts] API.
     * See [com.android.build.api.artifact.Artifacts] for details.
     *
     * The [VariantT] object exposes many [org.gradle.api.provider.Property] that are then used as
     * task inputs. It is safe to set new values on the properties, including using
     * [org.gradle.api.provider.Provider] with or without task dependencies.
     * When reading values, these [org.gradle.api.provider.Property] must be lazily linked to
     * properties used as task inputs. It is not safe to call [org.gradle.api.provider.Property.get]
     * during build configuration.
     *
     * Example without selection:
     * ```kotlin
     *  androidComponents {
     *      onVariants {
     *      }
     *  }
     * ```
     *
     * Example with selection:
     * ```kotlin
     *  androidComponents {
     *      val debug = selector().withBuildType("debug")
     *      onVariants(debug) {
     *      }
     *  }
     * ```
     *
     * See [here](https://developer.android.com/build/extend-agp#variant-api-artifacts-tasks) for
     * more information
     *
     * @param selector [VariantSelector] to select which instance of [VariantBuilderT] are
     * of interest. By default, all instances are of interest.
     * @param callback lambda to be called with each instance of [VariantT] of interest.
     */
    fun onVariants(
            selector: VariantSelector = selector().all(),
            callback: (VariantT) -> Unit
    )

    /**
     * [Action] based version of [onVariants] above.
     */
    fun onVariants(
            selector: VariantSelector = selector().all(),
            callback: Action<VariantT>
    )

    /**
     * Register an Android Gradle Plugin DSL extension.
     *
     * Please see Gradle documentation first at :
     * https://docs.gradle.org/current/userguide/custom_plugins.html#sec:getting_input_from_the_build
     *
     * A lambda must be provided to create and configure the variant scoped object
     * that will be stored alongside the Android Gradle Plugin's [com.android.build.api.variant.Variant]
     * instance.
     *
     * Variant Scoped objects should use [org.gradle.api.provider.Property<T>] for its mutable
     * state to allow for late binding. (see [com.android.build.api.variant.Variant] for examples).
     *
     * The [DslExtension.Builder] allow you to choose if you want to extend Project, BuiltType or
     * ProductFlavor. You can extend just one or up to all of them.
     *
     * A BuildType extension of type BuildTypeDslExtension will allow users to have the following
     * declarations in their build files:
     * ```
     * android {
     *     buildTypes {
     *         debug {
     *             extensions.configure<BuildTypeDslExtension> {
     *                 buildTypeSettingOne = "build_type_debug"
     *             }
     *         }
     *     }
     * }
     * ```
     *
     * A Product flavor extension of type ProductFlavorDslExtension will allow users to specify the
     * following declarations in their build files:
     * ```
     * android {
     *     flavorDimensions += "version"
     *     productFlavors {
     *         create("demo")  {
     *             dimension = "version"
     *             extensions.configure<ProductFlavorDslExtension> {
     *                 productFlavorSettingOne = "product_flavor_demo"
     *                 productFlavorSettingTwo = 99
     *             }
     *         }
     *     }
     * }
     * ```
     *
     * @param dslExtension the DSL extension configuration.
     * @param configurator a lambda to create a variant scoped object. The lambda is
     * provided with the [VariantExtensionConfig] that can be used to retrieve the [VariantT]
     * instance as well as DSL extensions registered with [DslExtension]
     * @return an instance of a sub type of [VariantExtension] that will be stored with the [VariantT]
     * instance and can be retrieved by [Variant.getExtension] API.
     *
     */
    @Incubating
    fun registerExtension(
        dslExtension: DslExtension,
        configurator: (variantExtensionConfig: VariantExtensionConfig<VariantT>) -> VariantExtension
    )

    /**
     * Register a new source type to all source sets.
     *
     * The name of the source type will be used to create expected directories names in the various
     * source sets. For instance, src/main/<name> or src/debug/<name>.
     *
     * There is no notion of priorities between the build-type, flavor specific directories, all
     * the expected directories will be interpreted as a flat namespace.
     *
     * Therefore, any [org.gradle.api.Task] that needs access to the entire list of source folders
     * can just use the [Sources.extras]'s [Flat.all] method for that source type.
     *
     * However, If you need to have overriding priorities between the expected directories and
     * therefore require a merging activity, you can still use this API but you will need to
     * create a merging task that will have all sources in input and produce a single output
     * folder for the merged sources.
     *
     * @param name the name of the source type.
     */
    @Incubating
    fun registerSourceType(
        name: String,
    )
}
