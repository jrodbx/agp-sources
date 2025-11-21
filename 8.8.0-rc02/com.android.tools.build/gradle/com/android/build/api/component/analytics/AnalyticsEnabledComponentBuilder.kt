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

package com.android.build.api.component.analytics

import com.android.build.api.variant.ComponentBuilder
import com.android.tools.build.gradle.internal.profile.VariantMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant

/**
 * Superclass for all analytics enabled implementations.
 */
abstract class AnalyticsEnabledComponentBuilder(
    open val delegate: ComponentBuilder,
    protected val stats: GradleBuildVariant.Builder
) : ComponentBuilder {

    @Suppress("OverridingDeprecatedMember")
    override var enabled: Boolean
        get() = delegate.enable
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type = VariantMethodType.ENABLED_VALUE
            delegate.enable = value
        }

    override var enable: Boolean
        get() = delegate.enable
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type = VariantMethodType.ENABLED_VALUE
            delegate.enable = value
        }

    override val name: String
        get() = delegate.name

    override val buildType: String?
        get() = delegate.buildType
    override val productFlavors: List<Pair<String, String>>
        get() = delegate.productFlavors
    override val flavorName: String?
        get() = delegate.flavorName
}
