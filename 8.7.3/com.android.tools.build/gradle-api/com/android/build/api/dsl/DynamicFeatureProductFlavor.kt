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

package com.android.build.api.dsl

/**
 * Encapsulates all product flavors properties for dynamic feature projects.
 *
 * Dynamic features must have exactly the same product flavors (name and dimensions) as the app that
 * includes them, however settings can be different between the application and the dynamic feature.
 * Properties on dynamic feature product flavors fall in to three categories.
 *
 * * Properties global to the application that affect the build flow, and so must be explicitly set
 *   in the dynamic feature.
 *   For example, the flavor names and dimensions must match the application that includes this
 *   dynamic feature.
 * * Properties global to the application that do not affect the build flow. These are set in the
 *   `com.android.application` project, and are automatically configured on the dynamic feature,
 *   they cannot be set on the dynamic feature.
 *   For example, application ID suffix and signing cannot be configured on the dynamic feature and
 *   are not present on this interface.
 * * Properties that can vary between the app and the dynamic feature.
 *   For example, `resValues` can be used independently from the app in a dynamic feature.
 *
 * See [ApplicationProductFlavor]
 */
interface DynamicFeatureProductFlavor :
    DynamicFeatureBaseFlavor,
    ProductFlavor
