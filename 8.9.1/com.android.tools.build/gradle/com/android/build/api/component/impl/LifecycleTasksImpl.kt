/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.api.component.impl

import com.android.build.api.variant.LifecycleTasks
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.Task

class LifecycleTasksImpl: LifecycleTasks {

    private enum class LifecycleEvent {
        PRE_BUILD,
    }
    private val registeredDependents = mutableMapOf<LifecycleEvent, MutableList<Any>>()

    internal fun invokePreBuildActions(task: Task) {
        invokeActions(LifecycleEvent.PRE_BUILD, task)
    }

    @VisibleForTesting
    internal fun hasPreBuildActions() = !registeredDependents.get(LifecycleEvent.PRE_BUILD).isNullOrEmpty()

    private fun invokeActions(event: LifecycleEvent, task: Task) {
        registeredDependents.get(event)?.let { taskDependencies ->
            task.dependsOn(*taskDependencies.toTypedArray())
        }
    }

    override fun registerPreBuild(vararg objects: Any) {
        registeredDependents.getOrPut(
            LifecycleEvent.PRE_BUILD
        ) { mutableListOf() }.addAll(objects)
    }
}
