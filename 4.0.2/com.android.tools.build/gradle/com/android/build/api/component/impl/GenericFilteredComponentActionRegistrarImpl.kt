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

import com.android.build.api.component.ActionableComponentObject
import com.android.build.api.component.ComponentIdentity
import com.android.build.api.component.FilteredComponentActionRegistrar
import com.android.build.api.component.GenericFilteredComponentActionRegistrar
import com.android.build.api.variant.impl.VariantOperations
import com.android.build.gradle.internal.api.dsl.DslScope
import org.gradle.api.Action
import javax.inject.Inject

internal open class GenericFilteredComponentActionRegistrarImpl<BaseT> @Inject constructor(
    private val dslScope: DslScope,
    private val operations: VariantOperations<BaseT>,
    baseType: Class<BaseT>
) : FilteredComponentActionRegistrarImpl<BaseT>(dslScope, operations, baseType),
    GenericFilteredComponentActionRegistrar<BaseT>
        where BaseT: ActionableComponentObject,
              BaseT: ComponentIdentity {

    override fun <NewTypeT : BaseT> withType(
        newType: Class<NewTypeT>
    ) : FilteredComponentActionRegistrar<NewTypeT> {
        @Suppress("UNCHECKED_CAST")
        return dslScope.objectFactory.newInstance(
            FilteredComponentActionRegistrarImpl::class.java,
            dslScope,
            operations,
            newType
        ) as FilteredComponentActionRegistrar<NewTypeT>
    }

    override fun <NewTypeT : BaseT> withType(
        newType: Class<NewTypeT>,
        action: NewTypeT.() -> Unit
    ) {
        operations.addFilteredAction(
            FilteredComponentAction(
                specificType = newType,
                action = Action { action(it) }
            )
        )
    }

    override fun <NewTypeT : BaseT> withType(newType: Class<NewTypeT>, action: Action<NewTypeT>) {
        operations.addFilteredAction(
            FilteredComponentAction(
                specificType = newType,
                action = action
            )
        )
    }
}
