/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.build.api.dsl.Installation
import com.android.build.gradle.internal.services.DslServices
import javax.inject.Inject

/** Options for the adb tool. */
abstract class AdbOptions @Inject constructor(
    val dslServices: DslServices,
) : com.android.builder.model.AdbOptions,
    com.android.build.api.dsl.AdbOptions,
    Installation {

    abstract override val installOptions: MutableList<String>
    abstract override var timeOutInMs: Int

    open fun timeOutInMs(timeOutInMs: Int) {
        this.timeOutInMs = timeOutInMs
    }

    fun setInstallOptions(option: String) {
        installOptions.clear()
        installOptions.add(option)
    }

    fun setInstallOptions(vararg options: String) {
        installOptions.clear()
        installOptions.addAll(options)
    }

    override fun installOptions(option: String) {
        installOptions.clear()
        installOptions.add(option)
    }

    override fun installOptions(vararg options: String) {
        installOptions.clear()
        installOptions.addAll(options)
    }
}
