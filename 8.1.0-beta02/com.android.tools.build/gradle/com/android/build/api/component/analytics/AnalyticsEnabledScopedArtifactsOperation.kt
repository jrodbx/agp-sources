/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifactsOperation
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

open class AnalyticsEnabledScopedArtifactsOperation<T: Task> @Inject constructor(
    val delegate: ScopedArtifactsOperation<T>,
    val stats: GradleBuildVariant.Builder,
    val objectFactory: ObjectFactory,
): ScopedArtifactsOperation<T> {

    override fun toAppend(to: ScopedArtifact, with: (T) -> Property<out FileSystemLocation>) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.SCOPED_ARTIFACTS_APPEND_VALUE
        delegate.toAppend(to, with)
    }

    override fun toGet(
        type: ScopedArtifact,
        inputJars: (T) -> ListProperty<RegularFile>,
        inputDirectories: (T) -> ListProperty<Directory>
    ) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.SCOPED_ARTIFACTS_TO_GET_VALUE
        delegate.toGet(type, inputJars, inputDirectories)
    }

    override fun toTransform(
        type: ScopedArtifact,
        inputJars: (T) -> ListProperty<RegularFile>,
        inputDirectories: (T) -> ListProperty<Directory>,
        into: (T) -> RegularFileProperty
    ) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.SCOPED_ARTIFACTS_TO_TRANSFORM_VALUE
        delegate.toTransform(type, inputJars, inputDirectories, into)
    }

    override fun toReplace(type: ScopedArtifact, into: (T) -> RegularFileProperty) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.SCOPED_ARTIFACTS_TO_REPLACE_VALUE
        delegate.toReplace(type, into)
    }
}
