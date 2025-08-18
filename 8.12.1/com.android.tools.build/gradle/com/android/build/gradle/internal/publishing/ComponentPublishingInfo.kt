/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.publishing

import com.android.build.gradle.internal.dsl.AbstractPublishing

/**
 * A data class wraps publishing info related to a software component.
 */
data class ComponentPublishingInfo(
    val componentName: String,
    val type: AbstractPublishing.Type,
    val attributesConfig: AttributesConfig? = null,
    val isClassifierRequired: Boolean = false,
    val withSourcesJar: Boolean = false,
    val withJavadocJar: Boolean = false
) {

    /**
     * Configs for attributes to be added to the variant.
     */
    data class AttributesConfig(
        val buildType: String?,
        val flavorDimensions: Set<String>
    )
}
