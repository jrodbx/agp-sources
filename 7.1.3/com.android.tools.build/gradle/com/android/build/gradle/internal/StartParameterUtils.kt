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

internal val StartParameter.isConfigurationCache : Boolean? get() {
    return try {
        // TODO(b/167384234) move away from using Gradle internal class when public API is available
        // When project isolation is enabled, configuration cache is consider enabled. See b/190811940
        (this as StartParameterInternal).configurationCache.get() || this.isolatedProjects.get()
    } catch (e : Throwable) {
        Logging.getLogger("StartParameterUtils")
            .warn("Unable to decide if config caching is enabled", e)
        return null
    }
}
