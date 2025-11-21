/*
 * Copyright (C) 2022 The Android Open Source Project
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

@Incubating
interface PrivacySandboxSdkBundle {

    @get:Deprecated(message = "packageName is replaced with applicationId", replaceWith = ReplaceWith("applicationId"))
    @get:Incubating
    @set:Deprecated(message = "packageName is replaced with applicationId", replaceWith = ReplaceWith("applicationId"))
    @set:Incubating
    var packageName: String?

    @get:Incubating
    @set:Incubating
    var applicationId: String?

    @get:Incubating
    @set:Incubating
    var sdkProviderClassName: String?

    @get:Incubating
    @set:Incubating
    var compatSdkProviderClassName: String?

    @Incubating
    fun setVersion(major: Int, minor: Int, patch: Int)
}
