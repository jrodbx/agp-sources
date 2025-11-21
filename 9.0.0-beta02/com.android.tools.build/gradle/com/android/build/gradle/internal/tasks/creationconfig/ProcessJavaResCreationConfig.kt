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

package com.android.build.gradle.internal.tasks.creationconfig

import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.gradle.internal.component.TaskCreationConfig
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider

/**
 * Configuration needed to create the [com.android.build.gradle.internal.tasks.ProcessJavaResTask],
 * implementations should mostly delegate to [com.android.build.gradle.internal.component.ComponentCreationConfig].
 */
interface ProcessJavaResCreationConfig: TaskCreationConfig {

    /**
     * Extra [org.gradle.api.file.FileCollection] that may contains bytecodes and resources that should be added.
     */
    val extraClasses: Collection<FileCollection>

    /**
     * Is build-in kotlin support enabled.
     */
    val useBuiltInKotlinSupport: Boolean

    /**
     * In case an APK is built, should jacoco runtime be packaged.
     */
    val packageJacocoRuntime: Boolean

    /**
     * Configuration for all annotation processors.
     */
    val annotationProcessorConfiguration: Configuration?

    /**
     * The Java/Kotlin `resources` directories if there are any.
     */
    val sources: FlatSourceDirectoriesImpl?

    /**g
     * Saves the [com.android.build.gradle.internal.tasks.ProcessJavaResTask] to the
     * [com.android.build.gradle.internal.scope.TaskContainer] for access through the old
     * variant API.
     */
    fun setJavaResTask(task: TaskProvider<out Sync>)
}
