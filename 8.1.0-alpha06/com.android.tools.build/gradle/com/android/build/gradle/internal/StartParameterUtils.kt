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

@file:JvmName("StartParameterUtils")

package com.android.build.gradle.internal

import org.gradle.StartParameter
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.logging.Logging

internal val StartParameter.isProjectIsolation: Boolean?
    get() {
        return try {
            (this as StartParameterInternal).isolatedProjects.get()
        } catch (e: Throwable) {
            Logging.getLogger("StartParameterUtils")
                .debug("Unable to decide if project isolation is enabled")
            return null
        }
    }
