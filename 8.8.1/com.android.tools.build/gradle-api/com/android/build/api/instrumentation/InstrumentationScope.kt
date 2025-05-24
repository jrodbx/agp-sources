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

package com.android.build.api.instrumentation

enum class InstrumentationScope {
    /**
     * Instrument the classes of the current project only.
     *
     * Libraries that this project depends on will not be instrumented.
     */
    PROJECT,

    /**
     * Instrument the classes of the current project and its library dependencies.
     *
     * This can't be applied to library projects, as instrumenting library dependencies will have no
     * effect on library consumers.
     */
    ALL
}
