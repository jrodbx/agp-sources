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

import com.android.ide.common.workers.GradlePluginMBeans
import org.gradle.api.model.ObjectFactory
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.io.Serializable
import javax.inject.Inject

/**
 * Adapted version of the Gradle's [WorkAction] for handling BuiltArtifact instances.
 *
 *
 */
open class WorkActionAdapter<TargetTypeT, WorkItemParametersT> @Inject constructor(
    val objectFactory: ObjectFactory,
    val params: WorkItemParametersT)
    : WorkAction<WorkItemParametersT>
        where TargetTypeT: WorkParameters, TargetTypeT: Serializable,
              WorkItemParametersT : WorkActionAdapter.AdaptedWorkParameters<TargetTypeT>, WorkItemParametersT: Serializable {

    open class AdaptedWorkParameters<WorkParametersT>: WorkParameters, Serializable
            where WorkParametersT : WorkParameters, WorkParametersT: Serializable {
        var projectName: String = ""
        var tastName: String = ""
        var workerKey: String = ""
        lateinit var adaptedParameters: WorkParametersT
        lateinit var adaptedAction: String
    }

    override fun getParameters(): WorkItemParametersT {
        return params
    }

    override fun execute() {
        if (params.projectName.isNotBlank()) {
            GradlePluginMBeans.getProfileMBean(params.projectName)
                ?.workerStarted(params.tastName, params.workerKey)
        }
        @Suppress("UNCHECKED_CAST")
        val actionType = parameters.adaptedParameters.javaClass.classLoader.loadClass(parameters.adaptedAction) as Class<out WorkAction<*>>
        val workAction = objectFactory.newInstance(actionType, parameters.adaptedParameters)
        workAction.execute()
        if (params.projectName.isNotBlank()) {
            GradlePluginMBeans.getProfileMBean(params.projectName)
                ?.workerFinished(params.tastName, params.workerKey)
        }
    }
}