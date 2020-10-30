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

package com.android.build.gradle.internal.cxx.services

import com.android.build.gradle.internal.cxx.logging.PassThroughDeduplicatingLoggingEnvironment
import com.android.build.gradle.internal.cxx.model.CxxBuildModel

/**
 * Once-per-build service that executes at different points in the build lifetime.
 */
interface CxxBuildModelListener {
    fun onceBeforeJsonGeneration(model : CxxBuildModel)
}

/**
 * Register the listener service.
 */
internal fun createBuildModelListenerService(builder : CxxServiceRegistryBuilder) {
    builder.registerFactory(BUILD_MODEL_LISTENER_SERVICE) { CxxBuildModelListenerService() }
}

/**
 * Private service key for process service. For use in retrieving this service from
 * [CxxServiceRegistry].
 */
private val BUILD_MODEL_LISTENER_SERVICE = object : CxxServiceKey<CxxBuildModelListenerService> {
    override val type = CxxBuildModelListenerService::class.java
}

/**
 * Action to execute once per-build before JSON generation.
 */
fun CxxBuildModel.doOnceBeforeJsonGeneration(
    key : String,
    action : (CxxBuildModel) -> Unit) {
    services[BUILD_MODEL_LISTENER_SERVICE].listeners[key] = object : CxxBuildModelListener {
        override fun onceBeforeJsonGeneration(model: CxxBuildModel) {
            action(model)
        }
    }
}

/**
 * Call to execute all listeners for this [CxxBuildModel] before Json generation starts.
 * The second and subsequent calls to this function will do nothing except return true or
 * false indicating whether JSON generation should proceed.
 *
 * This function is intentionally serialized in such a way that all listeners execute before
 * *any* call is allowed to finish. This is so that JSON generation can choose to not execute
 * if there are any errors.
 *
 * This function returns true if no error occurred while invoking listeners.
 */
fun CxxBuildModel.executeListenersOnceBeforeJsonGeneration() : Boolean {
    val service = services[BUILD_MODEL_LISTENER_SERVICE]
    synchronized(this) {
        if (service.beforeJsonGenerationListenerInvocationFailed == null) {
            PassThroughDeduplicatingLoggingEnvironment().use { logger ->
                service.listeners.values.forEach { listener ->
                    listener.onceBeforeJsonGeneration(this)
                }
                service.beforeJsonGenerationListenerInvocationFailed = !logger.hadErrors()
            }
        }
    }
    return service.beforeJsonGenerationListenerInvocationFailed!!
}

/**
 * Private interface to access the process service via [CxxServiceRegistry].
 */
private class CxxBuildModelListenerService {
    val listeners = mutableMapOf<String, CxxBuildModelListener>()
    var beforeJsonGenerationListenerInvocationFailed : Boolean? = null
}
