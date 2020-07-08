/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.build.api.component.ComponentBuilder
import com.android.build.api.component.ComponentIdentity
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import org.gradle.api.Action
import org.gradle.api.Incubating

/**
 * Generic extension for Android Gradle Plugin related components.
 *
 * Each component has a type, like application or library and will have a dedicated extension with
 * methods that are related to the particular component type.
 *
 * @param VariantBuilderT the [ComponentBuilder] type produced by this variant.
 */
@Incubating
interface AndroidComponentsExtension<
        VariantBuilderT: VariantBuilder, VariantT: Variant> {

    /**
     * Creates a [GenericVariantSelector] instance that can be configured
     * to reduce the set of [ComponentBuilder] instances participating in the [beforeVariants]
     * and [onVariants] callback invocation.
     *
     * @return [GenericVariantSelector] to select the variants of interest.
     */
    fun <T: ComponentIdentity> selector(): GenericVariantSelector<T>

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
     * '''kotlin
     *  androidComponents {
     *      beforeVariants {
     *          println("Called with variant : ${'$'}name")
     *      }
     *  }
     * '''
     *
     * Example with selection:
     * '''kotlin
     *  androidComponents {
     *      val debug = selector().withBuildType("debug")
     *      beforeVariants(debug) {
     *          println("Called with variant : ${'$'}name")
     *      }
     *  }
     * '''
     *
     * @param selector [GenericVariantSelector] instance to select which instance of [VariantBuilderT] are
     * of interest. By default, all instances are of interest.
     * @param callback lambda to be called with each instance of [VariantBuilderT] of interest.
     */
    fun beforeVariants(
            selector: VariantSelector<VariantBuilderT> = selector<VariantBuilderT>().all(),
            callback: (VariantBuilderT) -> Unit)

    /**
     * [Action] based version of [beforeVariants] above.
     */
    fun beforeVariants(
            selector: VariantSelector<VariantBuilderT> = selector<VariantBuilderT>().all(),
            callback: Action<VariantBuilderT>)

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
     * information is represented as [org.gradle.api.provider.Property] and can still be modified
     * and have internal [org.gradle.api.Task] to the Android Gradle Plugin see and use the updated
     * value.
     */
    fun onVariants(
            selector: VariantSelector<VariantT> = selector<VariantT>().all(),
            callback: (VariantT) -> Unit
    )

    /**
     * [Action] based version of [onVariants] above.
     */
    fun onVariants(
            selector: VariantSelector<VariantT> = selector<VariantT>().all(),
            callback: Action<VariantT>
    )
}
