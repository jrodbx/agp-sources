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

package com.android.build.api.variant

import org.gradle.api.Action
import org.gradle.api.Incubating

@Incubating
interface DslLifecycle<T> {

    /**
     * API to customize the DSL Objects programmatically after they have been evaluated from the
     * build files and before used in the build process next steps like variant or tasks creation.
     *
     * Example of a build type creation:
     * ```kotlin
     * androidComponents.finalizeDsl { extension ->
     *     extension.buildTypes.create("extra")
     * }
     * ```
     */
    fun finalizeDsl(callback: (T) -> Unit)

    /**
     * [Action] based version of [finalizeDsl] above.
     */
    fun finalizeDsl(callback: Action<T>)
}
