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

import com.android.utils.appendCapitalized
import org.gradle.api.NamedDomainObjectFactory
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.external.ExternalKotlinCompilationDescriptor
import org.jetbrains.kotlin.gradle.plugin.mpp.external.createCompilation

@OptIn(ExternalKotlinTargetApi::class)
class KotlinMultiplatformAndroidCompilationFactory(
    private val target: KotlinMultiplatformAndroidTargetImpl,
    private val kotlinExtension: KotlinMultiplatformExtension
): NamedDomainObjectFactory<KotlinMultiplatformAndroidCompilation> {

    override fun create(name: String): KotlinMultiplatformAndroidCompilationImpl {
        if (!KmpPredefinedAndroidCompilation.values().any { it.compilationName == name }) {
            throw IllegalAccessException(
                "Kotlin multiplatform android plugin doesn't support creating arbitrary " +
                        "compilations. Only three types of compilations are supported:\n" +
                        "  * main compilation (named \"${KmpPredefinedAndroidCompilation.MAIN.compilationName}\"),\n" +
                        "  * unit test compilation (named \"${KmpPredefinedAndroidCompilation.UNIT_TEST.compilationName}\"),\n" +
                        "  * instrumented test compilation (named \"${KmpPredefinedAndroidCompilation.INSTRUMENTED_TEST.compilationName}\")."
            )
        }

        return target.createCompilation {
            compilationName = name
            defaultSourceSet = kotlinExtension.sourceSets.getByName(
                target.targetName.appendCapitalized(name)
            )
            decoratedKotlinCompilationFactory =
                ExternalKotlinCompilationDescriptor.DecoratedKotlinCompilationFactory(
                    ::KotlinMultiplatformAndroidCompilationImpl
                )
            compileTaskName = "compile".appendCapitalized(
                target.targetName.appendCapitalized(name)
            )
        }
    }
}
