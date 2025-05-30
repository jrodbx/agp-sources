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

package com.android.build.gradle.internal.services

import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.util.concurrent.ConcurrentHashMap

/**
 * *Global* [BuildService] (see [GlobalServiceRegistrationAction]), which allows running an action
 * only once per build.
 */
interface RunOnceBuildService {

    /**
     * Runs the action with the given [name] and [scope] only once (i.e., if the same action was
     * already performed before, calling this method will be a no-op).
     *
     * The [scope] is used to prevent two unrelated actions having the same name by accident. It is
     * usually the fully qualified name of the containing class of the action.
     */
    fun runOnce(name: String, scope: String, action: () -> Unit) {
        if (getOrSetActionPerformed(name, scope)) return
        action()
    }

    /**
     * Returns `true` if the action with the given [name] and [scope] has been performed. Otherwise,
     * set the action as performed, and return `false` (subsequent calls to this method for that
     * action will return `true`).
     *
     * The [scope] is used to prevent two unrelated actions having the same name by accident. It is
     * usually the fully qualified name of the containing class of the action.
     */
    fun getOrSetActionPerformed(name: String, scope: String): Boolean
}

/**
 * *Global* [BuildService] (see [GlobalServiceRegistrationAction]), which allows running an action
 * only once per build.
 */
abstract class RunOnceBuildServiceImpl : BuildService<BuildServiceParameters.None>, RunOnceBuildService {

    private data class ActionName(val name: String, val scope: String)

    private val performedActions = ConcurrentHashMap.newKeySet<ActionName>()

    override fun getOrSetActionPerformed(name: String, scope: String): Boolean {
        return !performedActions.add(ActionName(name, scope))
    }

    class RegistrationAction(project: Project)
        : GlobalServiceRegistrationAction<RunOnceBuildService, RunOnceBuildServiceImpl, BuildServiceParameters.None>(
            project, RunOnceBuildService::class.java, RunOnceBuildServiceImpl::class.java) {

        override fun configure(parameters: BuildServiceParameters.None) {
            // Do nothing
        }
    }

}
