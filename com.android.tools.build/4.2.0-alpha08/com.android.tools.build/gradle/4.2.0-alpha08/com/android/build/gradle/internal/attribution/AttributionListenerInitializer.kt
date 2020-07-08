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

package com.android.build.gradle.internal.attribution

import org.gradle.api.Project
import org.gradle.api.invocation.Gradle

object AttributionListenerInitializer {
    private var attributionBuildListener: AttributionBuildListener? = null

    @Synchronized
    fun init(project: Project, attributionFileLocation: String?) {
        if (attributionBuildListener == null && attributionFileLocation != null) {
            attributionBuildListener = AttributionBuildListener(attributionFileLocation)
            project.gradle.addListener(attributionBuildListener!!)
        }
    }

    @Synchronized
    fun unregister(gradle: Gradle?) {
        if (attributionBuildListener != null) {
            gradle?.removeListener(attributionBuildListener!!)
            attributionBuildListener = null
        }
    }
}
