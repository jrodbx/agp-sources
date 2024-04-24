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

import com.android.build.gradle.internal.dependency.AndroidAttributes
import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Category

fun AndroidArtifacts.ArtifactType.getAttributes(
    named: (Class<out Named>, String) -> Named
) = AndroidAttributes(
    namedAttributes = category?.let { category ->
        mapOf(
            Category.CATEGORY_ATTRIBUTE to named(Category::class.java, category.categoryName),
            category.secondaryAttribute to named(category.secondaryAttribute.type, type)
        )
    } ?: emptyMap()
)

fun AndroidArtifacts.ArtifactType.getAttributes(
    namedAttributes: Map<Attribute<out Named>, Named>?,
    named: (Class<out Named>, String) -> Named
) = AndroidAttributes(
    namedAttributes = (namedAttributes ?: emptyMap()) + (category?.let { category ->
        mapOf(
            Category.CATEGORY_ATTRIBUTE to named(Category::class.java, category.categoryName),
            category.secondaryAttribute to named(category.secondaryAttribute.type, type)
        )
    } ?: emptyMap())
)
