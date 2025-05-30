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

package com.android.build.gradle.internal.services

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.options.SyncOptions
import com.android.ide.common.workers.WorkerExecutorException
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import org.gradle.api.logging.Logging
import java.io.Closeable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import javax.annotation.concurrent.ThreadSafe

/** Wrapper around aapt2 and an executor service for use in gradle worker actions. */
@ThreadSafe
class AsyncResourceProcessor<ServiceT> constructor(
    private val owner: String,
    private val executor: ExecutorService,
    private val service: ServiceT,
    val errorFormatMode: SyncOptions.ErrorFormatMode
    ) : Closeable {

    val logger = Logging.getLogger(this.javaClass)
    val iLogger = LoggerWrapper(logger)

    private var counter = 0
    private val futures = mutableListOf<Future<*>>()


    @Synchronized
    fun submit(analyticsService: AnalyticsService, action: (ServiceT) -> Unit) {
        val workerKey = "$owner${counter.inc()}"
        analyticsService.getTaskRecord(owner)?.addWorker(workerKey, GradleBuildProfileSpan.ExecutionType.THREAD_EXECUTION)
        futures.add(executor.submit {
            analyticsService.workerStarted(owner, workerKey)
            action.invoke(service)
            analyticsService.workerFinished(owner, workerKey)
        })
    }

    @Synchronized
    private fun drainFutures() : List<Future<*>> {
        val currentTasks = mutableListOf<Future<*>>()
        currentTasks.addAll(futures)
        futures.clear()
        return currentTasks
    }

    fun await() {
        val currentTasks = drainFutures()
        val exceptions = ArrayList<Throwable>()
        for (task in currentTasks) {
            try {
                task.get()
            } catch (e: ExecutionException) {
                exceptions.add(e)
            }
        }
        if (exceptions.isNotEmpty()) {
            throw WorkerExecutorException(exceptions)
        }
    }


    override fun close() {
        await()
    }
}
