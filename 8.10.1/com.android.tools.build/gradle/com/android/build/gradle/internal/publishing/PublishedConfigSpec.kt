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

import org.gradle.api.component.SoftwareComponent

/**
 * Data wrapper class contains all the information about a configuration created for publishing.
 *
 * @param configType Configuration type for published artifacts.
 * @param componentName [SoftwareComponent] this configuration is added to for maven publishing.
 * @param isClassifierRequired Whether the artifact of this configuration needs classifier
 * for maven publishing.
 *
 * Note if [configType] is not a publicationConfig, [componentName] and [isClassifierRequired] don't
 * apply and are set to default values.
 */
data class PublishedConfigSpec @JvmOverloads constructor(
    val configType: AndroidArtifacts.PublishedConfigType,
    val componentName: String? = null,
    val isClassifierRequired: Boolean = false
) {
    constructor(configType: AndroidArtifacts.PublishedConfigType, component: ComponentPublishingInfo)
            : this(configType, component.componentName, component.isClassifierRequired)
}
