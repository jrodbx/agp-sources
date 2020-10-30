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

import com.android.build.api.component.AndroidTestBuilder
import com.android.build.api.component.AndroidTest
import com.android.build.api.component.ComponentBuilder
import com.android.build.api.component.UnitTestBuilder
import com.android.build.api.component.UnitTest
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
            selector: VariantSelector = selector().all(),
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
     * information represented as [org.gradle.api.provider.Property] can still be modified ensuring
     * that all [org.gradle.api.Task]s created by the Android Gradle Plugin use the updated value.
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
     * Allow for registration of a [callback] to be called with [UnitTestBuilder] that
     * satisfies the [selector]. The [callback] will be called as soon as the [UnitTestBuilder]
     * instance has been created but before any [com.android.build.api.artifact.Artifact] related to
     * unit tests has been determined, therefore the build flow can still be changed when the
     * [callback] is invoked.
     *
     * @param selector [VariantSelector] instance to select which instance of [UnitTestBuilder]
     * are of interest. By default, all instances are of interest.
     * @param callback lambda to be called with each instance [UnitTestBuilder] of interest.
     */
    fun beforeUnitTest(
            selector: VariantSelector = selector().all(),
            callback: (UnitTestBuilder) -> Unit)

    /**
     * [Action] based version of [beforeUnitTest] above.
     */
    fun beforeUnitTest(
            selector: VariantSelector = selector().all(),
            callback: Action<UnitTestBuilder>)

    /**
     * Allow for registration of a [callback] to be called with [AndroidTestBuilder] that
     * satisfies the [selector]. The [callback] will be called as soon as the [AndroidTestBuilder]
     * instance has been created but before any [com.android.build.api.artifact.Artifact] related to
     * android tests has been determined, therefore the build flow can still be changed when the
     * [callback] is invoked.
     *
     * @param selector [VariantSelector] instance to select which instance of [AndroidTestBuilder]
     * are of interest. By default, all instances are of interest.
     * @param callback lambda to be called with each instance [AndroidTestBuilder] of interest.
     */
    fun beforeAndroidTest(
            selector: VariantSelector = selector().all(),
            callback: (AndroidTestBuilder) -> Unit)

    /**
     * [Action] based version of the [beforeAndroidTest] above.
     */
    fun beforeAndroidTest(
            selector: VariantSelector = selector().all(),
            callback: Action<AndroidTestBuilder>)

    /**
     * Allow for registration of a [callback] to be called with instances of type [UnitTest]
     * once the list of [com.android.build.api.artifact.Artifact] has been determined.
     *
     * At this stage, access to the DSL objects is disallowed and access to the [UnitTestBuilder]
     * instance is limited to read-only access.
     *
     * Because the list of artifacts (including private ones) is final, one cannot change the build
     * flow anymore as [org.gradle.api.Task]s are now expecting those artifacts as inputs. However
     * users can modify such artifacts by replacing or transforming them, see [com.android.build.api.artifact.Artifacts]
     * for details.
     *
     * Code executing in the [callback] also has access to the [UnitTest] information which is used
     * to configure [org.gradle.api.Task] inputs. Such information represented as
     * [org.gradle.api.provider.Property] can still be modified ensuring that all
     * [org.gradle.api.Task]s created by the Android Gradle Plugin use the updated value.
     */
    fun unitTest(
            selector: VariantSelector = selector().all(),
            callback: Action<UnitTest>)

    /**
     * [Action] based version of the [unitTest] above.
     */
    fun unitTest(
            selector: VariantSelector = selector().all(),
            callback: (UnitTest) -> Unit)

    /**
     * Allow for registration of a [callback] to be called with instances of type [AndroidTest]
     * once the list of [com.android.build.api.artifact.Artifact] has been determined.
     *
     * At this stage, access to the DSL objects is disallowed and access to the [AndroidTestBuilder]
     * instance is limited to read-only access.
     *
     * Because the list of artifacts (including private ones) is final, one cannot change the build
     * flow anymore as [org.gradle.api.Task]s are now expecting those artifacts as inputs. However
     * users can modify such artifacts by replacing or transforming them, see [com.android.build.api.artifact.Artifacts]
     * for details.
     *
     * Code executing in the [callback] also has access to the [AndroidTest] information which is used
     * to configure [org.gradle.api.Task] inputs. Such information represented as
     * [org.gradle.api.provider.Property] can still be modified ensuring that all
     * [org.gradle.api.Task]s created by the Android Gradle Plugin use the updated value.
     */
    fun androidTest(
            selector: VariantSelector = selector().all(),
            callback: Action<AndroidTest>)

    /**
     * [Action] based version of the [AndroidTest] above.
     */
    fun androidTest(
            selector: VariantSelector = selector().all(),
            callback: (AndroidTest) -> Unit)
}
