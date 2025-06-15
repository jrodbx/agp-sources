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

package com.android.build.gradle.internal.publishing

import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.VerificationType

/**
 * Indicates that an artifact should be published/consumed with [categoryName] as the category
 * attribute, and using [secondaryAttribute] to distinguish between the different artifacts in the
 * same category.
 */
enum class ArtifactCategory(
    val categoryName: String,
    val secondaryAttribute: Attribute<out Named>
) {
    VERIFICATION(Category.VERIFICATION, VerificationType.VERIFICATION_TYPE_ATTRIBUTE),
}
