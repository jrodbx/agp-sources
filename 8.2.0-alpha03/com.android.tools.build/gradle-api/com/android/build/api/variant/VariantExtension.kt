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

package com.android.build.api.variant

/**
 * Marker type for [Variant] extension objects.
 *
 * Variant extension must be registered using the
 * [AndroidComponentsExtension.registerExtension] API and can be
 * retrieved from a [Variant] instance using the [Variant.getExtension] API.
 *
 * Since this type will most likely be used as [org.gradle.api.Task]'s input, your subtype
 * should also extend [java.io.Serializable]
 */
interface VariantExtension
