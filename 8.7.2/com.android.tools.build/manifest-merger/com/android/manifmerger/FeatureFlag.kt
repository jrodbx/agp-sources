/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.manifmerger

import com.android.SdkConstants

/**
 * Represents a feature flag, used to conditionally enable or disable features within an Android project.
 *
 * A FeatureFlag consists of:
 * - `name`: The name of the feature flag (e.g., "new_ui", "experimental_feature").
 * - `value`: A boolean indicating whether the feature is enabled (`true`) or disabled (`false`).
 *
 * The `attributeValue` property provides the formatted string representation suitable for use in
 * XML attributes (e.g., "new_ui" for enabled, "!new_ui" for disabled).
 *
 * ```
 */
data class FeatureFlag(val name: String, val value: Boolean) {
    companion object {
        const val NAMESPACE_URI: String = SdkConstants.ANDROID_URI
        const val ATTRIBUTE_NAME: String = "featureFlag"
        const val QUALIFIED_ATTRIBUTE_NAME: String = "${SdkConstants.ANDROID_NS_NAME}:featureFlag"
        private const val NEGATION_PREFIX: String = "!"

        fun from(value: String): FeatureFlag =
            if (value.startsWith(NEGATION_PREFIX)) {
                FeatureFlag(value.substring(1), false)
            } else {
                FeatureFlag(value, true)
            }
    }
    val attributeValue = if (value) name else "!$name"
}
