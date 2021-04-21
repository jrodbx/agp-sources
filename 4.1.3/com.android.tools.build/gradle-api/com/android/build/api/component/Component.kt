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

package com.android.build.api.component

import com.android.build.api.variant.VariantProperties
import org.gradle.api.Action
import org.gradle.api.Incubating

/**
 * Component object that contains properties that must be set during configuration time as it
 * changes the build flow for the variant.
 *
 * @param PropertiesT the [ComponentProperties] type associated with this [Component]
 */
@Incubating
interface Component<PropertiesT : ComponentProperties>: ComponentIdentity,
    ActionableComponentObject {

    /**
     * Set to True if the variant is active and should be configured, false otherwise.
     */
    var enabled: Boolean

    /**
     * Runs the [Action] block on the [VariantProperties] object once created.
     */
    fun onProperties(action: PropertiesT.() -> Unit)

}