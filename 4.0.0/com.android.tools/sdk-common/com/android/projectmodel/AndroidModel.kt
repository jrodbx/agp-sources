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
package com.android.projectmodel

/**
 * Entry point for the Android model. It contains a collection of libraries, applications, instant apps, etc.
 *
 * New properties may be added in the future; clients that invoke the constructor are encouraged to
 * use Kotlin named arguments to stay source compatible.
 */
data class AndroidModel (
    /**
     * List of [AndroidSubmodule] that are present in this module.
     */
    val submodules: Collection<AndroidSubmodule> = emptyList()
) {
    /**
     * Map of project names onto [AndroidSubmodule].
     */
    val submodulesByName: Map<String, AndroidSubmodule> = submodules.associateBy { it.name }

    override fun toString(): String = printProperties(this, AndroidModel())

    /**
     * Returns the submodule with the given name or null if none.
     */
    fun getSubmodule(name: String): AndroidSubmodule? = submodulesByName[name]
}
