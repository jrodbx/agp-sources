/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.ProjectOptions
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.util.concurrent.ForkJoinPool


/** Build service used to hold shared thread pool used for aapt2. */
abstract class Aapt2ThreadPoolBuildService : BuildService<Aapt2ThreadPoolBuildService.Params>,
    AutoCloseable {
    interface Params : BuildServiceParameters {
        val aapt2ThreadPoolSize: Property<Int>
    }

    val aapt2ThreadPool: ForkJoinPool = ForkJoinPool(parameters.aapt2ThreadPoolSize.get())

    override fun close() {
        aapt2ThreadPool.shutdown()
    }

    class RegistrationAction(project: Project, projectOptions: ProjectOptions) :
        ServiceRegistrationAction<Aapt2ThreadPoolBuildService, Params>(
            project,
            Aapt2ThreadPoolBuildService::class.java
        ) {
        private val aapt2ThreadPoolSize =
            computeMaxAapt2Daemons(projectOptions)



        override fun configure(parameters: Params) {
            parameters.aapt2ThreadPoolSize.set(aapt2ThreadPoolSize)
        }
    }
}

private const val MAX_AAPT2_THREAD_POOL_SIZE = 8

fun computeMaxAapt2Daemons(projectOptions: ProjectOptions): Int {
    return projectOptions.get(IntegerOption.AAPT2_THREAD_POOL_SIZE) ?: Integer.min(
        MAX_AAPT2_THREAD_POOL_SIZE,
        ForkJoinPool.getCommonPoolParallelism()
    )
}
