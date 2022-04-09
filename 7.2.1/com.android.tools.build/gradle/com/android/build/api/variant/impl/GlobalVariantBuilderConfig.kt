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

package com.android.build.api.variant.impl

import com.android.build.api.dsl.DependenciesInfo

/**
 * Global Config for VariantBuilder objects.
 *
 * This gives access to a few select objects that are needed by the VariantBuilder
 * API but are not variant specific.
 *
 * IMPORTANT: it must not give access to the whole extension as it is too dangerous. We need to
 * control that is accessible (DSL elements that are global) and what isn't (DSL
 * elements that are configurable per-variant). Giving access directly to the DSL removes this
 * safety net and reduce maintainability in the future when things become configurable per-variant.
 */

interface GlobalVariantBuilderConfig {

    val dependenciesInfo: DependenciesInfo
}
