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

import com.android.build.gradle.internal.profile.ProfilerInitializer
import com.android.ide.common.workers.ExecutorServiceAdapter
import com.android.ide.common.workers.GradlePluginMBeans
import com.android.ide.common.workers.WorkerExecutorException
import com.android.ide.common.workers.WorkerExecutorFacade
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutionException
import org.gradle.workers.WorkerExecutor
import java.io.Serializable
import java.lang.reflect.Constructor
import java.util.concurrent.ExecutorService
import java.util.concurrent.ForkJoinPool
import javax.inject.Inject

/**
 * Singleton object responsible for providing instances of [WorkerExecutorFacade]
 * in the context of the current build settings (like whether or not we should use
 * Gradle's [WorkerExecutor] or the level of parallelism allowed by users.
 */
object Workers {

    /**
     * Possibly, in the future, consider using a pool with a dedicated size using the gradle
     * parallelism settings.
     */
    private val defaultExecutorService: ExecutorService = ForkJoinPool.commonPool()

    /**
     * Creates a [WorkerExecutorFacade] using the passed [WorkerExecutor], using the value of
     * [enableGradleWorkers] to decide which implementation to use.
     *
     * If the Gradle workers are enabled, submission of work items will be handled preferably
     * by a [WorkerExecutor.submit], otherwise by a [ExecutorService.submit] call.
     *
     * @param projectName name of the project owning the task
     * @param owner the task path issuing the request and owning the [WorkerExecutor] instance.
     * @param worker [WorkerExecutor] to use if Gradle's worker executor are enabled.
     * @param enableGradleWorkers if Gradle workers should be used.
     * @param executor [ExecutorService] to use if the Gradle's worker are not enabled or null
     * if the default installed version is to be used.
     * @return an instance of [WorkerExecutorFacade] using the passed worker or the default
     * [ExecutorService] depending on the project options.
     */
    @JvmOverloads
    fun preferWorkers(
        projectName: String,
        owner: String,
        worker: WorkerExecutor,
        enableGradleWorkers: Boolean,
        executor: ExecutorService? = null
    ): WorkerExecutorFacade {
        return if (enableGradleWorkers) {
            WorkerExecutorAdapter(
                projectName,
                owner,
                worker
            )
        } else {
            ProfileAwareExecutorServiceAdapter(
                projectName,
                owner,
                executor ?: defaultExecutorService,
                WorkerExecutorAdapter(projectName, owner, worker)
            )
        }
    }

    /**
     * Creates a [WorkerExecutorFacade] using the passed [WorkerExecutor]
     *
     * Submission will preferably use a default [ExecutorService] to submit work items, but
     * environment settings may force to use Gradle Workers instead.
     *
     * @param projectName the project name.
     * @param owner the task path issuing the request and owning the [WorkerExecutor] instance.
     * @param workerExecutor [WorkerExecutor] to use if Gradle's worker executor are enabled.
     * @param enableGradleWorkers if Gradle workers can be used.
     * if the default installed version is to be used.
     * @return an instance of [WorkerExecutorFacade].
     */
    fun preferThreads(
        projectName: String,
        owner: String,
        workerExecutor: WorkerExecutor,
        enableGradleWorkers: Boolean
    ): WorkerExecutorFacade {
        return ProfileAwareExecutorServiceAdapter(
            projectName,
            owner,
            defaultExecutorService,
            preferWorkers(projectName, owner, workerExecutor, enableGradleWorkers))
    }

    /**
     * Creates a [WorkerExecutorFacade] using the default [ExecutorService].
     *
     * Callers cannot use a [WorkerExecutor] probably due to Serialization requirement of parameters
     * being not possible.
     *
     * @param projectName the project name.
     * @param owner the task path issuing the request.
     * @return an instance of [WorkerExecutorFacade]
     */
    fun withThreads(projectName: String, owner: String) =
        ProfileAwareExecutorServiceAdapter(projectName, owner, defaultExecutorService)

    /**
     * Simple implementation of [WorkerExecutorFacade] that uses a Gradle [WorkerExecutor]
     * to submit new work actions.
     *
     */
    private class WorkerExecutorAdapter(
        private val projectName: String,
        private val owner: String,
        private val workerExecutor: WorkerExecutor
    ) :
        WorkerExecutorFacade {

        val taskRecord by lazy {
            ProfilerInitializer.getListener()?.getTaskRecord(owner)
        }

        override fun submit(
            actionClass: Class<out Runnable>,
            parameter: Serializable
        ) {
            submit(
                actionClass,
                WorkerExecutorFacade.Configuration(
                    parameter,
                    WorkerExecutorFacade.IsolationMode.NONE,
                    listOf()
                )
            )
        }

        override fun submit(
            actionClass: Class<out Runnable>,
            configuration: WorkerExecutorFacade.Configuration
        ) {
            val workerKey = "$owner${actionClass.name}${configuration.parameter.hashCode()}"
            val submissionParameters = ActionParameters(
                actionClass,
                configuration.parameter,
                projectName,
                owner,
                workerKey
            )

            taskRecord?.addWorker(workerKey, GradleBuildProfileSpan.ExecutionType.WORKER_EXECUTION)

            val classpath = configuration.classPath.toList()

            workerExecutor.submit(ActionFacade::class.java) {
                it.isolationMode = configuration.isolationMode.toGradleIsolationMode()
                if (classpath.isNotEmpty()) {
                    it.classpath = classpath
                }
                if (configuration.jvmArgs.isNotEmpty()) {
                    it.forkOptions.setJvmArgs(configuration.jvmArgs)
                }
                it.params(submissionParameters)
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
            taskRecord?.setTaskClosed()
        }
    }

    /**
     * Translates sdk common [WorkerExecutorFacade.IsolationMode] into Gradle's [IsolationMode]
     */
    fun WorkerExecutorFacade.IsolationMode.toGradleIsolationMode() =
        when (this) {
            WorkerExecutorFacade.IsolationMode.NONE -> IsolationMode.NONE
            WorkerExecutorFacade.IsolationMode.CLASSLOADER -> IsolationMode.CLASSLOADER
            WorkerExecutorFacade.IsolationMode.PROCESS -> IsolationMode.PROCESS
            else -> throw IllegalArgumentException("$this is not a handled isolation mode")
        }

    class ActionParameters(
        val delegateAction: Class<out Runnable>,
        val delegateParameters: Serializable,
        val projectName: String,
        val taskOwner: String,
        val workerKey: String
    ) : Serializable

    class ActionFacade @Inject constructor(val params: ActionParameters) : Runnable {

        override fun run() {
            val constructor = findAppropriateConstructor()
                ?: throw RuntimeException("Cannot find constructor with @Inject in ${params.delegateAction.name}")

            val delegate = constructor.newInstance(params.delegateParameters) as Runnable
            GradlePluginMBeans.getProfileMBean(params.projectName)
                ?.workerStarted(params.taskOwner, params.workerKey)
            delegate.run()
            GradlePluginMBeans.getProfileMBean(params.projectName)
                ?.workerFinished(params.taskOwner, params.workerKey)
        }

        private fun findAppropriateConstructor(): Constructor<*>? {
            for (constructor in params.delegateAction.constructors) {
                if (constructor.parameterTypes.size == 1
                    && constructor.isAnnotationPresent(Inject::class.java)
                    && Serializable::class.java.isAssignableFrom(constructor.parameterTypes[0])
                ) {
                    constructor.isAccessible = true
                    return constructor
                }
            }
            return null
        }
    }

    /**
     * Adapter to record tasks using the [ExecutorService] through a [WorkerExecutorFacade].
     *
     * This will allow to record thread execution, just like WorkerItems.
     */
    class ProfileAwareExecutorServiceAdapter(
        projectName: String,
        owner: String,
        executor: ExecutorService,
        delegate: WorkerExecutorFacade? = null
    ) :
        ExecutorServiceAdapter(projectName, owner, executor, delegate) {

        private val taskRecord by lazy {
            ProfilerInitializer.getListener()?.getTaskRecord(owner)
        }

        override fun workerSubmission(workerKey: String) {
            super.workerSubmission(workerKey)
            taskRecord?.addWorker(workerKey, GradleBuildProfileSpan.ExecutionType.THREAD_EXECUTION)
        }
    }
}