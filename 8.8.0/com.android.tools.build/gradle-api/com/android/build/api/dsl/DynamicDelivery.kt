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

/** DSL object to configure dynamic delivery of an asset pack. */
interface DynamicDelivery {
    /**
     * Identifies the delivery type {install-time, fast-follow, on-demand}
     * when the asset pack is used with a persistent app.
     */
    val deliveryType: Property<String>
    /**
     * Identifies the delivery type {on-demand}
     * when the asset pack is used with an instant app.
     */
    val instantDeliveryType: Property<String>
}
