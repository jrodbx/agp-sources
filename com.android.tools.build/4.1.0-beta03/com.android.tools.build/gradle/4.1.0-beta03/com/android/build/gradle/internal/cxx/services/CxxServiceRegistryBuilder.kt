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

import java.lang.IllegalArgumentException
import java.util.WeakHashMap

/**
 * Builder for default implementation of ServiceRegistry.
 *
 * This implementation uses WeakHashMap for holding ServiceKeys. The reason
 * for this is that some ServiceKey implementations may hold large objects
 * that need to be collected. We don't want to hold these objects longer
 * than necessary.
 *
 * This implementation is not currently thread-safe. If thread safety is
 * needed at some point then by careful about using ServiceKey as the
 * synchronizing object. Services are lazily populated and potentially
 * could cause locks to be taken in opposite order from different threads,
 * resulting in deadlock.
 */
internal class CxxServiceRegistryBuilder {

    // Holds the factories for each ServiceKey instance.
    private val factories = WeakHashMap<CxxServiceKey<*>, () -> Any?>()

    // Holds the actual value (result of calling factory).
    private val values = WeakHashMap<CxxServiceKey<*>, Any?>()

    /**
     * Registers a factory for the given service key.
     */
    fun <T> registerFactory(type: CxxServiceKey<T>, factory: () -> T?) {
        factories[type] = factory
    }

    /**
     * Seals this service registry and returns an immutable
     */
    fun build() : CxxServiceRegistry {
        return object : CxxServiceRegistry {
            /**
             * Will get the service value related to the given key, computing it if
             * necessary.
             */
            override fun <T> getOrNull(key: CxxServiceKey<T>): T? {
                @Suppress("UNCHECKED_CAST")
                return values.computeIfAbsent(key) {
                    val factory = factories.computeIfAbsent(key) {
                        throw IllegalArgumentException("Service $key has not been registered")
                    }
                    factory()
                } as T?
            }
        }
    }
}

