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

package com.android.build.api.artifact.impl

/**
 * Defines common behaviors of operation requests on an [com.android.build.api.artifact.Artifact]
 */
interface ArtifactOperationRequest {

    /**
     * Closes the operation request, all input parameters have been provided and the operation has
     * enough information to proceed to the next step.
     */
    fun closeRequest() {
        artifacts.closeRequest(this)
    }

    val description: String

    val artifacts: ArtifactsImpl
}
