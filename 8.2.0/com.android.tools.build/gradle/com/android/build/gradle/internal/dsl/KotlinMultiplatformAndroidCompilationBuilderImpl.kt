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
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetHierarchy
import org.jetbrains.kotlin.gradle.plugin.mpp.targetHierarchy.SourceSetTreeClassifier

internal class KotlinMultiplatformAndroidCompilationBuilderImpl(
    private val compilationType: KmpAndroidCompilationType
): KotlinMultiplatformAndroidCompilationBuilder {
    override var compilationName = compilationType.defaultCompilationName
    override var defaultSourceSetName = compilationType.defaultSourceSetName
    override var sourceSetTreeName = compilationType.defaultSourceSetTreeName

    @OptIn(ExternalKotlinTargetApi::class)
    internal fun getSourceSetTreeClassifier(): SourceSetTreeClassifier {
        return when (compilationType) {
            KmpAndroidCompilationType.MAIN -> SourceSetTreeClassifier.Default

            KmpAndroidCompilationType.TEST_ON_JVM -> sourceSetTreeName?.let {
                SourceSetTreeClassifier.Name(it)
            } ?: SourceSetTreeClassifier.Value(KotlinTargetHierarchy.SourceSetTree.test)

            KmpAndroidCompilationType.TEST_ON_DEVICE -> sourceSetTreeName?.let {
                SourceSetTreeClassifier.Name(it)
            } ?: SourceSetTreeClassifier.None
        }
    }
}
