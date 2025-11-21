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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilationBuilder
import com.android.build.api.variant.impl.KmpAndroidCompilationType
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.plugin.hierarchy.KotlinSourceSetTreeClassifier

internal class KotlinMultiplatformAndroidCompilationBuilderImpl(
    private val compilationType: KmpAndroidCompilationType
): KotlinMultiplatformAndroidCompilationBuilder {
    override var compilationName = compilationType.defaultCompilationName
    override var defaultSourceSetName = compilationType.defaultSourceSetName
    override var sourceSetTreeName = compilationType.defaultSourceSetTreeName

    @OptIn(ExternalKotlinTargetApi::class)
    internal fun getSourceSetTreeClassifier(): KotlinSourceSetTreeClassifier {
        return when (compilationType) {
            KmpAndroidCompilationType.MAIN -> KotlinSourceSetTreeClassifier.Default

            KmpAndroidCompilationType.TEST_ON_JVM -> sourceSetTreeName?.let {
                KotlinSourceSetTreeClassifier.Name(it)
            } ?: KotlinSourceSetTreeClassifier.Value(KotlinSourceSetTree.test)

            KmpAndroidCompilationType.TEST_ON_DEVICE -> sourceSetTreeName?.let {
                KotlinSourceSetTreeClassifier.Name(it)
            } ?: KotlinSourceSetTreeClassifier.None
        }
    }
}
