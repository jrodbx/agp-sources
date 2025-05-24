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
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.min
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.ProjectOptions

/**
 * Build service to manage R8/D8 shared thread pool. Thread pool size can be adjusted using the
 * [com.android.build.gradle.options.IntegerOption.R8_THREAD_POOL_SIZE] integer option.
 */
abstract class R8D8ThreadPoolBuildService : BuildService<R8D8ThreadPoolBuildService.Parameters>, AutoCloseable {

    abstract class Parameters : BuildServiceParameters {

        abstract val threadPoolSize: Property<Int>

    }

    /**
     * Shared thread pool used by R8 and D8 tasks.
     */
    val threadPool: ExecutorService by lazy {
        threadPoolCreated = true
        newThreadPool(parameters.threadPoolSize.get())
    }

    private var threadPoolCreated = false

    override fun close() {
        if (threadPoolCreated) {
            threadPool.doClose()
        }
    }

    class RegistrationAction(project: Project, projectOptions: ProjectOptions) :
        ServiceRegistrationAction<R8D8ThreadPoolBuildService, Parameters>(
            project,
            R8D8ThreadPoolBuildService::class.java
        ) {

        // This `IntegerOption` has default value so get() should return not-null
        private val r8ThreadPoolSize = projectOptions.get(IntegerOption.R8_THREAD_POOL_SIZE)!!

        override fun configure(parameters: Parameters) {
            parameters.threadPoolSize.set(r8ThreadPoolSize)
        }
    }

    companion object {

        fun newThreadPool(threadPoolSize: Int): ExecutorService {
            // Use the same type of thread pool that R8 is using (see b/375394051#comment7)
            return Executors.newWorkStealingPool(threadPoolSize)
        }

        fun defaultThreadPoolSize(): Int {
            // Use the same thread pool size that R8 is using
            // (see https://r8.googlesource.com/r8/+/fedff04/src/main/java/com/android/tools/r8/utils/ThreadUtils.java#232)
            val processors = Runtime.getRuntime().availableProcessors()
            return if (processors <= 16) {
                processors
            } else {
                val threadPoolSize = 16 + Math.round((processors - 16) / 2.0).toInt()
                min(threadPoolSize, 48)
            }
        }
    }
}

/**
 * [ExecutorService.close] is only available on JDK 19+, so we implement a simpler version of it
 * here.
 */
fun ExecutorService.doClose() {
    // Submitted tasks should have completed execution by the time this method is called, so after
    // `shutdown()` we expect `awaitTermination()` to return `true` immediately.
    // In the unexpected case that it returns `false`, we'll ask users to file a bug.
    shutdown()
    while (!awaitTermination(60, TimeUnit.SECONDS)) {
        LoggerWrapper.getLogger(R8D8ThreadPoolBuildService::class.java)
            .warning(
                "Unable to shut down ExecutorService after 60 seconds: ${toString()}.\n" +
                        "Waiting for another 60 seconds.\n" +
                        "If this issue persists, try ./gradlew --stop and file a bug."
            )
    }
}
