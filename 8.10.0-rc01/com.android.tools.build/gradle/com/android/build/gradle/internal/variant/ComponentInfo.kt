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

package com.android.build.gradle.internal.variant

import com.android.build.api.variant.ComponentBuilder
import com.android.build.api.variant.VariantBuilder
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.core.dsl.VariantDslInfo
import com.google.wireless.android.sdk.stats.GradleBuildVariant

open class ComponentInfo<
        ComponentBuilderT : ComponentBuilder,
        ComponentT : ComponentCreationConfig>(
    val variantBuilder: ComponentBuilderT,
    val variant: ComponentT,
    val stats: GradleBuildVariant.Builder?
)

class VariantComponentInfo<
        VariantBuilderT : VariantBuilder,
        VariantDslInfoT: VariantDslInfo,
        VariantT : VariantCreationConfig> (
    variantBuilder: VariantBuilderT,
    variant: VariantT,
    stats: GradleBuildVariant.Builder?,
    val variantDslInfo: VariantDslInfoT
) : ComponentInfo<VariantBuilderT, VariantT>(variantBuilder, variant, stats)
