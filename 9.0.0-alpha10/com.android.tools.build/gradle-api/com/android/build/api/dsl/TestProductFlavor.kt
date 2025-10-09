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
 * Encapsulates all product flavors properties for test projects.
 *
 * Test projects have a target application project that they depend on and flavor matching works in
 * the same way as library dependencies. Therefore, to test multiple flavors of an application,
 * you can declare corresponding product flavors here. If you want to use some, you can use
 * [missingDimensionStrategy] to resolve any conflicts.
 *
 * See [ApplicationProductFlavor]
 */
interface TestProductFlavor :
    TestBaseFlavor,
    ProductFlavor
