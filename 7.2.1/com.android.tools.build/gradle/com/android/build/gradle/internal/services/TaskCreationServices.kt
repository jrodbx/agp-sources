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

package com.android.build.gradle.internal.services

import com.android.build.gradle.internal.lint.LintFromMaven
import org.gradle.api.Named
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import java.io.File

/**
 * Services for creating Tasks.
 *
 * This contains whatever is needed during task creation
 *
 * This is meant to be used only by TaskManagers and TaskCreation actions. Other stages of the plugin
 * will use different services objects.
 *
 * This is accessed via [com.android.build.gradle.internal.component.ComponentCreationConfig]
 */
interface TaskCreationServices: BaseServices {
    fun file(file: Any): File
    fun fileCollection(): ConfigurableFileCollection
    fun fileCollection(vararg files: Any): ConfigurableFileCollection
    fun initializeAapt2Input(aapt2Input: Aapt2Input)
    fun <T> provider(callable: () -> T): Provider<T>

    fun <T : Named> named(type: Class<T>, name: String): T

    val lintFromMaven: LintFromMaven
}
