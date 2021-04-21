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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.ide.common.workers.ExecutorServiceAdapter
import com.android.ide.common.workers.WorkerExecutorException
import com.android.ide.common.workers.WorkerExecutorFacade
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.workers.WorkerExecutionException
import org.gradle.workers.WorkerExecutor
import java.util.concurrent.ExecutorService
import java.util.concurrent.ForkJoinPool

/**
 * Singleton object responsible for providing instances of [WorkerExecutorFacade]
 * that relies on Gradle's [WorkerExecutor].
 */
object Workers {

    /**
     * Possibly, in the future, consider using a pool with a dedicated size using the gradle
     * parallelism settings.
     */
    private val defaultExecutorService: ExecutorService = ForkJoinPool.commonPool()

    /**
     * Creates a [WorkerExecutorFacade] using the passed [WorkerExecutor].
     *
     * @param projectName name of the project owning the task
     * @param owner the task path issuing the request and owning the [WorkerExecutor] instance.
     * @param worker [WorkerExecutor] to use if Gradle's worker executor are enabled.
     * @param analyticsService the build service to record worker execution spans
     * @return an instance of [WorkerExecutorFacade] using the passed worker
     */
    fun withGradleWorkers(
        projectName: String,
        owner: String,
        worker: WorkerExecutor,
        analyticsService: Provider<AnalyticsService>
    ): WorkerExecutorFacade {
        return WorkerExecutorAdapter(projectName, owner, worker, analyticsService)
    }

    /**
     * Creates a [WorkerExecutorFacade] using the default [ExecutorService].
     *
     * Callers cannot use a [WorkerExecutor] probably due to Serialization requirement of parameters
     * being not possible.
     *
     * @param owner the task path issuing the request.
     * @param analyticsService the build service to record worker execution spans
     * @return an instance of [WorkerExecutorFacade]
     */
    fun withThreads(owner: String, analyticsService: AnalyticsService) =
        ProfileAwareExecutorServiceAdapter(owner, defaultExecutorService, null, analyticsService)

    /**
     * Simple implementation of [WorkerExecutorFacade] that uses a Gradle [WorkerExecutor]
     * to submit new work actions.
     *
     */
    private class WorkerExecutorAdapter(
        private val projectName: String,
        private val owner: String,
        private val workerExecutor: WorkerExecutor,
        private val analyticsService: Provider<AnalyticsService>
    ) : WorkerExecutorFacade {

        val taskRecord by lazy {
           analyticsService.get().getTaskRecord(owner)
        }

        override fun submit(action: WorkerExecutorFacade.WorkAction) {
            val workerKey = "$owner${action::class.java.name}${action.hashCode()}"
            taskRecord?.addWorker(workerKey, GradleBuildProfileSpan.ExecutionType.WORKER_EXECUTION)

            workerExecutor.noIsolation().submit(
                RunnableWrapperWorkAction::class.java
            ) { params: RunnableWrapperParams ->
                params.projectName.set(projectName)
                params.taskOwner.set(owner)
                params.workerKey.set(workerKey)
                params.runnableAction.set(action)
                params.analyticsService.set(analyticsService)
            }
        }

        override fun await() {
            try {
                taskRecord?.setTaskWaiting()
                workerExecutor.await()
            } catch (e: WorkerExecutionException) {
                throw WorkerExecutorException(e.causes)
            }
        }

        /**
         * In a normal situation you would like to call await() here, however:
         * 1) Gradle currently can only run a SINGLE @TaskAction for a given project
         *    (and this should be fixed!)
         * 2) WorkerExecutor passed to a task instance is tied to the task and Gradle is able
         *    to control which worker items are executed by which task
         *
         * Thus, if you put await() here, only a single task can run.
         * If not (as it is), gradle will start another task right after it finishes executing a
         * @TaskAction (which ideally should be just some preparation + a number of submit() calls
         * to a WorkerExecutorFacade. In case the task B depends on the task A and the work items
         * of the task A hasn't finished yet, gradle will call await() on the dedicated
         * WorkerExecutor of the task A and therefore work items will finish before task B
         * @TaskAction starts (so, we are safe!).
         */
        override fun close() {
        }
    }

    /**
     * Adapter to record tasks using the [ExecutorService] through a [WorkerExecutorFacade].
     *
     * This will allow to record thread execution, just like WorkerItems.
     */
    class ProfileAwareExecutorServiceAdapter(
        private val owner: String,
        executor: ExecutorService,
        /**
         * [WorkerExecutorFacade] to delegate submissions that cannot be handled by this adapter or
         * null if no delegation expected.
         */
        val delegate: WorkerExecutorFacade? = null,
        private val analyticsService: AnalyticsService
    ) : ExecutorServiceAdapter(executor), WorkerExecutorFacade {

        private val taskRecord by lazy {
            analyticsService.getTaskRecord(owner)
        }

        override fun workerSubmission(workerKey: String) {
            super.workerSubmission(workerKey)
            taskRecord?.addWorker(workerKey, GradleBuildProfileSpan.ExecutionType.THREAD_EXECUTION)
        }

        override fun submit(action: WorkerExecutorFacade.WorkAction) {
            val key = "$owner${action::class.java.name}${action.hashCode()}"
            workerSubmission(key)

            val submission = executor.submit {
                analyticsService.workerStarted(owner, key)
                action.run()
                analyticsService.workerFinished(owner, key)
            }
            synchronized(this) {
                futures.add(submission)
            }
        }
    }

    abstract class RunnableWrapperParams : ProfileAwareWorkAction.Parameters() {
        abstract val runnableAction: Property<Runnable>
    }

    /* Used as a wrapper around runnables submitted using [WorkerExecutorAdapter]. */
    abstract class RunnableWrapperWorkAction : ProfileAwareWorkAction<RunnableWrapperParams>() {
        override fun run() {
            parameters.runnableAction.get().run()
        }
    }
}
