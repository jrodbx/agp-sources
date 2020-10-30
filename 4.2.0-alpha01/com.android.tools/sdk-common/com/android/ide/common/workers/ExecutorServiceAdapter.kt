/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.ide.common.workers

import java.io.Serializable
import java.lang.IllegalArgumentException
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implementation of [WorkerExecutorFacade] using a plain JDK [ExecutorService]
 */
open class ExecutorServiceAdapter(
    /**
     * Project name owning this adapter.
     */
    val projectName: String,

    /**
     * Task name owning this adapter.
     */
    val owner: String,
    /**
     * Instantiate an adapter using the passed [ExecutorService]
     */
    val executor: ExecutorService,
    /**
     * [WorkerExecutorFacade] to delegate submissions that cannot be handled by this adapter or
     * null if no delegation expected.
     */
    private val delegate: WorkerExecutorFacade?= null
) : WorkerExecutorFacade {

    constructor(projectName: String, owner: String, executor: ExecutorService):
            this(projectName, owner, executor, null)

    private val futures = mutableListOf<Future<*>>()
    private val delegateUsed= AtomicBoolean(false)

    override fun submit(
        actionClass: Class<out Runnable>,
        parameter: Serializable
    ) {
        val key= "$owner${actionClass.name}${parameter.hashCode()}"
        workerSubmission(key)

        val submission = executor.submit {
            val constructor = actionClass.getDeclaredConstructor(parameter.javaClass)
            constructor.isAccessible = true
            val action = constructor.newInstance(parameter)
            GradlePluginMBeans.getProfileMBean(projectName)?.workerStarted(owner, key)
            action.run()
            GradlePluginMBeans.getProfileMBean(projectName)?.workerFinished(owner, key)
        }
        synchronized(this) {
            futures.add(submission)
        }
    }

    override fun submit(
        actionClass: Class<out Runnable>,
        configuration: WorkerExecutorFacade.Configuration
    ) {
        if (configuration.isolationMode != WorkerExecutorFacade.IsolationMode.NONE) {
            if (delegate == null) {
                throw IllegalArgumentException(
                    "Adapter does not support ${configuration.isolationMode} " +
                            "and no delegate provided")
            }
            delegateUsed.set(true)
            delegate.submit(actionClass, configuration)
        } else {
            submit(actionClass, configuration.parameter)
        }
    }

    override fun await() {
        val currentTasks = mutableListOf<Future<*>>()
        synchronized(this) {
            currentTasks.addAll(futures)
            futures.clear()
        }
        val exceptions = ArrayList<Throwable>()
        currentTasks.forEach {
            try {
                it.get()
            } catch (e: ExecutionException) {
                exceptions.add(e)
            }
        }
        if (!exceptions.isEmpty()) {
            throw WorkerExecutorException(exceptions)
        }
        if (delegateUsed.get()) {
            delegate?.await()
        }
    }

    // We need to call await on closing because Gradle is not aware of any java workers spawned by
    // a task, so we should wait till everything is finished.
    override fun close() {
        await()
    }

    /**
     * Notification of a new worker submission.
     */
    protected open fun workerSubmission(workerKey: String) {
    }
}