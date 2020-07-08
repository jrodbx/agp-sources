/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.ide.common.gradle.model

import com.android.builder.model.DependenciesInfo
import java.io.Serializable
import java.util.Objects

class IdeDependenciesInfo : DependenciesInfo, Serializable {
    val includeInApk: Boolean
    val includeInBundle: Boolean
    val hashCode: Int

    override fun isIncludeInApk() = includeInApk

    override fun isIncludeInBundle() = includeInBundle

    companion object {
        @JvmStatic
        fun createOrNull(model: DependenciesInfo?) = model?.let { IdeDependenciesInfo(it) }
    }

    constructor(model: DependenciesInfo) {
        this.includeInApk = model.isIncludeInApk
        this.includeInBundle = model.isIncludeInBundle
        this.hashCode = calculateHashCode()
    }

    constructor() {
        this.includeInApk = true
        this.includeInBundle = true
        this.hashCode = 0
    }

    override fun equals(other: Any?): Boolean {
        return when {
            this === other -> true
            other !is IdeDependenciesInfo -> false
            else -> includeInApk == other.includeInApk && includeInBundle == other.includeInBundle
        }
    }

    override fun hashCode(): Int = hashCode;

    override fun toString(): String = "IdeDependenciesInfo{" +
            "includeInApk=$includeInApk, "+
            "includeInBundle=$includeInBundle" +
            "}"


    private fun calculateHashCode(): Int = Objects.hash(includeInApk, includeInBundle)
}