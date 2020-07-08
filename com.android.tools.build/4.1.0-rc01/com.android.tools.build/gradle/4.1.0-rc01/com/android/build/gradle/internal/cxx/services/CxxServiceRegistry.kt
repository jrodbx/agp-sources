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

/**
 * Defines a key for a service withing ServiceRegistry.
 * The type field defines the type of the provided service.
 *
 * Contract:
 * - Implementations of service key must use object instance identity because
 * multiple keys of the same type may represent different services.
 *
 * - In particular, ServiceKey must not be implemented as a data class
 * because this introduces value identity.
 *
 * A typical implementation of ServiceKey might be like:
 *
 *   val MY_FANCY_SERVICE = object : ServiceKey<FancyService> {
 *     override type = FancyService::class.java
 *   }
 *
 * This is an instance that is used to register the service and to retrieve it
 * later. It also defines the interface type for the service.
 *
 * It's possible to store basic types (like String) this way as well.
 *
 *   val MY_CONFIG_STRING = object : ServiceKey<String> {
 *     override type = String::class.java
 *   }
 */
interface CxxServiceKey<T> {
    val type : Class<T>
}

/**
 * Defines a registry of services that are available by key.
 */
interface CxxServiceRegistry {
    /**
     * Get the requested service.
     * Contract:
     * - Throws an exception if the key wasn't registered.
     * - Throws an exception if the key value is null.
     */
    operator fun <T> get(key : CxxServiceKey<T>) : T = getOrNull(key)!!

    /**
     * Get the requested service.
     * Contract:
     * - Throws an exception if the key wasn't registered.
     * - Returns null if key value is null.
     */
    fun <T> getOrNull(key : CxxServiceKey<T>) : T?
}