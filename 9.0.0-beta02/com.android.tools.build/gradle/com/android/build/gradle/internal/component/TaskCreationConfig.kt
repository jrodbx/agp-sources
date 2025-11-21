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

package com.android.build.gradle.internal.component;

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.utils.appendCapitalized

/**
 * Basic configuration for creating [org.gradle.api.Task]s
 */
interface TaskCreationConfig {

    /**
     * Name of the user visible concept like the variant or a test suite source that will
     * be used to create tasks' names from.
     */
    val name: String

    val services: TaskCreationServices

    /**
     * Deprecated, only to support old variant API.
     */
    val taskContainer: MutableTaskContainer

    /**
     * [com.android.build.api.artifact.Artifacts] instance that can be used to lookup or register
     * new artifacts. This instance is bound to the component or test suites source it was created
     * for.
     */
    val artifacts: ArtifactsImpl

    fun computeTaskNameInternal(prefix: String, suffix: String): String =
        prefix.appendCapitalized(name, suffix)

    fun computeTaskNameInternal(prefix: String): String =
        prefix.appendCapitalized(name)
}
