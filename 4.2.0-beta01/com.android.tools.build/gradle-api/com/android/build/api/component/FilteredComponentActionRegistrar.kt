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

package com.android.build.api.component

import org.gradle.api.Action
import org.gradle.api.Incubating
import java.util.regex.Pattern

/**
 * Allows registering Actions on [ComponentT], with the ability to include
 * filters to target subsets of [ComponentT].
 *
 * The filters act on the properties of [ComponentIdentity].
 *
 * Calls can be chained to include more than one filter, though in some cases, selecting a
 * particular filter can reduce the list of available filters in the chain.
 */
@Incubating
interface FilteredComponentActionRegistrar<ComponentT>
        where ComponentT: ActionableComponentObject, ComponentT: ComponentIdentity {

    /**
     * Returns a new registrar for [ComponentT] objects with a given build type.
     *
     * @param buildType Build type to filter [ComponentT] on.
     * @return An instance of [BuildTypedComponentActionRegistrar] to further filter variants.
     */
    fun withBuildType(buildType: String): BuildTypedComponentActionRegistrar<ComponentT>

    /**
     * Registers an [Action] for [ComponentT] objects with a given build type.
     *
     * @param buildType Build type to filter [ComponentT] on.
     * @param action [Action] to run on filtered [ComponentT].
     */
    fun withBuildType(buildType: String, action: Action<ComponentT>)

    /**
     * Registers an action for [ComponentT] objects with a given build type.
     *
     * @param buildType Build type to filter [ComponentT] on.
     * @param action Lambda function to run on filtered [ComponentT].
     */
    fun withBuildType(buildType: String, action: ComponentT.() -> Unit)

    /**
     * Returns a new registrar for [ComponentT] objects with a given (dimension, flavorName).
     *
     * @param flavorToDimension Dimension and flavor to filter [ComponentT] on.
     * @return [FlavoredComponentActionRegistrar] instance to further filter instances of [ComponentT]
     */
    fun withFlavor(flavorToDimension: Pair<String, String>): FlavoredComponentActionRegistrar<ComponentT>

    /**
     * Registers an [Action] for [ComponentT] objects with a given (dimension, flavorName).
     *
     * @param flavorToDimension Dimension and flavor to filter [ComponentT] on.
     * @param action [Action] to run on filtered [ComponentT].
     */
    fun withFlavor(flavorToDimension: Pair<String, String>, action: Action<ComponentT>)

    /**
     * Registers an action for [ComponentT] objects with a given (dimension, flavorName).
     *
     * @param flavorToDimension Dimension and flavor to filter [ComponentT] on.
     * @param action Lambda function to run on filtered [ComponentT].
     */
    fun withFlavor(flavorToDimension: Pair<String, String>, action: ComponentT.() -> Unit)

    /**
     * Registers an [Action] for [ComponentT] objects with a given name pattern.
     *
     * @param pattern [Pattern] to apply on the [org.gradle.api.Named.getName] to filter [ComponentT]
     * instances on
     * @param action [Action] to run on filtered [ComponentT].
     */
    fun withName(pattern: Pattern, action: Action<ComponentT>)

    /**
     * Registers an [Action] for [ComponentT] objects with a given name.
     *
     * @param name Name to filter [ComponentT] on.
     * @param action [Action] to run on filtered [ComponentT].
     */
    fun withName(name: String, action: Action<ComponentT>)

    /**
     * Registers an action for [ComponentT] objects with a given name.
     *
     * @param name Name to filter [ComponentT] on.
     * @param action Lambda function to run on filtered [ComponentT].
     */
    fun withName(name: String, action: ComponentT.() -> Unit)
}
