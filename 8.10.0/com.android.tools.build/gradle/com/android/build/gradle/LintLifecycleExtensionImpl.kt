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

package com.android.build.gradle

import com.android.build.api.dsl.Lint
import com.android.build.api.extension.impl.DslLifecycleComponentsOperationsRegistrar
import com.android.build.api.variant.LintLifecycleExtension
import org.gradle.api.Action

open class LintLifecycleExtensionImpl internal constructor(
    private val dslLifecycleOperationsRegistrar: DslLifecycleComponentsOperationsRegistrar<Lint>
): LintLifecycleExtension {

    override fun finalizeDsl(callback: (Lint) -> Unit) {
        dslLifecycleOperationsRegistrar.add {
            callback.invoke(it)
        }
    }

    override fun finalizeDsl(callback: Action<Lint>) {
        dslLifecycleOperationsRegistrar.add(callback)
    }
}
