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

package com.android.build.api.extension.impl

import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.VariantSelector
import org.gradle.api.Action
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Registrar object to keep track of Variant API operations registered on the [Component]
 */
open class OperationsRegistrar<Component: ComponentIdentity> {

    private class Operation<Component : ComponentIdentity>(
        val selector: VariantSelectorImpl,
        val callBack: Action<Component>
    )

    private val operations = mutableListOf<Operation<Component>>()

    private val noSelector = VariantSelectorImpl().all()
    private val actionsExecuted = AtomicBoolean(false)

    /**
     * Add a public callback to the end of the list of operations to be executed. A public callback
     * is a callback defined by a user or a third party plugin, for example.
     *
     * @param callback the callback to be added to the list of operations
     * @param callingFunctionName the name of the function that called this method (useful in case
     *        of an error)
     * @param selector the selector to use to determine which variants to execute the callback on
     */
    fun addPublicOperation(
        callback: Action<Component>,
        callingFunctionName: String,
        selector: VariantSelector = noSelector,
    ) {
        if (actionsExecuted.get()) {
            throw RuntimeException(
                """
                It is too late to add actions as the callbacks already executed.
                Did you try to call $callingFunctionName from the old variant API
                'applicationVariants' for instance? You can instead call $callingFunctionName
                directly from the androidComponents DSL block.
                """.trimIndent()
            )
        }
        operations.add(Operation(selector as VariantSelectorImpl, callback))
    }

    /**
     * Add an internal callback to the *beginning* of the list of operations to be executed, to
     * ensure it's executed before any callbacks added via [addPublicOperation]. An internal
     * callback is a callback defined by AGP.
     *
     * @param callback the callback to be added to the list of operations
     * @param callingFunctionName the name of the function that called this method (useful in case
     *        of an error)
     * @param selector the selector to use to determine which variants to execute the callback on
     */
    fun addInternalOperation(
        callback: Action<Component>,
        callingFunctionName: String,
        selector: VariantSelector = noSelector,
    ) {
        if (actionsExecuted.get()) {
            throw RuntimeException(
                "It is too late to call $callingFunctionName as the callbacks already executed."
            )
        }
        operations.add(0, Operation(selector as VariantSelectorImpl, callback))
    }

    fun executeOperations(userVisibleVariant: Component) {
        actionsExecuted.set(true)
        operations.forEach { operation ->
            if (operation.selector.appliesTo(userVisibleVariant)) {
                operation.callBack.execute(userVisibleVariant)
            }
        }
    }
}
