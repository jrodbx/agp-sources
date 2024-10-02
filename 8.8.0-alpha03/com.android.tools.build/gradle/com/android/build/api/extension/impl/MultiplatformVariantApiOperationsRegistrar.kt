/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.build.api.dsl.KotlinMultiplatformAndroidExtension
import com.android.build.api.variant.KotlinMultiplatformAndroidVariant
import org.gradle.api.Action
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Holder of various [KotlinMultiplatformOperationsRegistrar] for all the
 * variant API related operations in KMP Android plugin
 */
class MultiplatformVariantApiOperationsRegistrar(
    extension: KotlinMultiplatformAndroidExtension,
) : DslLifecycleComponentsOperationsRegistrar<KotlinMultiplatformAndroidExtension>(extension) {

    internal val variantOperations = KotlinMultiplatformOperationsRegistrar()
}

/**
 * Registrar object to keep track of Variant API operations registered on the [KotlinMultiplatformAndroidVariant]
 */
open class KotlinMultiplatformOperationsRegistrar {

    private class Operation(
        val callBack: Action<KotlinMultiplatformAndroidVariant>
    )

    private val operations = mutableListOf<Operation>()
    private val actionsExecuted = AtomicBoolean(false)

    fun addOperation(
        callback: Action<KotlinMultiplatformAndroidVariant>
    ) {
        if (actionsExecuted.get()) {
            throw RuntimeException(
                """
                It is too late to add actions as the callbacks already executed.
                Make sure to call onVariant directly from the androidComponents DSL block.
                """
            )
        }
        operations.add(Operation(callback))
    }

    fun executeOperations(variant: KotlinMultiplatformAndroidVariant) {
        actionsExecuted.set(true)
        operations.forEach { operation ->
            operation.callBack.execute(variant)
        }
    }
}
