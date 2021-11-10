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

import com.android.build.api.component.impl.ComponentBuilderImpl
import com.android.build.api.component.impl.ComponentImpl
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.variant.impl.VariantBuilderImpl
import com.android.build.api.variant.impl.VariantImpl
import com.google.wireless.android.sdk.stats.GradleBuildVariant

open class ComponentInfo<
        ComponentBuilderT : ComponentBuilderImpl,
        ComponentT : ComponentImpl>(
    val variantBuilder: ComponentBuilderT,
    val variant: ComponentT,
    val stats: GradleBuildVariant.Builder?
)

class VariantComponentInfo<
        VariantBuilderT : VariantBuilderImpl,
        VariantT : VariantImpl>
(
        variantBuilder: VariantBuilderT,
        variant: VariantT,
        stats: GradleBuildVariant.Builder?,
        val variantApiOperationsRegistrar: VariantApiOperationsRegistrar<
                in CommonExtension<*, *, *, *>,
                in VariantBuilderT,
                in VariantT,
                >
) : ComponentInfo<VariantBuilderT, VariantT>(variantBuilder, variant, stats)
