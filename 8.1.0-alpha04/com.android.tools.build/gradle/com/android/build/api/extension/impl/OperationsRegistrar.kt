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

import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.VariantSelector
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import org.gradle.api.Action
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

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

    fun addOperation(
        callback: Action<Component>,
        selector: VariantSelector = noSelector
    ) {
        if (actionsExecuted.get()) {
            throw RuntimeException(
                """
                It is too late to add actions as the callbacks already executed.
                Did you try to call beforeVariants or onVariants from the old variant API
                'applicationVariants' for instance ? you should always call beforeVariants or
                onVariants directly from the androidComponents DSL block.
                """
            )
        }
        operations.add(Operation(selector as VariantSelectorImpl, callback))
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

class VariantOperationsRegistrar<Component: ComponentIdentity> : OperationsRegistrar<Component>() {

    fun executeOperations(userVisibleVariant: Component, internalVariant: ComponentCreationConfig) {
        super.executeOperations(userVisibleVariant)
        wireAllFinalizedBy(internalVariant)
    }

    private fun wireAllFinalizedBy(variant: ComponentCreationConfig) {
        InternalArtifactType::class.sealedSubclasses.forEach { kClass ->
            handleFinalizedByForType(variant, kClass)
        }
    }

    private fun handleFinalizedByForType(variant: ComponentCreationConfig, type: KClass<out InternalArtifactType<*>>) {
        type.objectInstance?.let { artifact ->
            artifact.finalizingArtifact?.forEach { artifactFinalizedBy ->
                val artifactContainer = when(artifactFinalizedBy) {
                    is SingleArtifact -> variant.artifacts.getArtifactContainer(artifact)
                    is MultipleArtifact -> variant.artifacts.getArtifactContainer(artifact)
                    else -> throw RuntimeException("Unhandled artifact type : $artifactFinalizedBy")
                }
                artifactContainer.getTaskProviders().forEach { taskProvider ->
                    taskProvider.configure {
                        it.finalizedBy(variant.artifacts.get(artifact))
                    }
                }
            }
        }
    }
}
