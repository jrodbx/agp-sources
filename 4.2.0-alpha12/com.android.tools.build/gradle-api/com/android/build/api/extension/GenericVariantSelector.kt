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
package com.android.build.api.extension

import com.android.build.api.component.ActionableComponentObject
import com.android.build.api.component.ComponentIdentity
import org.gradle.api.Incubating

/**
 * Selector interface to reduce the number of variants that are of interests when calling any of the
 * variant API like [AndroidComponentsExtension.beforeVariants].
 */
@Incubating
interface GenericVariantSelector<ComponentT> :
    FilteredVariantSelector<ComponentT>
    where ComponentT: ComponentIdentity {

    /**
     * Creates a [VariantSelector] of [ComponentT]that includes all the variants for the current
     * module.
     *
     * @return a [VariantSelector] for all variants.
     */
    fun all(): VariantSelector<ComponentT>

    /**
     * Creates a [VariantSelector] of [NewTypeT], including all variants that are a sub type of
     * [NewTypeT], discarding all others.
     *
     * @param newType the sub type of [ComponentT] of interest.
     */
    fun <NewTypeT: ComponentT> withType(newType: Class<NewTypeT>): FilteredVariantSelector<NewTypeT>
}
