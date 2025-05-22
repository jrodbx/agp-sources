/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.api.dsl

import org.gradle.api.Incubating

/**
 * DSL object for configuring Version Control information
 */
interface VcsInfo {

    /**
     * Determines whether to include VCS info in the build.
     *
     * When the value is not set/null, the feature will be enabled by default in release builds.
     * However, in the case that it is not successful, the build will not fail but will log the
     * error message.
     */
    @get:Incubating
    @set:Incubating
    var include: Boolean?
}
