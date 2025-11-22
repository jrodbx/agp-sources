/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal.utils

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer

/**
 * Looks for an item with the given name, registering it and adding it to this container if it does
 * not exist
 */
fun ConfigurationContainer.maybeRegister(name: String, configuration: Configuration.() -> Unit = {}): NamedDomainObjectProvider<Configuration> {
    return if (this.names.contains(name)) {
        this.named(name)
    } else {
        this.register(name, configuration)
    }
}
