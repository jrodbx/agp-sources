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
 * The filters acts on the properties of [ComponentIdentity], and on the type of [ComponentT]
 *
 * Calls can be chained to include more than one filters, though in some cases, selecting a
 * particular filter can reduce the list of available filters in the chain.
 *
 * This extends [FilteredComponentActionRegistrar] to allow filtering per type.
 */
@Incubating
interface GenericFilteredComponentActionRegistrar<ComponentT> :
    FilteredComponentActionRegistrar<ComponentT>
        where ComponentT: ActionableComponentObject,
              ComponentT: ComponentIdentity {

    fun <NewTypeT: ComponentT> withType(newType: Class<NewTypeT>): FilteredComponentActionRegistrar<NewTypeT>

    fun <NewTypeT: ComponentT> withType(newType: Class<NewTypeT>, action: NewTypeT.() -> Unit)

    fun <NewTypeT: ComponentT> withType(newType: Class<NewTypeT>, action: Action<NewTypeT>)
}
