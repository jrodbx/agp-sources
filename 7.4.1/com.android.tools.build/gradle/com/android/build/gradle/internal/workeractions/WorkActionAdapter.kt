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

package com.android.build.gradle.internal.workeractions

import org.gradle.workers.WorkAction
import java.io.Serializable

/**
 * Adapted version of the Gradle's [WorkAction] for handling BuiltArtifact instances.
 *
 * This subclass of [WorkAction] receives a subclass of [DecoratedWorkParameters] as parameters
 * to the work item.
 *
 * Subclasses must implement the [doExecute] method where the can access the parameters through the
 * [WorkAction.getParameters] method.
 */
interface WorkActionAdapter<WorkItemParametersT>
    : WorkAction<WorkItemParametersT>, Serializable
        where WorkItemParametersT : DecoratedWorkParameters {

    @JvmDefault
    override fun execute() {
        parameters.analyticsService.get()
            .workerStarted(parameters.taskPath.get(), parameters.workerKey.get())
        doExecute()
        parameters.analyticsService.get()
            .workerFinished(parameters.taskPath.get(), parameters.workerKey.get())
    }

    /**
     * Actual implementation of the [WorkAction]
     */
    fun doExecute()
}