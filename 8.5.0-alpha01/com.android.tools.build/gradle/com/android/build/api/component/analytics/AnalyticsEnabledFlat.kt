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

package com.android.build.api.component.analytics

import com.android.build.api.variant.SourceDirectories
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.file.Directory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import javax.inject.Inject

open class AnalyticsEnabledFlat @Inject constructor(
    override val delegate: SourceDirectories.Flat,
    stats: GradleBuildVariant.Builder,
    objectFactory: ObjectFactory,
):
    AnalyticsEnabledSourceDirectories(delegate, stats, objectFactory),
    SourceDirectories.Flat
{

    override val all: Provider<out Collection<Directory>>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SOURCES_DIRECTORIES_GET_ALL_VALUE
            return delegate.all
        }

    override val static: Provider<out Collection<Directory>>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SOURCES_DIRECTORIES_GET_STATIC_VALUE
            return delegate.static
        }
}
