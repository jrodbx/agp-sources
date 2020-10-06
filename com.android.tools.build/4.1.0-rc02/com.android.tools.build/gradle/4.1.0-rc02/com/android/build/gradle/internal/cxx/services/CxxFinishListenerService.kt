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

import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.model.CxxBuildModel

/**
 * Create and register a [CxxFinishListenerService] for executing actions on build completion.
 */
internal fun createFinishListenerService(services: CxxServiceRegistryBuilder) {
    services.registerFactory(FINISH_LISTENER_SERVICE_KEY) {
        CxxFinishListenerService()
    }
}

/**
 * Registers a listener that runs when the build finishes.
 * @param key to identify afterSyncListeners. Registering afterSyncListeners with the same key would overwrite the
 *   previous listener registered under this key
 * @param listener the listener to be executed when build completes. If the listener throws an
 *   exception, it would bubble up and cause the gradle build to fail. So if it's OK for the
 *   listener to fail, it should catch its own exceptions.
 */
fun CxxBuildModel.runWhenBuildFinishes(key: String, listener: () -> Unit) {
    services[FINISH_LISTENER_SERVICE_KEY].finishListeners[key] = listener
}

/** Invokes all the registered afterSyncListeners. This is invoked by Gradle when the build completes. */
internal fun CxxBuildModel.runFinishListeners() {
    services[FINISH_LISTENER_SERVICE_KEY].finishListeners.forEach { (key, listener) ->
        infoln("Invoking finish listener '$key'...")
        listener()
    }
}

/** Private service key for CxxFinishListenerService. */
private val FINISH_LISTENER_SERVICE_KEY = object : CxxServiceKey<CxxFinishListenerService> {
    override val type = CxxFinishListenerService::class.java
}

private data class CxxFinishListenerService(
    val finishListeners: MutableMap<String, () -> Unit> = mutableMapOf()
)
