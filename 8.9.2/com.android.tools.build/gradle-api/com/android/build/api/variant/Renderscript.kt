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

import org.gradle.api.provider.Property

/**
 * Build-time properties for renderscript inside a [Component].
 *
 * This is accessed via [GeneratesApk.renderscript] or [LibraryVariant.renderscript]
 */
interface Renderscript {
    /** Returns the renderscript support mode.  */
    val supportModeEnabled: Property<Boolean>

    /** Returns the renderscript BLAS support mode.  */
    val supportModeBlasEnabled: Property<Boolean>

    /** Returns the renderscript NDK mode.  */
    val ndkModeEnabled: Property<Boolean>

    val optimLevel: Property<Int>
}
