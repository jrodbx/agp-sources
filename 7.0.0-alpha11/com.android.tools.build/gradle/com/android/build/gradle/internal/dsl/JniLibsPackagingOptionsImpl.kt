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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.JniLibsPackagingOptions

open class JniLibsPackagingOptionsImpl : JniLibsPackagingOptions {

    override var useLegacyPackaging: Boolean? = null

    override val excludes: MutableSet<String> = mutableSetOf()

    // support excludes += 'foo' syntax in groovy
    fun setExcludes(patterns: Set<String>) {
        val newExcludes = patterns.toList()
        excludes.clear()
        excludes.addAll(newExcludes)
    }

    override val pickFirsts: MutableSet<String> = mutableSetOf()

    // support pickFirsts += 'foo' syntax in groovy
    fun setPickFirsts(patterns: Set<String>) {
        val newPickFirsts = patterns.toList()
        pickFirsts.clear()
        pickFirsts.addAll(newPickFirsts)
    }

    override val keepDebugSymbols: MutableSet<String> = mutableSetOf()

    // support keepDebugSymbols += 'foo' syntax in groovy
    fun setKeepDebugSymbols(patterns: Set<String>) {
        val newKeepDebugSymbols = patterns.toList()
        keepDebugSymbols.clear()
        keepDebugSymbols.addAll(newKeepDebugSymbols)
    }
}
