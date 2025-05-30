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
package com.android.utils.concurrency

import com.google.common.collect.EvictingQueue
import java.util.NoSuchElementException
import java.util.Queue
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * [ExecutorService] that only allows [maxQueueingTasks] tasks waiting to be executed. If the limit is
 * reached, the next task to arrive will preempt the first one in the queue.
 * The tasks are executed on the given [delegateExecutor].
 *
 * This class is thread-safe
 */
class EvictingExecutor(private val delegateExecutor: ExecutorService, maxQueueingTasks: Int = 1) :
    AbstractExecutorService() {
    private val evictingQueueLock = ReentrantLock()
    private val evictingQueue: Queue<Runnable> = EvictingQueue.create<Runnable>(maxQueueingTasks)

    private fun queueProcessor() = delegateExecutor.execute inner@{
        // For each offer call we always queue a remove so it will always be true that
        // (number of removes) >= (number of elements)
        val runnable = try {
            evictingQueueLock.withLock {
                evictingQueue.remove()
            }
        } catch (e: NoSuchElementException) {
            return@inner
        }

        runnable.run()
    }

    override fun isTerminated(): Boolean = delegateExecutor.isTerminated

    override fun execute(command: Runnable) {
        if (delegateExecutor.isTerminated) {
            return
        }

        evictingQueueLock.withLock {
            val before = evictingQueue.size
            evictingQueue.offer(command)
            if (before != evictingQueue.size) {
                queueProcessor()
            }
        }
    }

    override fun shutdown() {
        shutdownNow()
    }

    override fun shutdownNow(): MutableList<Runnable> {
        val pendingRunnables = mutableListOf<Runnable>()
        evictingQueueLock.withLock {
            evictingQueue.removeAll { pendingRunnables.add(it) }
        }
        return pendingRunnables.apply {
            addAll(delegateExecutor.shutdownNow())
        }
    }

    override fun isShutdown(): Boolean = delegateExecutor.isShutdown

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean =
        delegateExecutor.awaitTermination(timeout, unit)
}