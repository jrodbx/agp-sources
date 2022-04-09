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
 * Shared properties between DSL objects that contribute to an application variant.
 *
 * That is, [ApplicationBuildType] and [ApplicationProductFlavor] and [ApplicationDefaultConfig].
 */
interface ApplicationVariantDimension : VariantDimension {
    /**
     * Application id suffix. It is appended to the "base" application id when calculating the final
     * application id for a variant.
     *
     * In case there are product flavor dimensions specified, the final application id suffix
     * will contain the suffix from the default product flavor, followed by the suffix from product
     * flavor of the first dimension, second dimension and so on. All of these will have a dot in
     * between e.g. &quot;defaultSuffix.dimension1Suffix.dimensions2Suffix&quot;.
     */
    var applicationIdSuffix: String?

    /**
     * Version name suffix. It is appended to the "base" version name when calculating the final
     * version name for a variant.
     *
     * In case there are product flavor dimensions specified, the final version name suffix will
     * contain the suffix from the default product flavor, followed by the suffix from product
     * flavor of the first dimension, second dimension and so on.
     */
    var versionNameSuffix: String?

    /**
     * Returns whether multi-dex is enabled.
     *
     * This can be null if the flag is not set, in which case the default value is used.
     */
    var multiDexEnabled: Boolean?

    /** The associated signing config or null if none are set on the variant dimension. */
    var signingConfig: ApkSigningConfig?
}
