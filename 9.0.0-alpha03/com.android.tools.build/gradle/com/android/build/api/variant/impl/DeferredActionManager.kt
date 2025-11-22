/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.api.variant.impl

/**
 * This class manages the registration and book keeping of deferred actions.
 *
 * A deferred action is a simple lambda that can be store for later execution.
 */
class DeferredActionManager {

    val registeredListeners = mutableListOf<() -> Unit>()

    @Synchronized
    fun addAction(action: () -> Unit) {
        registeredListeners.add(action)
    }

    @Synchronized
    fun executeActions() {
        registeredListeners.forEach { action ->
            action()
        }
    }
}
