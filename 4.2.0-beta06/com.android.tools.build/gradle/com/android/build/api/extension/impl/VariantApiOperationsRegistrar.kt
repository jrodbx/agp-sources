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

package com.android.build.api.extension.impl

import com.android.build.api.component.AndroidTest
import com.android.build.api.component.AndroidTestBuilder
import com.android.build.api.component.UnitTest
import com.android.build.api.component.UnitTestBuilder
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder

/**
 * Holder of various [OperationsRegistrar] for all the variant API related operations to a plugin.
 */
class VariantApiOperationsRegistrar<VariantBuilderT: VariantBuilder, VariantT: Variant> {
    internal val variantBuilderOperations = OperationsRegistrar<VariantBuilderT>()
    internal val variantOperations = OperationsRegistrar<VariantT>()
    internal val unitTestBuilderOperations = OperationsRegistrar<UnitTestBuilder>()
    internal val androidTestBuilderOperations = OperationsRegistrar<AndroidTestBuilder>()
    internal val unitTestOperations = OperationsRegistrar<UnitTest>()
    internal val androidTestOperations = OperationsRegistrar<AndroidTest>()
}
