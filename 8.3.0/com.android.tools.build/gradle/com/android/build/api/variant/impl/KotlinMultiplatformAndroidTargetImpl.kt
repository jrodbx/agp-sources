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

import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilation
import com.android.build.api.dsl.KotlinMultiplatformAndroidExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidTarget
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtensionImpl
import org.gradle.api.NamedDomainObjectContainer
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.external.DecoratedExternalKotlinTarget

@OptIn(ExternalKotlinTargetApi::class)
internal class KotlinMultiplatformAndroidTargetImpl(
    delegate: Delegate,
    kotlinExtension: KotlinMultiplatformExtension,
    androidExtension: KotlinMultiplatformAndroidExtensionImpl
) : DecoratedExternalKotlinTarget(delegate),
    KotlinMultiplatformAndroidTarget,
    KotlinMultiplatformAndroidExtension by androidExtension {

    internal var enableJavaSources = false
        private set

    override val compilations: NamedDomainObjectContainer<KotlinMultiplatformAndroidCompilation> =
        project.objects.domainObjectContainer(
            KotlinMultiplatformAndroidCompilation::class.java,
            KotlinMultiplatformAndroidCompilationFactory(
                project = project,
                target = this,
                kotlinExtension = kotlinExtension,
                androidExtension = androidExtension
            )
        )

    override fun withJava() {
        enableJavaSources = true
    }
}
