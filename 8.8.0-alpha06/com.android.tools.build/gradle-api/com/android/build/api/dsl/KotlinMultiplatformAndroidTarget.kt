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

package com.android.build.api.dsl

import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

@Incubating
interface KotlinMultiplatformAndroidTarget: KotlinTarget, KotlinMultiplatformAndroidLibraryExtension {
    override val compilations: NamedDomainObjectContainer<KotlinMultiplatformAndroidCompilation>

    /**
     * Enables compilation of java sources.
     *
     * @note This API is experimental and is likely to change.
     */
    @Incubating
    fun withJava()
}

@Incubating
fun KotlinMultiplatformExtension.androidLibrary(
    action: KotlinMultiplatformAndroidTarget.() -> Unit
) {
    (this as ExtensionAware).extensions.findByType(
        KotlinMultiplatformAndroidTarget::class.java
    )?.action() ?: throw IllegalStateException(
        "You need to apply the " +
            "`com.android.kotlin.multiplatform.library` plugin before accessing the android target."
    )
}
