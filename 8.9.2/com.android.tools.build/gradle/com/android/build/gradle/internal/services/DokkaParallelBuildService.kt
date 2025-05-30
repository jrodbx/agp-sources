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

package com.android.build.gradle.internal.services

import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * [DokkaParallelBuildService] is to limit the number of workers in Javadoc generation task to one
 * in order to avoid thread safety issue in dokka-core. See https://github.com/Kotlin/dokka/issues/2308
 */
abstract class DokkaParallelBuildService : BuildService<BuildServiceParameters.None> {
    class RegistrationAction(project: Project) :
        ServiceRegistrationAction<DokkaParallelBuildService, BuildServiceParameters.None>(
            project,
            DokkaParallelBuildService::class.java,
            MAX_WORKER_NUMBER
        ) {
        override fun configure(parameters: BuildServiceParameters.None) {}
    }
}

private const val MAX_WORKER_NUMBER = 1
