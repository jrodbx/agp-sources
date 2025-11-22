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

package com.android.build.api.variant

import org.gradle.api.Incubating

/**
 * Exception thrown when users are trying to access [VariantBuilder] properties.
 *
 * [VariantBuilder] properties are defined as kotlin properties so users can use simple
 * assignment rather than calling a set function. However, those properties can only be
 * set.
 *
 * It is not safe to read the property value as other registered
 * [AndroidComponentsExtension.beforeVariants] callbacks can run later and change the value.
 *
 * If you need the final value of a property, it is most likely available in the [Variant]
 * hierarchy accessible through the [AndroidComponentsExtension.onVariants] callbacks.
 */
@Incubating
class PropertyAccessNotAllowedException(
        val propertyName: String,
        val location: String
): RuntimeException(
        """
            You cannot access '$propertyName' on $location in the [AndroidComponentsExtension.beforeVariants]
            callbacks. Other plugins applied later can still change this value, it is not safe
            to read at this stage.
        """.trimIndent()
)
