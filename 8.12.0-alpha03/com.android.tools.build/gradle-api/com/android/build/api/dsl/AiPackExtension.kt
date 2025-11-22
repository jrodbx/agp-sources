/*
 * Copyright (C) 2024 The Android Open Source Project
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

import org.gradle.api.Incubating
import org.gradle.api.provider.Property

/**
 * Extension properties for the Asset Pack plugin that are specific to AI packs.
 */
@Incubating
interface AiPackExtension {
    /**
     * The split name to assign to the AI pack.
     */
    @get:Incubating
    val packName: Property<String>
    /**
     * Contains the dynamic delivery settings for the AI pack.
     */
    @get:Incubating
    val dynamicDelivery: DynamicDelivery
    /**
     * The AI model which this AI pack depends upon.
     */
    @get:Incubating
    val modelDependency: ModelDependency
    /** @see dynamicDelivery */
    @Incubating
    fun dynamicDelivery(action: DynamicDelivery.() -> Unit)
    /** @see modelDependency */
    @Incubating
    fun modelDependency(action: ModelDependency.() -> Unit)
}
