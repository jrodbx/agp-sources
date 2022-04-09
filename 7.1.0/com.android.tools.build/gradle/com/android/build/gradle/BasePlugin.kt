/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Base class for the plugin.
 *
 * @Deprecated Use the plugin classes directly
 */
open class BasePlugin: Plugin<Project> {
    private lateinit var project: Project

    override fun apply(project: Project) {
        this.project = project

        project.apply(VERSION_CHECK_PLUGIN_ID)
    }

    /**
     * Returns the Android extension.
     *
     * @deprecated Directely call project.extensions.getByName("android") instead.
     */
    @Deprecated("Use project.extensions.getByName(\"android\")")
    fun getExtension(): BaseExtension {
        return project.extensions.getByName("android") as BaseExtension
    }
}

internal val VERSION_CHECK_PLUGIN_ID = mapOf("plugin" to "com.android.internal.version-check")
