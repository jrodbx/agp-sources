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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.Task
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal

/** Common interface for tasks that use [AnalyticsService]. */
interface UsesAnalytics {

    @get:Internal
    val analyticsService: Property<AnalyticsService>

    object ConfigureAction {

        fun <TaskT> configure(task: TaskT) where TaskT: Task, TaskT: UsesAnalytics {
            getBuildService<AnalyticsService, AnalyticsService.Params>(task.project.gradle.sharedServices).let {
                task.analyticsService.setDisallowChanges(it)
                task.usesService(it)
            }
        }
    }
}
