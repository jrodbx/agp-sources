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

package com.android.build.api.variant

import org.gradle.api.Incubating
import org.gradle.api.Task

/**
 * Lifecycle tasks created by the AGP plugins.
 *
 * A lifecycle task usually does not have any processing associated with it but represent a
 * specific location in the build process that can be used to register dependent tasks. These are
 * also [Task]s that can be invoked by users which provide a consumable output.
 */
@Incubating
interface LifecycleTasks {

    /**
     * Registers a task dependency on the PreBuild lifecycle task.
     *
     * @param objects must comply to Gradle's task dependency rules defined
     * [there](https://docs.gradle.org/current/javadoc/org/gradle/api/Task.html#dependencies)
     */
    fun registerPreBuild(vararg objects: Any)
}
