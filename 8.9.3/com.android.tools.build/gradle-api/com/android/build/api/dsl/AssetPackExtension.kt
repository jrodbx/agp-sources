/*
 * Copyright (C) 2019 The Android Open Source Project
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

import org.gradle.api.provider.Property

/**
 * Extension properties for the Asset Pack plugin.
 */
interface AssetPackExtension {
    /**
     * The split name to assign to the asset pack.
     */
    val packName: Property<String>
    /**
     * Contains the dynamic delivery settings for the asset pack.
     */
    val dynamicDelivery: DynamicDelivery
    /** @see dynamicDelivery */
    fun dynamicDelivery(action: DynamicDelivery.() -> Unit)
}
