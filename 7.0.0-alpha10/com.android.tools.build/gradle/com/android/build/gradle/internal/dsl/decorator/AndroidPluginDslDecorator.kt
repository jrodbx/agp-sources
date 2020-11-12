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

package com.android.build.gradle.internal.dsl.decorator

/** The list of all the supported property types for the production AGP */
val AGP_SUPPORTED_PROPERTY_TYPES: List<SupportedPropertyType> = listOf(
    SupportedPropertyType.Var.String,
    SupportedPropertyType.Collection.List,
    SupportedPropertyType.Collection.Set,
)

/**
 * The DSL decorator in this classloader in AGP.
 *
 * This is a static field, rather than a build service as it shares its lifetime with
 * the classloader that AGP is loaded in.
 */
val androidPluginDslDecorator = DslDecorator(AGP_SUPPORTED_PROPERTY_TYPES)
