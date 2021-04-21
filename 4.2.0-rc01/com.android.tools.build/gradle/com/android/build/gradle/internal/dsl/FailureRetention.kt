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

import com.android.build.gradle.internal.services.DslServices
import javax.inject.Inject

open class FailureRetention @Inject constructor(dslServices: DslServices) :
    com.android.build.api.dsl.FailureRetention {

    override var enable: Boolean = false
    private var retainAll: Boolean = false

    override fun retainAll() {
        retainAll = true
    }

    fun getRetainAll(): Boolean {
        return retainAll
    }

    override var maxSnapshots: Int = 5

    override var compressSnapshots: Boolean = false
}
