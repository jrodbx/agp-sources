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

/**
 * Allows registering Actions on [ComponentT], with the ability to include
 * filters to target sub sets of [ComponentT].
 *
 * This registrar is already filtered on build type and therefore only offers flavors of
 * [ComponentIdentity] as a filter.
 *
 * Calls can be chained to include more than one filters, though in some cases, selecting a
 * particular filter can reduce the list of available filters in the chain.
 */
@Incubating
interface BuildTypedComponentActionRegistrar<ComponentT>
        where ComponentT: ActionableComponentObject, ComponentT: ComponentIdentity {
    /**
     * Filters [ComponentT] instances with a flavor
     *
     * @param flavorToDimension the flavor name to flavor dimension
     * @param action [Action] to perform on each filtered variant.
     */
    fun withFlavor(flavorToDimension: Pair<String, String>, action: Action<ComponentT>)

    /**
     * Filters [ComponentT] instances with a flavor
     *
     * @param flavorToDimension the flavor name to flavor dimension
     * @param action [Action] to perform on each filtered variant.
     */
    fun withFlavor(flavorToDimension: Pair<String, String>, action: ComponentT.() -> Unit)

    /**
     * Filters [ComponentT] instances with a flavor, and return the same filter instance for further product
     * flavor filtering.
     *
     * @param flavorToDimension the flavor name to flavor dimension
     * @param action [Action] to perform on each filtered variant.
     */
    fun withFlavor(flavorToDimension: Pair<String, String>): BuildTypedComponentActionRegistrar<ComponentT>
}
