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
 * The plugin applied with 'com.android.reporting'
 */
class ReportingPlugin: Plugin<Project> {
    override fun apply(project: Project) {
        project.apply(VERSION_CHECK_PLUGIN_ID)
        project.apply(INTERNAL_PLUGIN_ID)
    }
}

private val INTERNAL_PLUGIN_ID = mapOf("plugin" to "com.android.internal.reporting")
