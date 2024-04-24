/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.ide.v2

import com.android.builder.model.v2.ide.LibraryInfo
import java.io.Serializable

data class LibraryInfoImpl(
    override val buildType: String?,
    override val productFlavors: Map<String, String>,
    override val attributes: Map<String, String>,
    override val capabilities: List<String>,
    override val group: String,
    override val name: String,
    override val version: String,
    override val isTestFixtures: Boolean
) : LibraryInfo, Serializable {
    companion object {
        @JvmStatic
        private val serialVersionUID: Long = 1L
    }

    fun computeKey(): String {
        val builder = StringBuilder()
        builder.append(group).append("|").append(name).append("|").append(version).append('|')
        if (buildType != null) builder.append(buildType).append("|")
        if (productFlavors.isNotEmpty()) builder.append(productFlavors.toKeyComponent()).append("|")
        builder.append(attributes.toKeyComponent()).append("|")
        builder.append(capabilities.sorted().joinToString())
        return builder.toString()
    }

    private fun Map<String, String>.toKeyComponent() =
        entries.sortedBy { it.key }.joinToString { "${it.key}>${it.value}" }

}
