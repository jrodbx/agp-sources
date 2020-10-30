/*
 * Copyright (C) 2017 The Android Open Source Project
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

import java.io.Closeable
import java.io.File
import java.io.Serializable

/**
 * Some classes do not have access to the Gradle APIs like classes located in the builder module.
 * Yet those classes could potentially have a need for a facility similar to Gradle's WorkerExecutor
 * to submit work items for remove or parallel processing.
 *
 *
 * This interface implementation can be used by Task or other higher level implementation classes
 * to provide this facility.
 *
 * High level interaction is as follow :
 *  * Task creates a WorkerExecutorFacade object that encapsulates an Executor style facility.
 * This facade instance is passed to the classes with no access to the Gradle APIs.
 *  * Classes create a new Runnable subclass that use an instance of {@param T} as a work
 * action configuration. Such [Runnable] cannot be passed to the Gradle WorkerExecutor
 * directly as it relies on @Inject to set the parameter value.
 *  * Classes create an serializable instance of parameters for each work actions and submit it
 *  with the [submit] API.
 *  * Task should call [await] for synchronous wait on the action completion or [close] for
 *  submission completion. [close] has an implementation dependent behaviour where some will
 *  immediately return while associating the actions to the task. Others will basically delegate
 *  back to [await]
 */
interface WorkerExecutorFacade : AutoCloseable, Closeable {

    /**
     * Supported isolation modes.
     */
    enum class IsolationMode {
        NONE,
        CLASSLOADER,
        PROCESS,
    }

    /**
     * Configuration for action submission.
     */
    data class Configuration(
        val parameter: Serializable,
        val isolationMode: IsolationMode = IsolationMode.NONE,
        val classPath: Iterable<File> = listOf(),
        val jvmArgs: List<String> = listOf()
    )

    /**
     * Submit a new work action to be performed.
     *
     * @param actionClass the [Runnable] implementing  the action and taking the passed parameter
     * as input parameters.
     * @param parameter the parameter instance to pass to the action.
     */
    fun submit(actionClass: Class<out Runnable>, parameter: Serializable)

    /**
     * Submit a new work action with submission parameters.
     *
     */
    fun submit(actionClass: Class<out Runnable>, configuration: Configuration) {
        submit(actionClass, configuration.parameter)
    }

    /**
     * Wait for all submitted work actions completion.
     *
     *
     * This is to be used when more work needs to be done inside the TaskAction after the await.
     * If nothing is done in the task action afterward, use [.close] instead.
     */
    fun await()

    /**
     * Indicate the task action is done.
     *
     *
     * This has a different behavior depending on the implementation. Worker based implementation
     * does nothing, while regular thread-pool implementation wait on all the threads to be done.
     */
    override fun close()
}
