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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.dx.command.dexer.Main
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.util.concurrent.atomic.AtomicBoolean

/** A very simple build service used to clean DX (deprecated dexer) state at the end of the build. */
abstract class DxStateBuildService : BuildService<BuildServiceParameters.None>, AutoCloseable {

    private val shouldClearInternTables = AtomicBoolean(false)

    fun clearStateAfterBuild() {
        shouldClearInternTables.set(true)
    }

    override fun close() {
        if (shouldClearInternTables.get()) {
            Main.clearInternTables()
        }
    }

    class RegistrationAction(project: Project) :
        ServiceRegistrationAction<DxStateBuildService, BuildServiceParameters.None>(
            project,
            DxStateBuildService::class.java
        ) {
        override fun configure(parameters: BuildServiceParameters.None) {
            // do nothing
        }
    }
}