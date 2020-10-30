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
 * This type represents an entry in the [ConfigTable]. It associates a [Config] with one or more
 * [Artifact] instances.
 *
 * New properties may be added in the future; clients that invoke the constructor are encouraged to
 * use Kotlin named arguments to stay source compatible.
 */
data class ConfigAssociation(
        /**
         * Filter that matches all [Artifact] instances this [Config] is associated with.
         */
        val path: ConfigPath,
        /**
         * Holds the [Config] being associated.
         */
        val config: Config
) {
    override fun toString(): String = path.toString()
}
