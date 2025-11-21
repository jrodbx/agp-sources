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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.min

/** Build service to manage R8 parallelism and shared thread pool. */
abstract class R8ParallelBuildService : BuildService<BuildServiceParameters.None>, AutoCloseable {

    /**
     * Shared thread pool used by all R8 tasks.
     *
     * Note: If [com.android.build.gradle.internal.core.ToolExecutionOptions.runInSeparateProcess]
     * == true, this shared thread pool will not be used, so it will not be created. (Each launched
     * process will have its own thread pool.)
     */
    val r8ThreadPool: ExecutorService by lazy {
        r8ThreadPoolCreated = true
        newR8ThreadPool()
    }

    private var r8ThreadPoolCreated = false

    override fun close() {
        if (r8ThreadPoolCreated) {
            r8ThreadPool.shutdown()
        }
    }

    class RegistrationAction(project: Project, maxParallelUsages: Int) :
        ServiceRegistrationAction<R8ParallelBuildService, BuildServiceParameters.None>(
            project,
            R8ParallelBuildService::class.java,
            maxParallelUsages
        ) {

        override fun configure(parameters: BuildServiceParameters.None) {
            // do nothing
        }
    }

    companion object {

        fun newR8ThreadPool(): ExecutorService {
            // Use the same executor and thread pool size that R8 is using
            // (see b/375394051#comment7 and https://r8.googlesource.com/r8/+/c7f65e4/src/main/java/com/android/tools/r8/utils/ThreadUtils.java#41).
            val processors = Runtime.getRuntime().availableProcessors()
            val threadPoolSize = if (processors <= 2) processors else ceil(min(processors, 16) / 2.0).toInt()

            return Executors.newWorkStealingPool(threadPoolSize)
        }
    }
}
