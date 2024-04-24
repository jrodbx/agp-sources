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
 * Model for Unit Test components that contains build-time properties
 *
 * This object is accessible on subtypes of [Variant] that implement [HasUnitTest], via
 * [HasUnitTest.unitTest]. It is also part of [Variant.nestedComponents].
 *
 * The presence of this component in a variant is controlled by [HasUnitTestBuilder.enableUnitTest]
 * which is accessible on subtypes of [VariantBuilder] that implement [HasUnitTestBuilder]
 */
interface UnitTest: TestComponent
