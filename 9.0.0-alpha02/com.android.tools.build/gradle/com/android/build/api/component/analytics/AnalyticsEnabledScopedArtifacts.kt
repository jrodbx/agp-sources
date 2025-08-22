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
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.ScopedArtifactsOperation
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Task
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import javax.inject.Inject

open class AnalyticsEnabledScopedArtifacts @Inject constructor(
    private val delegate: ScopedArtifacts,
    val stats: GradleBuildVariant.Builder,
    val objectFactory: ObjectFactory,
): ScopedArtifacts {

    @Suppress("UNCHECKED_CAST")
    override fun <T : Task> use(taskProvider: TaskProvider<T>): ScopedArtifactsOperation<T> =
        // no need to record this usage, we will record one of the method used within that
        // interface
        objectFactory.newInstance(
            AnalyticsEnabledScopedArtifactsOperation::class.java,
            delegate.use(taskProvider),
            stats,
            objectFactory,
        ) as ScopedArtifactsOperation<T>
}
