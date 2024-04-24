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
 * Shared properties between DSL objects that contribute to a separate-test-project variant.
 *
 * That is, [TestBuildType] and [TestProductFlavor] and [TestDefaultConfig].
 */
interface TestVariantDimension :
    VariantDimension {
    /**
     * Returns whether multi-dex is enabled.
     *
     * This can be null if the flag is not set, in which case the default value is used.
     */
    var multiDexEnabled: Boolean?

    /** The associated signing config or null if none are set on the variant dimension. */
    var signingConfig: ApkSigningConfig?
}
