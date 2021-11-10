/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.projectmodel

import com.android.resources.ResourceType

/**
 * Metadata for a dynamic resource, which is a resource whose value is provided or overridden by the build system.
 */
data class DynamicResourceValue(
        /**
         * Type of the Android resource.
         */
        val type: ResourceType,
        /**
         * Value of the resource, as a String.
         */
        val value: String
)
