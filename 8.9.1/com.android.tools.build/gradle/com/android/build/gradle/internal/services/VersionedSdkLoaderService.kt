/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.repository.Revision
import org.gradle.api.Project
import org.gradle.api.provider.Provider

/**
 * Object responsible for creating (and memoizing) the instance of [SdkComponentsBuildService.VersionedSdkLoader]
 *
 * This is used by several other services
 */
class VersionedSdkLoaderService(
    private val services: BaseServices,
    private val project: Project,
    private val compileSdkVersionAction: () -> String?,
    private val buildToolsRevision: () -> Revision
) {
    val versionedSdkLoader: Provider<SdkComponentsBuildService.VersionedSdkLoader> by lazy {
        val buildService =
            getBuildService(services.buildServiceRegistry, SdkComponentsBuildService::class.java)
        buildService
            .map { sdkComponentsBuildService ->
                sdkComponentsBuildService.sdkLoader(
                    project.provider(compileSdkVersionAction),
                    project.provider(buildToolsRevision)
                )
            }
    }
}
