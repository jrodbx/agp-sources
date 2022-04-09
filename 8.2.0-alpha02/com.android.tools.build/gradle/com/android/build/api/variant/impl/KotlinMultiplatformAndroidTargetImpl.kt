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

package com.android.build.api.variant.impl

import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtension
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.external.DecoratedExternalKotlinTarget

@OptIn(ExternalKotlinTargetApi::class)
class KotlinMultiplatformAndroidTargetImpl(
    delegate: Delegate,
    kotlinExtension: KotlinMultiplatformExtension,
    private val androidExtension: KotlinMultiplatformAndroidExtension
) : DecoratedExternalKotlinTarget(delegate), KotlinMultiplatformAndroidTarget {

    override val options: KotlinMultiplatformAndroidExtension
        get() = androidExtension

    override val compilations: NamedDomainObjectContainer<KotlinMultiplatformAndroidCompilationImpl> =
        project.objects.domainObjectContainer(
            KotlinMultiplatformAndroidCompilationImpl::class.java,
            KotlinMultiplatformAndroidCompilationFactory(
                this,
                kotlinExtension
            )
        )

    private val compilationOperations = mapOf(
        KmpPredefinedAndroidCompilation.MAIN to mutableListOf<Action<KotlinMultiplatformAndroidCompilation>>(),
        KmpPredefinedAndroidCompilation.TEST to mutableListOf(),
        KmpPredefinedAndroidCompilation.INSTRUMENTED_TEST to mutableListOf(),
    )

    private fun onCompilation(
        type: KmpPredefinedAndroidCompilation,
        action: KotlinMultiplatformAndroidCompilation.() -> Unit
    ) {
        compilations.findByName(type.compilationName)?.let(action) ?:
        compilationOperations[type]!!.add(action)
    }

    override fun onMainCompilation(action: KotlinMultiplatformAndroidCompilation.() -> Unit) {
        onCompilation(KmpPredefinedAndroidCompilation.MAIN, action)
    }

    override fun onUnitTestCompilation(action: KotlinMultiplatformAndroidCompilation.() -> Unit) {
        onCompilation(KmpPredefinedAndroidCompilation.TEST, action)
    }

    override fun onInstrumentedTestCompilation(action: KotlinMultiplatformAndroidCompilation.() -> Unit) {
        onCompilation(KmpPredefinedAndroidCompilation.INSTRUMENTED_TEST, action)
    }

    internal fun executeCompilationOperations() {
        compilationOperations.forEach { (type, actions) ->
            compilations.findByName(type.compilationName)?.let { compilation ->
                actions.forEach {
                    it.execute(compilation)
                }
            }
        }
    }

    override fun options(action: KotlinMultiplatformAndroidExtension.() -> Unit) {
        androidExtension.action()
    }
}
