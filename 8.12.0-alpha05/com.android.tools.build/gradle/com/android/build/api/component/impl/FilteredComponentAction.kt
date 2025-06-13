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

package com.android.build.api.component.impl

import com.android.build.api.variant.ComponentIdentity
import org.gradle.api.Action
import java.util.regex.Pattern

/**
 * An [Action] on a [ComponentT], associated with filters.
 *
 * The filters guarantees that [executeFor] only runs the actions on [ComponentT] that match the
 * them.
 *
 * The filters can be based on the type of [ComponentT] and on the properties of
 * [ComponentIdentity].
 */
class FilteredComponentAction<ComponentT>(
    private val buildType: String? = null,
    private val flavors: List<Pair<String, String>> = listOf(),
    private val namePattern: Pattern? = null,
    private val name: String? = null,
    private val action: Action<ComponentT>
) where ComponentT: ComponentIdentity {

    /**
     * executes the action if the component matches the filters.
     */
    fun executeFor(component: ComponentT) {
        if (buildType != null && buildType != component.buildType) {
            return
        }

        val flavorMap = component.productFlavors.groupBy({ it.first }, { it.second })
        flavors.forEach {
            val values = flavorMap[it.first]
            if (values == null || values.size != 1 || values[0] != it.second) {
                return
            }
        }

        if (namePattern != null) {
            if (!namePattern.matcher(component.name).matches()) {
                return
            }
        }

        if (name != null && name != component.name) {
            return
        }

        action.execute(component)
    }
}
