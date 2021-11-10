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

package com.android.build.api.extension

import com.android.build.api.AndroidPluginVersion
import com.android.build.api.component.ComponentBuilder
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.SdkComponents
import com.android.build.api.variant.DslExtension
import com.android.build.api.variant.DslLifecycle
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.api.variant.VariantExtension
import com.android.build.api.variant.VariantExtensionConfig
import org.gradle.api.Action
import org.gradle.api.Incubating

@Deprecated(
    message= "Use the com.android.build.api.variant package",
    replaceWith = ReplaceWith(
        "AndroidComponentsExtension",
        "com.android.build.api.variant.AndroidComponentsExtension"),
    level = DeprecationLevel.WARNING
)
interface AndroidComponentsExtension<
        DslExtensionT: CommonExtension<*, *, *, *>,
        VariantBuilderT: VariantBuilder,
        VariantT: Variant>
    : DslLifecycle<DslExtensionT> {

    /**
     * API to customize the DSL Objects programmatically before the [beforeVariants] is called.
     *
     * Example of a build type creation:
     * ```kotlin
     * androidComponents.finalizeDsl { extension ->
     *     extension.buildTypes.create("extra")
     * }
     * ```
     *
     * The list of variants will be finalized after all finalizeDsl Callbacks and cannot be altered
     * in the other APIs like [beforeVariants] or [onVariants].
     */
    override fun finalizeDsl(callback: (DslExtensionT) -> Unit)

    /**
     * [Action] based version of [finalizeDsl] above.
     */
    @Deprecated("Replaced by finalizeDsl", replaceWith = ReplaceWith("finalizeDsl"))
    fun finalizeDSl(callback: Action<DslExtensionT>)

    /**
     * The version of the Android Gradle Plugin currently in use.
     */
    val pluginVersion: AndroidPluginVersion

    /**
     * Provides access to underlying Android SDK and build-tools components like adb.
     *
     * @return [SdkComponents] to access Android SDK used by Gradle.
     */
    @get:Incubating
    val sdkComponents: SdkComponents

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
     * At this stage, access to the DSL objects is disallowed, use [afterDsl] method to
     * programmatically access the DSL objects before the [VariantBuilderT] object is built.
     *
     * Example without selection:
     * ```kotlin
     *  androidComponents {
     *      beforeVariants {
     *          println("Called with variant : ${'$'}name")
     *      }
     *  }
     * ```
     *
     * Example with selection:
     * ```kotlin
     *  androidComponents {
     *      val debug = selector().withBuildType("debug")
     *      beforeVariants(debug) {
     *          println("Called with variant : ${'$'}name")
     *      }
     *  }
     * ```
     *
     * @param selector [VariantSelector] instance to select which instance of [VariantBuilderT] are
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
        selector: VariantSelector,
        callback: Action<VariantBuilderT>
    )

    /**
     * Allow for registration of a [callback] to be called with variant instances of type [VariantT]
     * once the list of [com.android.build.api.artifact.Artifact] has been determined.
     *
     * At this stage, access to the DSL objects is disallowed and access to the [VariantBuilderT]
     * instance is limited to read-only access.
     *
     * Because the list of artifacts (including private ones) is final, one cannot change the build
     * flow anymore as [org.gradle.api.Task]s are now expecting those artifacts as inputs. However
     * users can modify such artifacts by replacing or transforming them, see [com.android.build.api.artifact.Artifacts]
     * for details.
     *
     * Code executing in the [callback] also has access to the [VariantT] information which is used
     * to configure [org.gradle.api.Task] inputs (for example, the buildConfigFields). Such
     * information represented as [org.gradle.api.provider.Property] can still be modified ensuring
     * that all [org.gradle.api.Task]s created by the Android Gradle Plugin use the updated value.
     */
    fun onVariants(
        selector: VariantSelector = selector().all(),
        callback: (VariantT) -> Unit
    ) { }

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
     * that will be stored with the Android Gradle Plugin [com.android.build.api.variant.Variant]
     * instance.
     *
     * Variant Scoped objects should use [org.gradle.api.provider.Property<T>] for its mutable
     * state to allow for late binding. (see [com.android.build.api.variant.Variant] for examples).
     *
     * @param dslExtension the DSL extension configuration.
     * @param configurator a lambda to create a variant scoped object. The lambda is
     * provided with the [VariantExtensionConfig] that can be used to retrieve the [VariantT]
     * instance as well as DSL extensions registered with [DslExtension]
     * @return an sub type of [VariantExtension] instance that will be stored with the [VariantT]
     * instance and can be retrieved by [Variant.getExtension] API.
     */
    @Incubating
    fun registerExtension(
        dslExtension: DslExtension,
        configurator: (variantExtensionConfig: VariantExtensionConfig<VariantT>) -> VariantExtension
    )
}
