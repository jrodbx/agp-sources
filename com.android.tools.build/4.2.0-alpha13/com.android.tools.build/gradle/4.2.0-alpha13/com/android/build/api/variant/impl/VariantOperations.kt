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

package com.android.build.api.variant.impl

import com.android.build.api.component.ActionableComponentObject
import com.android.build.api.component.ComponentIdentity
import com.android.build.api.component.impl.FilteredComponentAction
import org.gradle.api.Action
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Contains a list of registered [Action] on an instance of [VariantObjectT] plus services like
 * executing these actions.
 *
 * @param VariantObjectT is either a [com.android.build.api.variant.VariantBuilder] or
 * [com.android.build.api.variant.Variant]
 */
class VariantOperations<VariantObjectT> where VariantObjectT: ActionableComponentObject, VariantObjectT: ComponentIdentity {
    private val actions = mutableListOf<Action<VariantObjectT>>()
    private val actionsExecuted = AtomicBoolean(false)

    @Throws(RuntimeException::class)
    fun addAction(action: Action<VariantObjectT>) {
        if (actionsExecuted.get()) {
            throw RuntimeException("""
                It is too late to add actions as the callbacks already executed.
                Did you try to call onVariants or onVariantProperties from the old variant API
                'applicationVariants' for instance ? you should always call onVariants or
                onVariantProperties directly from the android DSL block.
                """)
        }
        actions.add(action)
    }

    fun addFilteredAction(action: FilteredComponentAction<out VariantObjectT>) {
        @Suppress("UNCHECKED_CAST")
        actions.add(Action { variant ->
            if (action.specificType.isInstance(variant)) {
                val castedVariant = action.specificType.cast(variant)
                (action as FilteredComponentAction<VariantObjectT>).executeFor(castedVariant)
            }
        })
    }

    fun executeActions(variant: VariantObjectT) {
        actionsExecuted.set(true)
        actions.forEach { action -> action.execute(variant) }
    }
}
