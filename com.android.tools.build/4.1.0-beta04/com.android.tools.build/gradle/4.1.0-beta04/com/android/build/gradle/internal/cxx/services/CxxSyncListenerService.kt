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

import com.android.build.gradle.internal.cxx.model.CxxAbiModel


/**
 * Register a [CxxAbiListenerService] in the [CxxServiceRegistryBuilder].
 */
internal fun createSyncListenerService(builder : CxxServiceRegistryBuilder) {
    builder.registerFactory(ABI_MODEL_LISTENER_SERVICE) {
        CxxAbiListenerService()
    }
}


/**
 * Private service key for process service. For use in retrieving this service from
 * [CxxServiceRegistry].
 */
private val ABI_MODEL_LISTENER_SERVICE = object : CxxServiceKey<CxxAbiListenerService> {
    override val type = CxxAbiListenerService::class.java
}

/**
 * Key type for registering Sync afterSyncListeners.
 */
interface CxxAbiListenerServiceKey

/**
 * Action to execute once per-ABI after JSON generation is complete.
 * Example:
 *
 * private val MY_KEY = object : CxxAbiListenerServiceKey { }
 * abi.doAfterJsonGeneration { abi ->
 *   // Do some work with abi
 * }
 *
 */
fun CxxAbiModel.doAfterJsonGeneration(
    key : CxxAbiListenerServiceKey,
    action : (CxxAbiModel) -> Unit) {
    services[ABI_MODEL_LISTENER_SERVICE].afterSyncListeners[key] = action
}

/**
 * Called to execute all afterSyncListeners once after JSON generation.
 */
fun CxxAbiModel.executeListenersOnceAfterJsonGeneration() {
    val listenerService = services[ABI_MODEL_LISTENER_SERVICE]
    if (!listenerService.onAfterSyncCalled) {
        listenerService.onAfterSyncCalled = true
        listenerService.afterSyncListeners.values.forEach { callback ->
            callback(this)
        }
    }
}

/**
 * Private interface to access the process service via [CxxServiceRegistry].
 */
private class CxxAbiListenerService {
    val afterSyncListeners = mutableMapOf<CxxAbiListenerServiceKey, (CxxAbiModel) -> Unit>()
    var onAfterSyncCalled = false
}
